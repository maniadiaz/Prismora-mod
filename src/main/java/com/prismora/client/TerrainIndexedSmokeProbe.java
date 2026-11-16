package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.GpuTargets;
import com.prismora.render.runtime.MrtPass;
import com.prismora.render.runtime.TerrainVertexData;
import com.prismora.render.runtime.ShadowDepthSampling;
import com.prismora.render.runtime.TerrainShadowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.List;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import java.util.Map;

final class TerrainIndexedSmokeProbe {
    private static final int SIZE = 128;
    private static final String HEADER = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            """;
    private static final String VERTEX = HEADER + """
            layout(std140) uniform TerrainCamera { mat4 ClipFromSection; };
            layout(location=0) in vec3 Position;
            layout(location=1) in vec2 UV0;
            layout(location=2) in vec4 mc_Entity;
            layout(location=3) in vec3 Normal;
            layout(location=0) out vec2 uv;
            layout(location=1) flat out float blockId;
            layout(location=2) flat out vec3 faceNormal;
            void main() {
                gl_Position = ClipFromSection * vec4(Position, 1.0);
                uv = UV0;
                blockId = mc_Entity.x;
                faceNormal = Normal;
            }
            """;
    private static final String FRAGMENT = HEADER + """
            uniform sampler2D BlockAtlas;
            layout(location=0) in vec2 uv;
            layout(location=1) flat in float blockId;
            layout(location=2) flat in vec3 faceNormal;
            layout(location=0) out vec4 color;
            layout(location=1) out vec4 material;
            layout(location=2) out vec4 normal;
            void main() {
                vec4 texel = texture(BlockAtlas, uv);
                if (texel.a < 0.1) discard;
                color = vec4(texel.rgb, 1.0);
                material = vec4(blockId, 0.0, 0.0, 1.0);
                normal = vec4(faceNormal, 1.0);
            }
            """;
    private static final String CHECK_VERTEX = HEADER + """
            void main() {
                vec2 uv = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String CHECK_FRAGMENT = HEADER + ShadowDepthSampling.GLSL + """
            uniform sampler2D Color;
            uniform sampler2D Material;
            uniform sampler2D NormalData;
            uniform sampler2D Depth;
            layout(location=0) out vec4 witness;
            void main() {
                ivec2 pixel = ivec2(gl_FragCoord.xy);
                vec4 c = texelFetch(Color, pixel, 0);
                vec4 m = texelFetch(Material, pixel, 0);
                vec4 n = texelFetch(NormalData, pixel, 0);
                float z = texelFetch(Depth, pixel, 0).r;
                bool hit = c.a > 0.5;
                bool equalCoverage = hit == (m.a > 0.5) && hit == (n.a > 0.5) && hit == (z > 0.0);
                bool valid = equalCoverage && (!hit || (abs(length(n.xyz) - 1.0) < 0.001 && m.r >= -1.0 && z <= 1.0));
                if (hit) {
                    vec2 uv = (vec2(pixel) + 0.5) / vec2(textureSize(Depth,0));
                    float legacy = 1.0 - z;
                    float visible = prismoraShadow2D(Depth, vec3(uv,legacy-0.01)).r;
                    float occluded = prismoraShadow2D(Depth, vec3(uv,legacy+0.01)).r;
                    valid = valid && visible > 0.99 && occluded < 0.01;
                }
                witness = valid ? vec4(0.0, hit ? 1.0 : 0.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
            }
            """;

    private TerrainIndexedSmokeProbe() {}

    static void submit(GpuBuffer vertices, int faceCount, Matrix4fc clipFromSection, Runnable completed) {
        submit(vertices, faceCount, clipFromSection, null, completed);
    }

    static void submitShadow(GpuBuffer vertices, int faceCount, TerrainShadowFrame light, Runnable completed) {
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        submit(vertices, faceCount, light.clipFromSection(zeroToOne), light.body(), completed);
    }

    private static void submit(GpuBuffer vertices, int faceCount, Matrix4fc clipFromSection, TerrainShadowFrame.Body body, Runnable completed) {
        var device = RenderSystem.getDevice();
        var format = GpuFormat.RGBA32_FLOAT;
        var spec = new GpuTargets.Spec(format, 1, 1, false, 0, 0, 0, 0);
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var readback = device.createBuffer(() -> "Prismora indexed terrain witness", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, SIZE * SIZE * 4L);
        int depthUsage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
        var matrixBytes = BufferUtils.createByteBuffer(16 * Float.BYTES);
        clipFromSection.get(matrixBytes);
        try (var camera = device.createBuffer(() -> "Prismora terrain camera", GpuBuffer.USAGE_UNIFORM, matrixBytes);
             var targets = new GpuTargets(device, Map.of(0, spec, 6, spec, 4, spec), SIZE, SIZE, 2L * 1024 * 1024);
             var depth = device.createTexture("Prismora indexed terrain depth", depthUsage, GpuFormat.D32_FLOAT, SIZE, SIZE, 1, 1);
             var depthView = device.createTextureView(depth);
             var output = device.createTexture("Prismora indexed terrain result", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
             var outputView = device.createTextureView(output);
             var program = GpuProgram.withVertices(device, "terrain_indexed_probe", VERTEX, FRAGMENT, List.of(format, format, format),
                     TerrainVertexData.FORMAT, PrimitiveTopology.QUADS, List.of("BlockAtlas"), DepthStencilState.DEFAULT, List.of("TerrainCamera"));
             var check = new GpuProgram(device, "terrain_indexed_check", CHECK_VERTEX, CHECK_FRAGMENT,
                     List.of(GpuFormat.RGBA8_UNORM), List.of("Color", "Material", "NormalData", "Depth"))) {
            var encoder = device.createCommandEncoder();
            encoder.clearDepthTexture(depth, 0.0);
            var descriptor = RenderPassDescriptor.builder(() -> "Prismora indexed block model faces")
                    .withColorAttachment(targets.write(0))
                    .withColorAttachment(targets.write(6))
                    .withColorAttachment(targets.write(4))
                    .withDepthAttachment(depthView)
                    .build();
            int indexCount = faceCount * 6;
            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            var indexBuffer = indices.getBuffer(indexCount);
            try (var pass = encoder.createRenderPass(descriptor)) {
                pass.setPipeline(program.compiled());
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setUniform("TerrainCamera", camera);
                pass.setUniform("BlockAtlas", atlas, sampler);
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
            MrtPass.draw(device, check, List.of(outputView),
                    Map.of("Color", targets.write(0), "Material", targets.write(6), "NormalData", targets.write(4), "Depth", depthView), sampler);
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                int covered = 0;
                try (readback; var mapping = readback.map(true, false)) {
                    var bytes = mapping.data();
                    for (int offset = 0; offset < SIZE * SIZE * 4; offset += 4) {
                        if (bytes.get(offset) != 0) {
                            throw new IllegalStateException("Indexed terrain color, material, normal and depth disagree at pixel " + offset / 4);
                        }
                        if ((bytes.get(offset + 1) & 255) == 255) {
                            covered++;
                        }
                    }
                }
                if (covered < 16) {
                    throw new IllegalStateException("Indexed terrain geometry did not cover enough pixels: " + covered);
                }
                if (body == null) {
                    Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_CAMERA_PASS: {} faces submitted, {} pixels with matching color, material, normal and depth",
                            faceCount, covered);
                } else {
                    Prismora.LOGGER.info("PRISMORA_WORLD_SHADOW_GEOMETRY_PASS: {} light, {} real faces submitted, {} covered texels with matching light and occlusion comparisons",
                            body, faceCount, covered);
                }
                completed.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }
}
