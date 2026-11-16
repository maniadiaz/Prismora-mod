package com.prismora.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.prismora.Prismora;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.SectionPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeSet;

final class ComplementaryShadowSmokeProbe {
    private static final int SIZE = 128;
    private static final long START = System.nanoTime();
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    record Inputs(TerrainShaderTranslator.Program shader, byte[] noise, byte[] cloudWater) {}

    private ComplementaryShadowSmokeProbe() {}

    static void submit(Inputs inputs, GpuBuffer vertices, int faceCount, TerrainShadowFrame light,
                       TerrainCameraFrame camera, SectionPos section, TerrainShaderTranslator.Program terrain, List<ComplementaryPostCompileProbe.Pass> post, Runnable completed) {
        var device = RenderSystem.getDevice();
        var client = Minecraft.getInstance();
        var shader = inputs.shader();
        var atlas = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        var scene = TerrainSceneUniforms.capture(client, atlas.getWidth(0), atlas.getHeight(0));
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var layout = new TerrainUniformLayout(shader.uniformTypes());
        var uniformData = layout.allocate();
        scene.write(layout, uniformData);
        var eye = camera.position();
        var origin = section.origin();
        var viewFromSection = light.viewFromSection();
        var viewFromCamera = new Matrix4f(viewFromSection).translate((float) (eye.x - origin.getX()),
                (float) (eye.y - origin.getY()), (float) (eye.z - origin.getZ()));
        var projection = light.legacyProjection();
        var cameraProjection = camera.legacyProjection(device.getDeviceInfo().isZZeroToOne());
        var cameraView = camera.viewRotation();
        layout.put(uniformData, "prismoraProjection", projection);
        layout.put(uniformData, "prismoraModelView", viewFromSection);
        layout.put(uniformData, "prismoraNormalMatrix", new Matrix3f(viewFromSection).invert().transpose());
        layout.put(uniformData, "shadowProjection", projection);
        layout.put(uniformData, "shadowProjectionInverse", new Matrix4f(projection).invert());
        layout.put(uniformData, "shadowModelView", viewFromCamera);
        layout.put(uniformData, "shadowModelViewInverse", new Matrix4f(viewFromCamera).invert());
        layout.put(uniformData, "gbufferProjection", cameraProjection);
        layout.put(uniformData, "gbufferProjectionInverse", new Matrix4f(cameraProjection).invert());
        layout.put(uniformData, "gbufferModelView", cameraView);
        layout.put(uniformData, "gbufferModelViewInverse", new Matrix4f(cameraView).invert());
        layout.putMatrixPair(uniformData, "prismoraTextureMatrix", new Matrix4f(),
                new Matrix4f().scaling(1f / 256f, 1f / 256f, 1f / 256f).m30(1f / 32f).m31(1f / 32f));
        layout.putVec3(uniformData, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        layout.putFloat(uniformData, "frameTimeCounter", (System.nanoTime() - START) / 1e9f % 3600f);
        float sun = (float) (client.gameRenderer.gameRenderState().levelRenderState.skyRenderState.sunAngle / (Math.PI * 2));
        layout.putFloat(uniformData, "sunAngle", sun - (float) Math.floor(sun));
        layout.putInt(uniformData, "entityId", 0);

        var terrainLayout = new TerrainUniformLayout(terrain.uniformTypes());
        var terrainData = terrainLayout.allocate();
        scene.write(terrainLayout, terrainData);
        terrainLayout.putFloat(terrainData, "viewWidth", 128);
        terrainLayout.putFloat(terrainData, "viewHeight", 128);
        terrainLayout.putFloat(terrainData, "aspectRatio", 1);
        var terrainView = camera.modelViewFromSection(section);
        terrainLayout.put(terrainData, "prismoraProjection", cameraProjection);
        terrainLayout.put(terrainData, "prismoraModelView", terrainView);
        terrainLayout.put(terrainData, "prismoraNormalMatrix", new Matrix3f(terrainView).invert().transpose());
        terrainLayout.put(terrainData, "gbufferProjection", cameraProjection);
        terrainLayout.put(terrainData, "gbufferProjectionInverse", new Matrix4f(cameraProjection).invert());
        terrainLayout.put(terrainData, "gbufferModelView", cameraView);
        terrainLayout.put(terrainData, "gbufferModelViewInverse", new Matrix4f(cameraView).invert());
        terrainLayout.put(terrainData, "shadowProjection", projection);
        terrainLayout.put(terrainData, "shadowProjectionInverse", new Matrix4f(projection).invert());
        terrainLayout.put(terrainData, "shadowModelView", viewFromCamera);
        terrainLayout.put(terrainData, "shadowModelViewInverse", new Matrix4f(viewFromCamera).invert());
        terrainLayout.putMatrixPair(terrainData, "prismoraTextureMatrix", new Matrix4f(),
                new Matrix4f().scaling(1f / 256f, 1f / 256f, 1f / 256f).m30(1f / 32f).m31(1f / 32f));
        terrainLayout.putVec3(terrainData, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        terrainLayout.putFloat(terrainData, "frameTimeCounter", (System.nanoTime() - START) / 1e9f % 3600f);
        terrainLayout.putFloat(terrainData, "sunAngle", sun - (float) Math.floor(sun));
        terrainLayout.putInt(terrainData, "entityId", 0);

        var remaining = new java.util.concurrent.atomic.AtomicInteger(2);
        Runnable verified = () -> { if (remaining.decrementAndGet() == 0) completed.run(); };
        var color = new GpuTargets.Spec(TargetFormat.RGBA8, 1, 1, false, 0, 0, 0, 0);
        var readback = device.createBuffer(() -> "Complementary shadow witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                SIZE * SIZE * 4L);
        try (var noise = load("noise", inputs.noise());
             var cloud = load("cloud-water", inputs.cloudWater());
             var repeat = device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
             var uniforms = device.createBuffer(() -> "Complementary shadow uniforms", GpuBuffer.USAGE_UNIFORM, uniformData);
             var targets = new GpuTargets(device, Map.of(0, color, 1, color), SIZE, SIZE, 1024 * 1024);
             var depth = new GpuDepthTargets(device, SIZE, SIZE, 1024 * 1024);
             var program = GpuProgram.withVertices(device, "complementary_shadow_draw", shader.vertex(), shader.fragment(),
                     List.of(GpuFormat.RGBA8_UNORM, GpuFormat.RGBA8_UNORM), TerrainVertexData.FORMAT, PrimitiveTopology.QUADS,
                     shader.samplers(), DepthStencilState.DEFAULT, List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
             var check = witnessProgram();
             var output = device.createTexture("Complementary shadow validation", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
             var outputView = device.createTextureView(output)) {
            var known = Map.of("tex", atlas, "noisetex", noise.getTextureView(), "gaux4", cloud.getTextureView(),
                    "lightmap", client.gameRenderer.lightmap());
            var required = new TreeSet<>(((FrontendRenderPipeline) program.compiled()).uniformIndices().keySet());
            required.remove(TerrainShaderTranslator.UNIFORM_BLOCK);
            var missing = new TreeSet<>(required);
            missing.removeAll(known.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Active Complementary shadow samplers without scene resources: " + missing + "; active=" + required);
            }
            var depthView = depth.view(GpuDepthTargets.Stage.SCENE);
            var descriptor = RenderPassDescriptor.builder(() -> "Complementary shadow draw")
                    .withColorAttachment(targets.write(0)).withColorAttachment(targets.write(1)).withDepthAttachment(depthView).build();
            int count = faceCount * 6;
            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            var indexBuffer = indices.getBuffer(count);
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(descriptor)) {
                pass.setPipeline(program.compiled());
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                for (String name : required) {
                    pass.setUniform(name, known.get(name), name.equals("noisetex") || name.equals("gaux4") ? repeat : nearest);
                }
                pass.drawIndexed(count, 1, 0, 0, 0);
            }
            depth.snapshot(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY);
            ComplementaryTerrainDrawProbe.submit(terrain, terrainData, vertices, count, atlas, noise.getTextureView(), repeat,
                    depthView, depth.view(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY), targets.write(0), post, camera, scene, verified);
            MrtPass.draw(device, check, List.of(outputView), Map.of("ShadowDepth", depthView,
                    "ShadowColor0", targets.write(0), "ShadowColor1", targets.write(1)), nearest);
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                int depthCount = 0, color0Count = 0, color1Count = 0;
                try (readback; var mapping = readback.map(true, false)) {
                    var bytes = mapping.data();
                    for (int i = 0; i < SIZE * SIZE * 4; i += 4) {
                        if (bytes.get(i) != 0) depthCount++;
                        if (bytes.get(i + 1) != 0) color0Count++;
                        if (bytes.get(i + 2) != 0) color1Count++;
                    }
                }
                if (depthCount < 16 || color0Count < 16 || color1Count < 16) {
                    throw new IllegalStateException("Real Complementary shadow outputs have insufficient coverage: "
                            + depthCount + "/" + color0Count + "/" + color1Count);
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SHADOW_DRAW_PASS: {} faces, depth={}, shadowcolor0={}, shadowcolor1={}, samplers={}",
                        faceCount, depthCount, color0Count, color1Count, required);
                verified.run();
            }, 0);
            encoder.submit();
        } catch (IOException | RuntimeException failure) {
            readback.close();
            throw new IllegalStateException("Complementary shadow render failed", failure);
        }
    }

    private static DynamicTexture load(String label, byte[] contents) throws IOException {
        return new DynamicTexture(() -> "Complementary " + label, NativeImage.read(contents));
    }

    private static GpuProgram witnessProgram() {
        return new GpuProgram(RenderSystem.getDevice(), "complementary_shadow_readback", HEADER + """
                void main(){vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}
                """, HEADER + """
                uniform sampler2D ShadowDepth;
                uniform sampler2D ShadowColor0;
                uniform sampler2D ShadowColor1;
                layout(location=0) out vec4 result;
                void main(){
                    ivec2 p=ivec2(gl_FragCoord.xy);
                    float z=texelFetch(ShadowDepth,p,0).r;
                    vec4 a=texelFetch(ShadowColor0,p,0);
                    vec4 b=texelFetch(ShadowColor1,p,0);
                    result=vec4(float(z>0.0&&z<=1.0),float(z>0.0&&any(greaterThan(a,vec4(0.0)))),
                        float(z>0.0&&any(greaterThan(b,vec4(0.0)))),1.0);
                }
                """, List.of(GpuFormat.RGBA8_UNORM), List.of("ShadowDepth", "ShadowColor0", "ShadowColor1"));
    }
}
