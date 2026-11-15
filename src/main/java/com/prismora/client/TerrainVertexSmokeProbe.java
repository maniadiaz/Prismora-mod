package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.TerrainVertexData;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

final class TerrainVertexSmokeProbe {
    private static final int CORNERS = 4;
    private static final int PLANES = 4;
    private static final int WIDTH = 256;
    private static final String VERTEX = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location=0) in vec3 Position;
            layout(location=1) in vec2 UV0;
            layout(location=2) in float mc_Entity;
            layout(location=3) in vec3 Normal;
            layout(location=4) in vec4 at_tangent;
            layout(location=5) in vec2 mc_midTexCoord;
            layout(location=0) flat out vec4 positionAndId;
            layout(location=1) flat out vec4 uvAndCenter;
            layout(location=2) flat out vec3 normal;
            layout(location=3) flat out vec4 tangent;
            void main() {
                int column = gl_VertexIndex % %WIDTH%;
                int row = gl_VertexIndex / %WIDTH%;
                vec2 pixel = vec2(column, row) + vec2(0.5);
                gl_Position = vec4(pixel * 2.0 / vec2(%WIDTH%, %HEIGHT%) - 1.0, 0.0, 1.0);
                gl_PointSize = 1.0;
                positionAndId = vec4(Position, mc_Entity);
                uvAndCenter = vec4(UV0, mc_midTexCoord);
                normal = Normal;
                tangent = at_tangent;
            }
            """;
    private static final String FRAGMENT = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location=0) flat in vec4 positionAndId;
            layout(location=1) flat in vec4 uvAndCenter;
            layout(location=2) flat in vec3 normal;
            layout(location=3) flat in vec4 tangent;
            layout(location=0) out vec4 outPosition;
            layout(location=1) out vec4 outUv;
            layout(location=2) out vec4 outNormal;
            layout(location=3) out vec4 outTangent;
            void main() {
                outPosition = positionAndId;
                outUv = uvAndCenter;
                outNormal = vec4(normal, 1.0);
                outTangent = tangent;
            }
            """;

    private TerrainVertexSmokeProbe() {}

    static void submit(List<TerrainVertexData> faces, Runnable completed) {
        var device = RenderSystem.getDevice();
        var remaining = new java.util.concurrent.atomic.AtomicInteger(2);
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
                TerrainIndexedSmokeProbe.submit(buffer, faces.size(), resultReady);
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
        }
        Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_VERTEX_PASS: {} vertices from {} real block-model faces matched", faces.size() * CORNERS, faces.size());
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
