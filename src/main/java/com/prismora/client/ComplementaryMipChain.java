package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.*;
import com.prismora.render.runtime.GpuProgram;

import java.util.ArrayList;
import java.util.List;

/** Regenerates a readable mip chain from the current version of a color attachment. */
final class ComplementaryMipChain implements AutoCloseable {
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private final List<AutoCloseable> owned = new ArrayList<>();
    private final GpuTexture chain;
    private final GpuTexture scratch;
    private final GpuTextureView sampled;
    private final List<GpuTextureView> outputs = new ArrayList<>();

    ComplementaryMipChain(String label, GpuFormat format, int width, int height) {
        var device = RenderSystem.getDevice();
        int levels = Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(width, height));
        try {
            chain = own(device.createTexture(label + " mip chain", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                    format, width, height, 1, levels));
            scratch = own(device.createTexture(label + " mip staging", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                    format, width, height, 1, levels));
            sampled = own(device.createTextureView(chain));
            for (int level = 1; level < levels; level++) {
                outputs.add(own(device.createTextureView(scratch, level, 1)));
            }
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private <T extends AutoCloseable> T own(T resource) {
        owned.add(resource);
        return resource;
    }

    int levels() {
        return outputs.size() + 1;
    }

    GpuTextureView render(GpuTextureView source) {
        var device = RenderSystem.getDevice();
        var baseCopy = device.createCommandEncoder();
        baseCopy.copyTextureToTexture(source.texture(), chain, 0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
        baseCopy.submit();
        if (outputs.isEmpty()) {
            return sampled;
        }
        var format = chain.getFormat();
        var shader = ComplementaryPreviewPipelines.get("mip " + format, () -> GpuProgram.withVertices(device,
                "complementary_mip_" + format.name().toLowerCase(java.util.Locale.ROOT), HEADER + """
                void main(){
                    vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                    gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
                }
                """, HEADER + """
                uniform sampler2D Source;
                layout(std140) uniform MipParams { int sourceLevel; };
                layout(location=0) out vec4 result;
                void main(){
                    ivec2 size=textureSize(Source,sourceLevel);
                    ivec2 base=ivec2(gl_FragCoord.xy)*2;
                    ivec2 edge=size-ivec2(1);
                    result=0.25*(texelFetch(Source,min(base,edge),sourceLevel)
                                +texelFetch(Source,min(base+ivec2(1,0),edge),sourceLevel)
                                +texelFetch(Source,min(base+ivec2(0,1),edge),sourceLevel)
                                +texelFetch(Source,min(base+ivec2(1,1),edge),sourceLevel));
                }
                """, List.of(format), null, com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES,
                List.of("Source"), null, List.of("MipParams")));
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        for (int index = 0; index < outputs.size(); index++) {
            var data = java.nio.ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());
            data.putInt(0, index);
            var output = outputs.get(index);
            var encoder = device.createCommandEncoder();
            try (var params = device.createBuffer(() -> "Complementary mip level", com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_UNIFORM, data);
                 var pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> "Complementary mip downsample")
                    .withColorAttachment(output).build())) {
                pass.setPipeline(shader.compiled());
                pass.setUniform("Source", sampled, nearest);
                pass.setUniform("MipParams", params);
                pass.draw(3, 1, 0, 0);
            }
            encoder.copyTextureToTexture(scratch, chain, index + 1, 0, 0, 0, 0, output.getWidth(0), output.getHeight(0));
            encoder.submit();
        }
        return sampled;
    }

    void verifyTail() {
        var device = RenderSystem.getDevice();
        int level = levels() - 1;
        if (chain.getWidth(level) != 1 || chain.getHeight(level) != 1 || chain.getFormat() != GpuFormat.RG11B10_FLOAT) {
            throw new IllegalStateException("Unexpected Complementary colortex0 mip tail");
        }
        var readback = device.createBuffer(() -> "Complementary mip tail witness",
                com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST
                        | com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_MAP_READ, Integer.BYTES);
        try {
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(chain, readback, 0, () -> {
                try (readback; var mapping = readback.map(true, false)) {
                    if (mapping.data().getInt(0) == 0) {
                        throw new IllegalStateException("Complementary colortex0 mip tail contains no rendered color");
                    }
                }
                com.prismora.Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_MIP_TAIL_PASS: rendered 1x1 texel at level {}", level);
            }, level);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (var resource : owned.reversed()) {
            try {
                resource.close();
            } catch (Exception error) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to release Complementary mip chain", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        owned.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
