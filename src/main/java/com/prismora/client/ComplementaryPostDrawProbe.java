package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.*;
import com.prismora.Prismora;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

final class ComplementaryPostDrawProbe {
    private static final List<String> TAIL = List.of("composite3", "composite4", "composite5", "final");
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final String QUAD = HEADER + """
            void main(){
                vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
            }
            """;

    private ComplementaryPostDrawProbe() {}

    static void submit(List<ComplementaryPostCompileProbe.Pass> programs, GpuTextureView terrain, GpuTextureView depth,
                       GpuTextureView noise, GpuSampler repeat, TerrainCameraFrame camera, TerrainSceneUniforms scene,
                       Runnable completed) {
        var passes = programs.stream().filter(pass -> TAIL.contains(pass.name())).toList();
        if (!passes.stream().map(ComplementaryPostCompileProbe.Pass::name).toList().equals(TAIL)) {
            throw new IllegalStateException("Incomplete Complementary postprocessing tail");
        }
        var device = RenderSystem.getDevice();
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        int width = terrain.getWidth(0), height = terrain.getHeight(0);
        var specs = Map.of(0, spec(GpuFormat.RG11B10_FLOAT), 3, spec(GpuFormat.RGBA8_UNORM), 14, spec(GpuFormat.R32_FLOAT));
        var readback = device.createBuffer(() -> "Complementary final pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                (long) width * height * 4);
        try (var buffers = new GpuTargets(device, specs, width, height, 1024 * 1024);
             var output = device.createTexture("Complementary final output",
                     GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
             var outputView = device.createTextureView(output);
             var convertDepth = new GpuProgram(device, "complementary_legacy_depth", QUAD, HEADER + """
                     uniform sampler2D NativeDepth;
                     layout(location=0) out vec4 Converted;
                     void main(){Converted=vec4(1.0-texelFetch(NativeDepth,ivec2(gl_FragCoord.xy),0).r);}
                     """, List.of(GpuFormat.R32_FLOAT), List.of("NativeDepth"))) {
            MrtPass.draw(device, convertDepth, List.of(buffers.write(14)), Map.of("NativeDepth", depth), nearest);
            var legacyDepth = buffers.write(14);
            GpuTextureView currentColor = terrain;
            for (var post : passes) {
                var shader = post.shader();
                int slot = shader.outputSlots().getFirst().orElseThrow();
                if (shader.outputSlots().size() != 1 || (slot != 0 && slot != 3)) {
                    throw new IllegalStateException("Unsupported targets in postprocessing tail: " + post.name());
                }
                boolean finalPass = post.name().equals("final");
                var destination = finalPass ? outputView : buffers.write(slot);
                var available = Map.of("colortex0", currentColor, "colortex3", buffers.read(3),
                        "depthtex0", legacyDepth, "noisetex", noise);
                if (!available.keySet().containsAll(shader.samplers())) {
                    throw new IllegalStateException("Missing texture for Complementary " + post.name() + ": " + shader.samplers());
                }
                try (var program = ComplementaryPostCompileProbe.compile(post);
                     var uniforms = device.createBuffer(() -> "Complementary post scene", GpuBuffer.USAGE_UNIFORM,
                             parameters(shader, camera, scene, width, height))) {
                    var target = RenderPassDescriptor.builder(() -> "Complementary " + post.name()).withColorAttachment(destination).build();
                    try (var pass = device.createCommandEncoder().createRenderPass(target)) {
                        pass.setPipeline(program.compiled());
                        pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                        for (String name : shader.samplers()) {
                            var source = available.get(name);
                            if (source.texture() == destination.texture()) {
                                throw new IllegalStateException("Complementary post attachment read/write alias: " + name);
                            }
                            pass.setUniform(name, source, name.equals("noisetex") ? repeat : name.equals("depthtex0") ? nearest : linear);
                        }
                        pass.draw(3, 1, 0, 0);
                    }
                }
                if (!finalPass) {
                    buffers.flip(List.of(slot));
                    if (slot == 0) {
                        currentColor = buffers.read(slot);
                    }
                }
            }

            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                int visible = 0;
                try (readback; var mapping = readback.map(true, false)) {
                    var pixels = mapping.data();
                    for (int i = 0; i < width * height * 4; i += 4) {
                        if ((pixels.get(i + 3) & 255) != 255) {
                            throw new IllegalStateException("Complementary final did not cover the output");
                        }
                        if ((pixels.get(i) & 255) + (pixels.get(i + 1) & 255) + (pixels.get(i + 2) & 255) > 24) {
                            visible++;
                        }
                    }
                }
                if (visible < 16) {
                    throw new IllegalStateException("Complementary final returned no visible terrain");
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_POST_DRAW_PASS: {} produced {} non-dark pixels", TAIL, visible);
                completed.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static java.nio.ByteBuffer parameters(TerrainShaderTranslator.Program shader, TerrainCameraFrame camera,
                                                    TerrainSceneUniforms scene, int width, int height) {
        var layout = new TerrainUniformLayout(shader.uniformTypes());
        var data = layout.allocate();
        scene.write(layout, data);
        layout.putFloat(data, "viewWidth", width);
        layout.putFloat(data, "viewHeight", height);
        layout.putFloat(data, "aspectRatio", (float) width / height);
        var client = Minecraft.getInstance();
        var cameraState = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        var fog = cameraState.fogData;
        layout.putVec4(data, "prismoraFogParams", fog.renderDistanceStart,
                1f / Math.max(0.00001f, fog.renderDistanceEnd - fog.renderDistanceStart), fog.renderDistanceEnd, 0f);
        layout.putMatrixPair(data, "prismoraTextureMatrix", new Matrix4f(), new Matrix4f());
        var projection = camera.legacyProjection(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        layout.put(data, "gbufferProjection", projection);
        layout.put(data, "gbufferProjectionInverse", new Matrix4f(projection).invert());
        var view = camera.viewRotation();
        layout.put(data, "gbufferModelView", view);
        layout.put(data, "gbufferModelViewInverse", new Matrix4f(view).invert());
        var eye = camera.position();
        layout.putVec3(data, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        return data;
    }

    private static GpuTargets.Spec spec(GpuFormat format) {
        return new GpuTargets.Spec(format, 1, 1, false, 0, 0, 0, 0);
    }
}
