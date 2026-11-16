package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.prismora.Prismora;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.PackContract;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.SectionPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the shaderpack's water and translucent block geometry using opaque depth as input. */
final class ComplementaryTranslucentPass {
    private static final String NAME = "gbuffers_water";

    record Program(TerrainShaderTranslator.Program shader, List<Integer> slots) {}
    record Geometry(GpuBuffer vertices, int count) {}

    private ComplementaryTranslucentPass() {}

    static Program load(LegacyPack pack, Map<String, String> options, Map<String, String> environment, String dimension)
            throws IOException {
        var source = new PackContract(pack, options, environment, dimension).resolve(NAME)
                .orElseThrow(() -> new IOException("Missing Complementary " + NAME));
        var vertex = new ShaderPreprocessor(pack, options, environment).process(source.vertex());
        var fragment = new ShaderPreprocessor(pack, options, environment).process(source.fragment());
        var shader = new TerrainShaderTranslator().translate(vertex, fragment,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        var slots = shader.outputSlots().stream().mapToInt(OptionalInt::orElseThrow).boxed().toList();
        var result = new Program(shader, slots);
        try (var compiled = compile(result)) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TRANSLUCENT_COMPILE_PASS: slots {} and samplers {}", slots, shader.samplers());
        }
        return result;
    }

