package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuDepthTargets;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.GpuTargets;
import com.prismora.render.runtime.MrtPass;
import com.prismora.render.runtime.ShadowDepthSampling;
import com.prismora.render.runtime.TargetFormat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ShadowSamplingSmokeProbe {
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final String VERTEX = HEADER + """
            void main() {
                vec2 uv = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private ShadowSamplingSmokeProbe() {}

    static void submit(Runnable completed) {
        var device = RenderSystem.getDevice();
        var spec = new GpuTargets.Spec(TargetFormat.RGBA8, 1, 1, false, 0, 0, 0, 0);
        var readback = device.createBuffer(() -> "Prismora shadow witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 4);
        try (var depth = new GpuDepthTargets(device, 2, 2, 1024);
             var color = new GpuTargets(device, Map.of(0, spec), 2, 2, 1024);
             var writer = GpuProgram.withDepth(device, "shadow_depth_fixture", VERTEX, HEADER + """
                     layout(location=0) out vec4 dummy;
                     void main() {
                         gl_FragDepth = gl_FragCoord.x < 1.0 ? 0.25 : 0.75;
                         dummy = vec4(1.0);
                     }
                     """, List.of(TargetFormat.RGBA8), List.of(), DepthStencilState.DEFAULT);
             var checker = new GpuProgram(device, "shadow_compare_fixture", VERTEX, HEADER + ShadowDepthSampling.GLSL + """
                     uniform sampler2D shadowtex0;
                     layout(location=0) out vec4 color;
                     void main() {
                         vec3 results = vec3(prismoraShadow2D(shadowtex0,vec3(0.25,0.5,0.5)).r,
                                             prismoraShadow2D(shadowtex0,vec3(0.75,0.5,0.5)).r,
                                             prismoraShadow2D(shadowtex0,vec3(0.50,0.5,0.5)).r);
                         bool passed = all(lessThan(abs(results-vec3(1.0,0.0,0.5)),vec3(0.001)));
                         color = passed ? vec4(0.0,1.0,0.0,1.0) : vec4(1.0,0.0,0.0,1.0);
                     }
                     """, List.of(GpuFormat.RGBA8_UNORM), List.of("shadowtex0"));
             var witness = device.createTexture("Prismora shadow comparison result",
                     GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
             var witnessView = device.createTextureView(witness)) {
            var view = depth.view(GpuDepthTargets.Stage.SCENE);
            MrtPass.draw(device, writer, List.of(color.write(0)), Map.of(), null, Optional.of(view));
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            MrtPass.draw(device, checker, List.of(witnessView), Map.of("shadowtex0", view), nearest);
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(witness, readback, 0, () -> {
                try (readback; var pixels = readback.map(true, false)) {
                    if ((pixels.data().get(1) & 255) != 255) {
                        throw new IllegalStateException("Reverse-Z shadow comparison or bilinear filtering failed");
                    }
                }
                Prismora.LOGGER.info("PRISMORA_WORLD_SHADOW_COMPARE_PASS: lit, occluded and filtered samples verified");
                completed.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }
}
