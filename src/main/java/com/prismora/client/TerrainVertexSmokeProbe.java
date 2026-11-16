package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.TerrainVertexData;
import com.prismora.render.runtime.TerrainShadowFrame;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

final class TerrainVertexSmokeProbe {
    private static final int CORNERS = 4;
    private static final int PLANES = 6;
    private static final int WIDTH = 256;
    private static final String VERTEX = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location=0) in vec3 Position;
            layout(location=1) in vec2 UV0;
            layout(location=2) in vec4 mc_Entity;
            layout(location=3) in vec3 Normal;
            layout(location=4) in vec4 at_tangent;
            layout(location=5) in vec4 mc_midTexCoord;
            layout(location=6) in vec4 Color;
            layout(location=7) in ivec2 UV2;
            layout(location=0) flat out vec4 positionAndId;
            layout(location=1) flat out vec4 uvAndCenter;
            layout(location=2) flat out vec3 normal;
            layout(location=3) flat out vec4 tangent;
            layout(location=4) flat out vec4 color;
            layout(location=5) flat out vec4 lightAndPadding;
            void main() {
                int column = gl_VertexIndex % %WIDTH%;
                int row = gl_VertexIndex / %WIDTH%;
                vec2 pixel = vec2(column, row) + vec2(0.5);
                gl_Position = vec4(pixel * 2.0 / vec2(%WIDTH%, %HEIGHT%) - 1.0, 0.0, 1.0);
                gl_PointSize = 1.0;
                positionAndId = vec4(Position, mc_Entity.x);
                uvAndCenter = vec4(UV0, mc_midTexCoord.xy);
                normal = Normal * (mc_Entity.w == 1.0 && mc_midTexCoord.w == 1.0 ? 1.0 : 0.0);
                tangent = at_tangent;
                color = Color;
                lightAndPadding = vec4(vec2(UV2), mc_Entity.y + mc_Entity.z, mc_midTexCoord.z);
            }
            """;
    private static final String FRAGMENT = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location=0) flat in vec4 positionAndId;
            layout(location=1) flat in vec4 uvAndCenter;
            layout(location=2) flat in vec3 normal;
            layout(location=3) flat in vec4 tangent;
            layout(location=4) flat in vec4 color;
            layout(location=5) flat in vec4 lightAndPadding;
            layout(location=0) out vec4 outPosition;
            layout(location=1) out vec4 outUv;
            layout(location=2) out vec4 outNormal;
            layout(location=3) out vec4 outTangent;
            layout(location=4) out vec4 outColor;
            layout(location=5) out vec4 outLight;
            void main() {
                outPosition = positionAndId;
                outUv = uvAndCenter;
                outNormal = vec4(normal, 1.0);
                outTangent = tangent;
                outColor = color;
                outLight = lightAndPadding;
            }
            """;

    private TerrainVertexSmokeProbe() {}

    static void submit(List<TerrainVertexData> faces, com.prismora.render.runtime.TerrainCameraFrame camera, net.minecraft.core.SectionPos section,
                       TerrainShadowFrame light, ComplementaryShadowSmokeProbe.Inputs shadow, Runnable completed) {
        var device = RenderSystem.getDevice();
        var remaining = new java.util.concurrent.atomic.AtomicInteger(4);
        Runnable resultReady = () -> {
            if (remaining.decrementAndGet() == 0) {
                completed.run();
            }
        };
        int vertices = Math.multiplyExact(faces.size(), CORNERS);
        int height = (vertices + WIDTH - 1) / WIDTH;
        int planeBytes = WIDTH * height * 4 * Float.BYTES;
        String vertex = VERTEX.replace("%WIDTH%", Integer.toString(WIDTH)).replace("%HEIGHT%", Integer.toString(height));
        ByteBuffer staging = MemoryUtil.memAlloc(vertices * TerrainVertexData.FORMAT.getVertexSize());
        GpuBuffer buffer;
        try {
            for (int index = 0; index < vertices; index++) {
                faces.get(index / CORNERS).writeVertex(staging, index % CORNERS);
            }
            buffer = device.createBuffer(() -> "Prismora terrain attributes", GpuBuffer.USAGE_VERTEX, staging.flip());
        } finally {
            MemoryUtil.memFree(staging);
        }

        var formats = Collections.nCopies(PLANES, GpuFormat.RGBA32_FLOAT);
        var descriptor = RenderPassDescriptor.builder(() -> "Prismora terrain attribute round trip");
        var textures = new GpuTexture[PLANES];
        var readback = device.createBuffer(() -> "Prismora terrain attribute readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, PLANES * planeBytes);
        var encoder = device.createCommandEncoder();
        try (buffer;
             var program = GpuProgram.withVertices(device, "terrain_vertex_probe", vertex, FRAGMENT, formats,
                     TerrainVertexData.FORMAT, PrimitiveTopology.POINTS)) {
            var views = new com.mojang.renderpearl.api.textures.GpuTextureView[PLANES];
            try {
                for (int i = 0; i < PLANES; i++) {
                    textures[i] = device.createTexture("Prismora terrain vertex plane " + i,
                            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                            formats.get(i), WIDTH, height, 1, 1);
                    views[i] = device.createTextureView(textures[i]);
                    encoder.clearColorTexture(textures[i], new org.joml.Vector4f(0));
                    descriptor.withColorAttachment(views[i]);
                }
                try (var pass = encoder.createRenderPass(descriptor.build())) {
                    pass.setPipeline(program.compiled());
                    pass.setVertexBuffer(0, buffer.slice());
                    pass.draw(vertices, 1, 0, 0);
                }
                for (int i = 0; i < PLANES; i++) {
                    boolean last = i == PLANES - 1;
                    encoder.copyTextureToBuffer(textures[i], readback, (long) i * planeBytes, last ? () -> {
                        try (readback; var mapping = readback.map(true, false)) {
                            verify(faces, planeBytes, mapping.data().order(ByteOrder.nativeOrder()));
                        }
                        resultReady.run();
                    } : () -> {}, 0);
                }
                encoder.submit();
                TerrainIndexedSmokeProbe.submit(buffer, faces.size(), camera.clipFromSection(section), resultReady);
                TerrainIndexedSmokeProbe.submitShadow(buffer, faces.size(), light, resultReady);
                ComplementaryShadowSmokeProbe.submit(shadow, buffer, faces.size(), light, camera, section, resultReady);
            } finally {
                for (int i = 0; i < PLANES; i++) {
                    if (views[i] != null) views[i].close();
                    if (textures[i] != null) textures[i].close();
                }
            }
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static void verify(List<TerrainVertexData> faces, int planeBytes, ByteBuffer result) {
        for (int index = 0; index < faces.size() * CORNERS; index++) {
            var data = faces.get(index / CORNERS);
            var attrs = data.attributes();
            var corner = data.corners().get(index % CORNERS);
            check(result, index, planeBytes, 0, corner.x(), corner.y(), corner.z(), data.materialId());
            check(result, index, planeBytes, 1, corner.u(), corner.v(), attrs.midU(), attrs.midV());
            check(result, index, planeBytes, 2, attrs.nx(), attrs.ny(), attrs.nz(), 1f);
            check(result, index, planeBytes, 3, attrs.tx(), attrs.ty(), attrs.tz(), attrs.handedness());
            int argb = corner.argb();
            check(result, index, planeBytes, 4, ((argb >>> 16) & 255) / 255f, ((argb >>> 8) & 255) / 255f,
                    (argb & 255) / 255f, (argb >>> 24) / 255f);
            int light = corner.light();
            check(result, index, planeBytes, 5, (short) light, (short) (light >>> 16), 0, 0);
        }
        Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_VERTEX_PASS: {} vertices from {} real block-model faces matched, including RGBA and lightmap coordinates", faces.size() * CORNERS, faces.size());
    }

    private static void check(ByteBuffer result, int vertex, int planeBytes, int plane, float... expected) {
        for (int component = 0; component < expected.length; component++) {
            int offset = plane * planeBytes + (vertex * 4 + component) * Float.BYTES;
            float actual = result.getFloat(offset);
            if (!(Math.abs(expected[component] - actual) <= 0.00001f)) {
                throw new IllegalStateException("Terrain vertex " + vertex + ", plane " + plane + ", component "
                        + component + ": expected " + expected[component] + ", got " + actual);
            }
        }
    }
}