    private static GpuProgram compile(Program program) {
        var targets = program.slots().stream().map(slot -> new ColorTargetState(
                slot == 4 || slot == 8 ? Optional.<BlendFunction>empty() : Optional.of(BlendFunction.TRANSLUCENT),
                ComplementaryPostCompileProbe.format(slot), ColorTargetState.WRITE_ALL)).toList();
        var shader = program.shader();
        String fragment = shader.fragment().replace("gl_FragCoord.z", "(1.0 - gl_FragCoord.z)") + """

                void main(){
                    prismoraWaterMain();
                    if (prismoraFragData[0].a <= 0.0001) discard;
                }
                """;
        fragment = fragment.replaceFirst("\\bvoid main\\s*\\(", "void prismoraWaterMain(");
        return GpuProgram.withVertexTargets(RenderSystem.getDevice(), NAME, shader.vertex(), fragment, targets,
                TerrainVertexData.FORMAT, shader.samplers(), DepthStencilState.DEFAULT, List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
    }

    static void draw(Program program, Geometry geometry, java.util.function.IntFunction<GpuTextureView> target, GpuTextureView liveDepth,
                     Map<String, GpuTextureView> available, GpuSampler repeat, TerrainCameraFrame camera,
                     TerrainSceneUniforms scene, TerrainShadowFrame light, SectionPos section) {
        var device = RenderSystem.getDevice();
        var pipeline = ComplementaryPreviewPipelines.get(NAME, () -> compile(program));
        if (geometry == null) {
            return;
        }
        var shader = program.shader();
        var layout = new TerrainUniformLayout(shader.uniformTypes());
        var data = layout.allocate();
        scene.write(layout, data);
        var state = net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState;
        var fog = state.cameraRenderState.fogData;
        layout.putVec4(data, "prismoraFogParams", fog.renderDistanceStart,
                1f / Math.max(0.00001f, fog.renderDistanceEnd - fog.renderDistanceStart), fog.renderDistanceEnd, 0f);
        float angle = state.skyRenderState.sunAngle / (float) (2.0 * Math.PI);
        layout.putFloat(data, "sunAngle", angle - (float) Math.floor(angle));
        layout.putFloat(data, "viewWidth", liveDepth.getWidth(0));
        layout.putFloat(data, "viewHeight", liveDepth.getHeight(0));
        layout.putFloat(data, "aspectRatio", (float) liveDepth.getWidth(0) / liveDepth.getHeight(0));
        var projection = camera.legacyProjection(device.getDeviceInfo().isZZeroToOne());
        var view = camera.viewRotation();
        var modelView = camera.modelViewFromSection(section);
        layout.put(data, "prismoraProjection", projection);
        layout.put(data, "prismoraModelView", modelView);
        layout.put(data, "prismoraNormalMatrix", new Matrix3f(modelView).invert().transpose());
        layout.put(data, "gbufferProjection", projection);
        layout.put(data, "gbufferProjectionInverse", new Matrix4f(projection).invert());
        layout.put(data, "gbufferModelView", view);
        layout.put(data, "gbufferModelViewInverse", new Matrix4f(view).invert());
        layout.putMatrixPair(data, "prismoraTextureMatrix", new Matrix4f(),
                new Matrix4f().scaling(1f / 256f, 1f / 256f, 1f).m30(1f / 32f).m31(1f / 32f));
        var eye = camera.position();
        var origin = section.origin();
        var lightView = light.viewFromSection().translate((float)(eye.x - origin.getX()),
                (float)(eye.y - origin.getY()), (float)(eye.z - origin.getZ()));
        layout.put(data, "shadowProjection", light.legacyProjection());
        layout.put(data, "shadowProjectionInverse", light.legacyProjection().invert());
        layout.put(data, "shadowModelView", lightView);
        layout.put(data, "shadowModelViewInverse", new Matrix4f(lightView).invert());
        layout.putVec3(data, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        var active = ((FrontendRenderPipeline) pipeline.compiled()).uniformIndices().keySet();
        var description = RenderPassDescriptor.builder(() -> "Complementary translucent terrain").withDepthAttachment(liveDepth);
        for (int slot : program.slots()) {
            description.withColorAttachment(target.apply(slot));
        }
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(geometry.count());
        try (var uniforms = device.createBuffer(() -> "Complementary translucency", GpuBuffer.USAGE_UNIFORM, data);
             var pass = device.createCommandEncoder().createRenderPass(description.build())) {
            pass.setPipeline(pipeline.compiled());
            pass.setVertexBuffer(0, geometry.vertices().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
            for (String sampler : active) {
                if (sampler.equals(TerrainShaderTranslator.UNIFORM_BLOCK)) {
                    continue;
                }
                var texture = available.get(sampler);
                if (texture == null) {
                    throw new IllegalStateException("Missing Complementary translucent texture " + sampler + " from " + active);
                }
                pass.setUniform(sampler, texture, sampler.equals("noisetex") || sampler.equals("gaux4") ? repeat : nearest);
            }
            pass.drawIndexed(geometry.count(), 1, 0, 0, 0);
        }
        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TRANSLUCENT_DRAW_PASS: {} sorted faces", geometry.count() / 6);
    }

    static void verifyDepth(GpuTextureView scene, GpuTextureView opaque, Runnable completed) {
        var device = RenderSystem.getDevice();
        int width = scene.getWidth(0), height = scene.getHeight(0);
        int bytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        var readback = device.createBuffer(() -> "Complementary translucent depth validation",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, bytes);
        String header = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
        try (var witness = new GpuProgram(device, "complementary_translucent_depth", header + """
                    void main(){
                        vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                        gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
                    }
                    """, header + """
                    uniform sampler2D Scene;
                    uniform sampler2D Opaque;
                    layout(location=0) out vec4 result;
                    void main(){
                        ivec2 p=ivec2(gl_FragCoord.xy);
                        float difference=texelFetch(Scene,p,0).r-texelFetch(Opaque,p,0).r;
                        result=vec4(float(difference>0.000001),float(difference< -0.000001),0.0,1.0);
                    }
                    """, List.of(GpuFormat.RGBA8_UNORM), List.of("Scene", "Opaque"));
             var output = device.createTexture("Complementary translucent depth witness",
                     GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
             var view = device.createTextureView(output)) {
            MrtPass.draw(device, witness, List.of(view), Map.of("Scene", scene, "Opaque", opaque),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                int closer = 0;
                try (readback; var mapping = readback.map(true, false)) {
                    for (int index = 0; index < bytes; index += 4) {
                        if (mapping.data().get(index + 1) != 0) {
                            throw new IllegalStateException("Translucency replaced nearer opaque depth");
                        }
                        if (mapping.data().get(index) != 0) {
                            closer++;
                        }
                    }
                }
                if (closer == 0) {
                    throw new IllegalStateException("No translucent faces drew in front of opaque terrain");
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TRANSLUCENT_DEPTH_PASS: {} foreground pixels kept separate from opaque depth", closer);
                completed.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }
}
