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
    private static final List<String> ORDER = List.of("deferred1", "composite", "composite1", "composite3", "composite4",
            "composite5", "composite6", "composite7", "final");
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final String QUAD = HEADER + """
            void main(){
                vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
            }
            """;

    private static TerrainCameraFrame previousPose;
    private static GpuTextureView previousHistory;
    private static int submissions;

    private ComplementaryPostDrawProbe() {}

    static void close() {
        previousHistory = null;
        previousPose = null;
        submissions = 0;
    }

    static void invalidateHistory() {
        previousHistory = null;
    }

    static void submit(ComplementaryFrameResources resources, List<ComplementaryPostCompileProbe.Pass> programs, GpuTargets terrain, GpuTextureView depth,
                       GpuTextureView noise, GpuSampler repeat, TerrainCameraFrame camera, TerrainSceneUniforms scene,
                       TerrainShadowFrame light, net.minecraft.core.SectionPos section, Map<String, GpuTextureView> shadows,
                       Runnable completed) {
        var order = programs.stream().map(ComplementaryPostCompileProbe.Pass::name).toList();
        if (!order.equals(ORDER)) {
            throw new IllegalStateException("Unexpected base Overworld post programs: " + order);
        }
        var device = RenderSystem.getDevice();
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        int width = terrain.write(0).getWidth(0), height = terrain.write(0).getHeight(0);
        int frameBytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        int historyBytes = Math.multiplyExact(Math.multiplyExact(width, height), GpuFormat.RGBA16_FLOAT.blockSize());
        var specs = new java.util.LinkedHashMap<Integer, GpuTargets.Spec>();
        for (int index : List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 12, 13)) {
            boolean persistent = List.of(1, 2, 4, 5, 7).contains(index);
            var format = ComplementaryPostCompileProbe.format(index);
            specs.put(index, new GpuTargets.Spec(format, 1, 1, persistent, 0, 0, 0, 0));
        }
        var depthSpec = new GpuTargets.Spec(GpuFormat.R32_FLOAT, 1, 1, true, 0, 0, 0, 0);
        specs.put(14, depthSpec);
        var readback = device.createBuffer(() -> "Complementary final pixels", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                (long) frameBytes * 2 + (long) historyBytes * 2);
        var buffers = resources.get("post colors", GpuTargets.class,
                () -> new GpuTargets(device, specs, width, height, 32L * 1024 * 1024));
        var lastPose = previousPose == null ? camera : previousPose;
        var lastHistory = previousHistory;
        int startingFrame = submissions * 2;
        try {
            var output = resources.get("final color", GpuTexture.class, () -> device.createTexture("Complementary final output",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, width, height, 1, 1));
            var outputView = resources.get("final view", GpuTextureView.class, () -> device.createTextureView(output));
            var convertDepth = ComplementaryPreviewPipelines.get("legacy depth", () -> new GpuProgram(device, "complementary_legacy_depth", QUAD, HEADER + """
                    uniform sampler2D NativeDepth;
                    layout(location=0) out vec4 Converted;
                    void main(){Converted=vec4(1.0-texelFetch(NativeDepth,ivec2(gl_FragCoord.xy),0).r);}
                    """, List.of(GpuFormat.R32_FLOAT), List.of("NativeDepth")));
            MrtPass.draw(device, convertDepth, List.of(buffers.write(14)), Map.of("NativeDepth", depth), nearest);
            var legacyDepth = buffers.write(14);
            GpuTextureView firstHistory = null;
            boolean historyReused = false;
            for (int frame = 0; frame < 2; frame++) {
                buffers.clear(false);
                if (frame == 1) copyHistory(firstHistory, readback, 2L * frameBytes + historyBytes);
                var available = new java.util.HashMap<String, GpuTextureView>(shadows);
                specs.keySet().stream().filter(index -> index != 14)
                        .forEach(index -> available.put("colortex" + index, buffers.read(index)));
                available.put("colortex0", terrain.write(0));
                available.put("colortex4", terrain.write(4));
                available.put("colortex6", terrain.write(6));
                available.put("depthtex0", legacyDepth);
                available.put("depthtex1", legacyDepth);
                available.put("noisetex", noise);

                for (var post : programs) {
                    var shader = post.shader();
                    var slots = shader.outputSlots().stream().mapToInt(java.util.OptionalInt::orElseThrow).boxed().toList();
                    boolean finalPass = post.name().equals("final");
                    var destinations = finalPass ? List.of(outputView) : slots.stream().map(buffers::write).toList();
                    if (frame == 0 && lastHistory != null && post.name().equals("composite6")
                            && available.get("colortex2") != lastHistory) {
                        throw new IllegalStateException("Camera update lost the previous pose history");
                    }
                    if (frame == 1 && post.name().equals("composite6") && shader.samplers().contains("colortex2")) {
                        historyReused = firstHistory != null && available.get("colortex2") == firstHistory;
                    }
                    if (!available.keySet().containsAll(shader.samplers())) {
                        var missing = new java.util.TreeSet<>(shader.samplers());
                        missing.removeAll(available.keySet());
                        throw new IllegalStateException("Missing texture for Complementary " + post.name() + ": " + missing);
                    }

                    var program = ComplementaryPreviewPipelines.get(post.name(), () -> ComplementaryPostCompileProbe.compile(post));
                    try (
                         var uniforms = device.createBuffer(() -> "Complementary post scene", GpuBuffer.USAGE_UNIFORM,
                                 parameters(shader, camera, frame == 0 ? lastPose : camera, scene, light, section, width, height, startingFrame + frame))) {
                        var target = RenderPassDescriptor.builder(() -> "Complementary " + post.name());
                        destinations.forEach(target::withColorAttachment);
                        try (var pass = device.createCommandEncoder().createRenderPass(target.build())) {
                            pass.setPipeline(program.compiled());
                            pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                            for (String name : shader.samplers()) {
                                var source = available.get(name);
                                if (destinations.stream().anyMatch(destination -> source.texture() == destination.texture())) {
                                    throw new IllegalStateException("Complementary post attachment read/write alias: " + name);
                                }
                                var sampler = name.equals("noisetex") ? repeat
                                        : name.startsWith("depthtex") || name.startsWith("shadowtex") ? nearest : linear;
                                pass.setUniform(name, source, sampler);
                            }
                            pass.draw(3, 1, 0, 0);
                        }
                    }
                    if (!finalPass) {
                        buffers.flip(slots);
                        slots.forEach(index -> available.put("colortex" + index, buffers.read(index)));
                    }
                }
                if (frame == 0) {
                    firstHistory = available.get("colortex2");
                    copyHistory(firstHistory, readback, 2L * frameBytes);
                }
                if (frame == 1 && !historyReused) {
                    throw new IllegalStateException("Complementary composite6 did not receive the previous frame's colortex2");
                }

                if (frame == 1) {
                    previousPose = camera;
                    previousHistory = available.get("colortex2");
                    submissions++;
                    if (lastHistory != null) {
                        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_POSE_HISTORY_PASS: colortex2 survived camera update {}", submissions);
                    }
                    ComplementaryPresentProbe.stage(output);
                }
                var encoder = device.createCommandEncoder();
                encoder.copyTextureToBuffer(output, readback, (long) frame * frameBytes, frame == 0 ? () -> {} : () -> {
                    try (readback; var mapping = readback.map(true, false)) {
                        int first = visiblePixels(mapping.data(), 0, frameBytes);
                        int second = visiblePixels(mapping.data(), frameBytes, frameBytes);
                        verifyHistory(mapping.data(), 2 * frameBytes, historyBytes);
                        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_POST_DRAW_PASS: {} linked passes, both frames drawn ({}, {} non-dark pixels), colortex2 reused",
                                order.size(), first, second);
                    }
                    completed.run();
                }, 0);
                encoder.submit();
            }
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static void copyHistory(GpuTextureView history, GpuBuffer destination, long offset) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(history.texture(), destination, offset, () -> {}, 0);
        encoder.submit();
    }

    private static void verifyHistory(java.nio.ByteBuffer data, int start, int bytes) {
        boolean nonzero = false;
        for (int i = 0; i < bytes; i++) {
            byte original = data.get(start + i);
            nonzero |= original != 0;
            if (original != data.get(start + bytes + i)) {
                throw new IllegalStateException("colortex2 history changed before the second frame consumed it");
            }
        }
        if (!nonzero) throw new IllegalStateException("Complementary did not write colortex2 history");
    }

    private static int visiblePixels(java.nio.ByteBuffer pixels, int start, int bytes) {
        int visible = 0;
        for (int i = start; i < start + bytes; i += 4) {
            if ((pixels.get(i + 3) & 255) != 255) {
                throw new IllegalStateException("Complementary final did not cover the output");
            }
            if ((pixels.get(i) & 255) + (pixels.get(i + 1) & 255) + (pixels.get(i + 2) & 255) > 24) {
                visible++;
            }
        }
        if (visible < 16) throw new IllegalStateException("Complementary final returned no visible terrain");
        return visible;
    }
    private static java.nio.ByteBuffer parameters(TerrainShaderTranslator.Program shader, TerrainCameraFrame camera, TerrainCameraFrame previous,
                                                    TerrainSceneUniforms scene, TerrainShadowFrame light, net.minecraft.core.SectionPos section, int width, int height, int frame) {
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
        var origin = section.origin();
        var lightView = light.viewFromSection().translate((float) (eye.x - origin.getX()),
                (float) (eye.y - origin.getY()), (float) (eye.z - origin.getZ()));
        var lightProjection = light.legacyProjection();
        layout.put(data, "shadowModelView", lightView);
        layout.put(data, "shadowModelViewInverse", new Matrix4f(lightView).invert());
        layout.put(data, "shadowProjection", lightProjection);
        layout.put(data, "shadowProjectionInverse", new Matrix4f(lightProjection).invert());
        layout.put(data, "gbufferPreviousProjection", previous.legacyProjection(RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()));
        layout.put(data, "gbufferPreviousModelView", previous.viewRotation());
        var previousEye = previous.position();
        layout.putVec3(data, "previousCameraPosition", (float) previousEye.x, (float) previousEye.y, (float) previousEye.z);
        if ("int".equals(shader.uniformTypes().get("frameCounter"))) layout.putInt(data, "frameCounter", frame);
        return data;
    }

}
