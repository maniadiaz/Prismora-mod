package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.prismora.Prismora;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.PackContract;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.TerrainCameraFrame;
import com.prismora.render.runtime.TerrainSceneUniforms;
import com.prismora.render.runtime.TerrainUniformLayout;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Draws the pack's procedural sky into colortex0 before the opaque world pass. */
final class ComplementarySkyPass {
    private static final String NAME = "gbuffers_skybasic";

    private ComplementarySkyPass() {}

    static ComplementaryPostCompileProbe.Pass load(LegacyPack pack, Map<String, String> options,
                                                    Map<String, String> environment, String dimension) throws IOException {
        var program = new PackContract(pack, options, environment, dimension).resolve(NAME)
                .orElseThrow(() -> new IOException("Missing Complementary " + NAME));
        var vertex = new ShaderPreprocessor(pack, options, environment).process(program.vertex());
        var fragment = new ShaderPreprocessor(pack, options, environment).process(program.fragment());
        var shader = new TerrainShaderTranslator().translateScreen(vertex, fragment);
        var slots = shader.outputSlots().stream().mapToInt(OptionalInt::orElseThrow).boxed().toList();
        if (!slots.equals(List.of(0))) {
            throw new IOException("Unexpected Complementary sky outputs: " + slots);
        }
        var pass = new ComplementaryPostCompileProbe.Pass(NAME, shader, List.of(ComplementaryPostCompileProbe.format(0)));
        try (var compiled = ComplementaryPostCompileProbe.compile(pass)) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SKY_COMPILE_PASS: {} / {}", program.vertex(), program.fragment());
        }
        return pass;
    }

    static void draw(ComplementaryPostCompileProbe.Pass sky, GpuTextureView color, GpuTextureView noise,
                     GpuSampler repeat, TerrainCameraFrame camera, TerrainSceneUniforms scene) {
        var device = RenderSystem.getDevice();
        var shader = sky.shader();
        var program = ComplementaryPreviewPipelines.get(NAME, () -> ComplementaryPostCompileProbe.compile(sky));
        var active = ((FrontendRenderPipeline) program.compiled()).uniformIndices().keySet();
        if (!List.of(TerrainShaderTranslator.UNIFORM_BLOCK, "noisetex").containsAll(active)) {
            throw new IllegalStateException("Unbound Complementary sky uniforms: " + active);
        }
        var layout = new TerrainUniformLayout(shader.uniformTypes());
        var data = layout.allocate();
        scene.write(layout, data);
        int width = color.getWidth(0), height = color.getHeight(0);
        layout.putFloat(data, "viewWidth", width);
        layout.putFloat(data, "viewHeight", height);
        layout.putFloat(data, "aspectRatio", (float) width / height);
        var projection = camera.legacyProjection(device.getDeviceInfo().isZZeroToOne());
        var view = camera.viewRotation();
        layout.put(data, "gbufferProjection", projection);
        layout.put(data, "gbufferProjectionInverse", new Matrix4f(projection).invert());
        layout.put(data, "gbufferModelView", view);
        layout.put(data, "gbufferModelViewInverse", new Matrix4f(view).invert());
        var eye = camera.position();
        layout.putVec3(data, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);
        var state = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.skyRenderState;
        float angle = state.sunAngle / (float) (2.0 * Math.PI);
        layout.putFloat(data, "sunAngle", angle - (float) Math.floor(angle));
        try (var uniforms = device.createBuffer(() -> "Complementary sky scene", GpuBuffer.USAGE_UNIFORM, data);
             var pass = device.createCommandEncoder().createRenderPass(RenderPassDescriptor.builder(() -> "Complementary procedural sky")
                     .withColorAttachment(color).build())) {
            pass.setPipeline(program.compiled());
            pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
            if (active.contains("noisetex")) {
                pass.setUniform("noisetex", noise, repeat);
            }
            pass.draw(3, 1, 0, 0);
        }
    }
}
