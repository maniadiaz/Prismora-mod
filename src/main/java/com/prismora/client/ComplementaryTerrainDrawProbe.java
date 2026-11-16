package com.prismora.client;

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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class ComplementaryTerrainDrawProbe {
    private static final int SIZE = 128;
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";

    private ComplementaryTerrainDrawProbe() {}

    static void submit(TerrainShaderTranslator.Program shader, ByteBuffer payload, GpuBuffer vertices, int indicesCount,
                       GpuTextureView atlas, GpuTextureView noise, GpuSampler repeat, GpuTextureView shadowDepth,
                       GpuTextureView shadowOpaqueDepth, GpuTextureView shadowColor, Runnable completed) {
        var device = RenderSystem.getDevice();
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var color = new GpuTargets.Spec(TargetFormat.R11F_G11F_B10F, 1, 1, false, 0, 0, 0, 0);
        var material = new GpuTargets.Spec(TargetFormat.RGB8, 1, 1, false, 0, 0, 0, 1);
        var normal = new GpuTargets.Spec(TargetFormat.RGBA8_SNORM, 1, 1, false, 0, 0, 0, 0);
        var readback = device.createBuffer(() -> "Complementary terrain witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, SIZE * SIZE * 4L);
        try (var uniforms = device.createBuffer(() -> "Complementary terrain scene", GpuBuffer.USAGE_UNIFORM, payload);
             var targets = new GpuTargets(device, Map.of(0, color, 6, material, 4, normal), SIZE, SIZE, 1024 * 1024);
             var depth = new GpuDepthTargets(device, SIZE, SIZE, 1024 * 1024);
             var specular = device.createTexture("Vanilla block materials without custom emission",
                     GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                     GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
             var specularView = device.createTextureView(specular);
             var program = GpuProgram.withVertices(device, "complementary_gbuffers_terrain_draw", shader.vertex(), shader.fragment(),
                     List.of(color.format(), material.format(), normal.format()), TerrainVertexData.FORMAT, PrimitiveTopology.QUADS,
                     shader.samplers(), DepthStencilState.DEFAULT, List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
             var witness = witnessProgram();
             var output = device.createTexture("Complementary terrain validation", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
             var outputView = device.createTextureView(output)) {
            var known = Map.of("tex", atlas, "noisetex", noise, "specular", specularView,
                    "shadowtex0", shadowDepth, "shadowtex1", shadowOpaqueDepth, "shadowcolor0", shadowColor);
            var required = new TreeSet<>(((FrontendRenderPipeline) program.compiled()).uniformIndices().keySet());
            required.remove(TerrainShaderTranslator.UNIFORM_BLOCK);
            if (!known.keySet().containsAll(required)) {
                throw new IllegalStateException("Unbound Complementary terrain samplers: " + required);
            }

            var encoder = device.createCommandEncoder();
            encoder.clearColorTexture(specular, new org.joml.Vector4f(0));
            var sceneDepth = depth.view(GpuDepthTargets.Stage.SCENE);
            var passDescription = RenderPassDescriptor.builder(() -> "Complementary gbuffers_terrain draw")
                    .withColorAttachment(targets.write(0)).withColorAttachment(targets.write(6)).withColorAttachment(targets.write(4))
                    .withDepthAttachment(sceneDepth).build();
            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            var indexBuffer = indices.getBuffer(indicesCount);
            try (var pass = encoder.createRenderPass(passDescription)) {
                pass.setPipeline(program.compiled());
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                for (String name : required) {
                    pass.setUniform(name, known.get(name), name.equals("noisetex") ? repeat : nearest);
                }
                pass.drawIndexed(indicesCount, 1, 0, 0, 0);
            }
            MrtPass.draw(device, witness, List.of(outputView), Map.of("TerrainDepth", sceneDepth,
                    "Color", targets.write(0), "Material", targets.write(6), "NormalData", targets.write(4)), nearest);
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                int colorCount = 0, materialCount = 0, normalCount = 0;
                try (readback; var mapping = readback.map(true, false)) {
                    var bytes = mapping.data();
                    for (int i = 0; i < SIZE * SIZE * 4; i += 4) {
                        if (bytes.get(i) != 0) colorCount++;
                        if (bytes.get(i + 1) != 0) materialCount++;
                        if (bytes.get(i + 2) != 0) normalCount++;
                    }
                }
                if (colorCount < 16 || materialCount < 16 || normalCount < 16) {
                    throw new IllegalStateException("Complementary terrain outputs have insufficient coverage: "
                            + colorCount + "/" + materialCount + "/" + normalCount);
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TERRAIN_DRAW_PASS: colortex0={}, colortex6={}, colortex4={}, samplers={}",
                        colorCount, materialCount, normalCount, required);
                completed.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static GpuProgram witnessProgram() {
        return new GpuProgram(RenderSystem.getDevice(), "complementary_terrain_readback", HEADER + """
                void main(){vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}
                """, HEADER + """
                uniform sampler2D TerrainDepth;
                uniform sampler2D Color;
                uniform sampler2D Material;
                uniform sampler2D NormalData;
                layout(location=0) out vec4 result;
                void main(){
                    ivec2 p=ivec2(gl_FragCoord.xy);
                    float z=texelFetch(TerrainDepth,p,0).r;
                    vec3 a=texelFetch(Color,p,0).rgb;
                    vec3 b=texelFetch(Material,p,0).rgb;
                    vec4 c=texelFetch(NormalData,p,0);
                    bool covered=z>0.0&&z<=1.0;
                    result=vec4(float(covered&&all(lessThan(abs(a),vec3(65504)))&&length(a)>0.0001),
                        float(covered&&any(greaterThan(b,vec3(0.0)))),float(covered&&length(c.xyz)>0.1),1.0);
                }
                """, List.of(GpuFormat.RGBA8_UNORM), List.of("TerrainDepth", "Color", "Material", "NormalData"));
    }
}
