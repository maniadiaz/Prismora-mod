package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.prismora.Prismora;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.PackContract;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class ComplementaryEntityPass {
    record Program(String name, TerrainShaderTranslator.Program shader, List<Integer> slots) {}

    private ComplementaryEntityPass() {}

    static Program load(String name, LegacyPack pack, Map<String, String> options, Map<String, String> environment, String dimension)
            throws IOException {
        var program = new PackContract(pack, options, environment, dimension).resolve(name)
                .orElseThrow(() -> new IOException("Missing " + name));
        var vertex = new ShaderPreprocessor(pack, options, environment).process(program.vertex());
        var fragment = new ShaderPreprocessor(pack, options, environment).process(program.fragment());
        var shader = new TerrainShaderTranslator().translate(vertex, fragment, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        var result = new Program(name, shader, shader.outputSlots().stream().mapToInt(OptionalInt::orElseThrow).boxed().toList());
        try (var compiled = compile(result)) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_ENTITIES_COMPILE_PASS: {} outputs {}", name, result.slots());
        }
        return result;
    }

    private static GpuProgram compile(Program program) {
        var shader = program.shader();
        var fragment = shader.fragment().replace("gl_FragCoord.z", "(1.0 - gl_FragCoord.z)")
                .replaceFirst("\\bvoid main\\s*\\(", "void prismoraEntityMain(") + """

                void main(){
                    prismoraEntityMain();
                    if (prismoraFragData[0].a <= 0.1) discard;
                }
                """;
        return GpuProgram.withVertices(RenderSystem.getDevice(), program.name(), shader.vertex(), fragment,
                program.slots().stream().map(ComplementaryPostCompileProbe::format).toList(),
                TerrainVertexData.FORMAT, PrimitiveTopology.QUADS, shader.samplers(), DepthStencilState.DEFAULT,
                List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
    }

    static void draw(Program program, List<ComplementaryFeatureCapture.Draw> draws, java.util.function.IntFunction<GpuTextureView> target, GpuTextureView depth,
                     Map<String, GpuTextureView> inputs, GpuSampler repeat, TerrainCameraFrame camera,
                     TerrainSceneUniforms scene, TerrainShadowFrame light, SectionPos section) {
        var device = RenderSystem.getDevice();
        var pipeline = ComplementaryPreviewPipelines.get(program.name(), () -> compile(program));
        if (draws.isEmpty()) {
            return;
        }
        var layout = new TerrainUniformLayout(program.shader().uniformTypes());
        var data = layout.allocate();
        scene.write(layout, data);
        int width = depth.getWidth(0), height = depth.getHeight(0);
        layout.putFloat(data, "viewWidth", width);
        layout.putFloat(data, "viewHeight", height);
        layout.putFloat(data, "aspectRatio", (float) width / height);
        var projection = camera.legacyProjection(device.getDeviceInfo().isZZeroToOne());
        var view = camera.viewRotation();
        var modelView = camera.modelViewFromSection(section);
        var state = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState;
        var geometryProjection = program.name().equals("gbuffers_hand")
                ? new Matrix4f().perspective((float) Math.toRadians(state.cameraRenderState.hudFov), scene.aspectRatio(), 0.05f, scene.far())
                : projection;
        layout.put(data, "prismoraProjection", geometryProjection);
        layout.put(data, "prismoraModelView", modelView);
        layout.put(data, "prismoraNormalMatrix", new Matrix3f(modelView).invert().transpose());
        layout.put(data, "gbufferProjection", projection);
        layout.put(data, "gbufferProjectionInverse", new Matrix4f(projection).invert());
        layout.put(data, "gbufferModelView", view);
        layout.put(data, "gbufferModelViewInverse", new Matrix4f(view).invert());
        layout.putMatrixPair(data, "prismoraTextureMatrix", new Matrix4f(),
                new Matrix4f().scaling(1f / 256f, 1f / 256f, 1f / 256f).m30(1f / 32f).m31(1f / 32f));
        var eye = camera.position();
        var origin = section.origin();
        var lightView = light.viewFromSection().translate((float)(eye.x - origin.getX()),
                (float)(eye.y - origin.getY()), (float)(eye.z - origin.getZ()));
        var lightProjection = light.legacyProjection();
        layout.put(data, "shadowProjection", lightProjection);
        layout.put(data, "shadowProjectionInverse", new Matrix4f(lightProjection).invert());
        layout.put(data, "shadowModelView", lightView);
        layout.put(data, "shadowModelViewInverse", new Matrix4f(lightView).invert());
        layout.putVec3(data, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        float angle = state.skyRenderState.sunAngle / (float) (2.0 * Math.PI);
        layout.putFloat(data, "sunAngle", angle - (float) Math.floor(angle));
        var fog = state.cameraRenderState.fogData;
        if (program.shader().uniformTypes().containsKey("prismoraFogParams")) {
            layout.putVec4(data, "prismoraFogParams", fog.renderDistanceStart,
                    1f / Math.max(0.00001f, fog.renderDistanceEnd - fog.renderDistanceStart), fog.renderDistanceEnd, 0f);
        }
        var known = new HashMap<>(inputs);
        known.put("lightmap", Minecraft.getInstance().gameRenderer.levelLightmap());
        var active = ((FrontendRenderPipeline) pipeline.compiled()).uniformIndices().keySet();
        var descriptor = RenderPassDescriptor.builder(() -> "Complementary world entities").withDepthAttachment(depth);
        for (int slot : program.slots()) {
            descriptor.withColorAttachment(target.apply(slot));
        }
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int maxIndices = draws.stream().mapToInt(ComplementaryFeatureCapture.Draw::indices).max().orElseThrow();
        var indexBuffer = indices.getBuffer(maxIndices);
        try (var uniforms = device.createBuffer(() -> "Complementary entity scene", GpuBuffer.USAGE_UNIFORM, data);
             var pass = device.createCommandEncoder().createRenderPass(descriptor.build())) {
            pass.setPipeline(pipeline.compiled());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
            for (var draw : draws) {
                known.put("tex", draw.texture());
                for (String sampler : active) {
                    if (sampler.equals(TerrainShaderTranslator.UNIFORM_BLOCK)) {
                        continue;
                    }
                    var texture = known.get(sampler);
                    if (texture == null) {
                        throw new IllegalStateException("Missing Complementary entity texture " + sampler);
                    }
                    pass.setUniform(sampler, texture, sampler.equals("noisetex") ? repeat : nearest);
                }
                pass.setVertexBuffer(0, draw.vertices().slice());
                pass.drawIndexed(draw.indices(), 1, 0, 0, 0);
            }
        }
    }
}
