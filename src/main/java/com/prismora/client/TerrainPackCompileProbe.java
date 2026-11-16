package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.prismora.render.runtime.TerrainVertexData;
import com.prismora.Prismora;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.PackContract;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.GpuProgram;

import java.io.IOException;
import java.util.List;
import java.util.Map;

final class TerrainPackCompileProbe {
    private TerrainPackCompileProbe() {}

    record Programs(TerrainShaderTranslator.Program terrain, ComplementaryShadowSmokeProbe.Inputs shadow, List<ComplementaryPostCompileProbe.Pass> post) {}

    static Programs verify(LegacyPack pack) throws IOException {
        var options = Map.<String, String>of();
        var environment = ShaderPreprocessor.environment();
        var dimension = "minecraft:overworld";
        var terrain = new PackContract(pack, options, environment, dimension).resolve("gbuffers_terrain").orElseThrow();
        var vertex = new ShaderPreprocessor(pack, options, environment).process(terrain.vertex());
        var fragment = new ShaderPreprocessor(pack, options, environment).process(terrain.fragment());
        var device = RenderSystem.getDevice();
        var translated = new TerrainShaderTranslator().translate(vertex, fragment, device.getDeviceInfo().isZZeroToOne());
        var slots = translated.outputSlots().stream().mapToInt(java.util.OptionalInt::orElseThrow).boxed().toList();
        if (!slots.equals(List.of(0, 6, 4))) {
            throw new IOException("Unexpected terrain targets: " + slots);
        }

        try (var program = GpuProgram.withVertices(device, "complementary_terrain_compile", translated.vertex(), translated.fragment(),
                List.of(GpuFormat.RG11B10_FLOAT, GpuFormat.RGBA8_UNORM, GpuFormat.RGBA8_SNORM),
                TerrainVertexData.FORMAT, PrimitiveTopology.QUADS, translated.samplers(), DepthStencilState.DEFAULT,
                List.of(TerrainShaderTranslator.UNIFORM_BLOCK))) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TERRAIN_COMPILE_PASS: {} / {}, outputs {}, {} uniforms and {} samplers",
                    terrain.vertex(), terrain.fragment(), slots, translated.uniformTypes().size(), translated.samplers().size());
            return new Programs(translated, verifyShadow(pack, options, environment, dimension),
                    ComplementaryPostCompileProbe.verify(pack, options, environment, dimension));
        } catch (RuntimeException error) {
            Prismora.LOGGER.error("Terrain compiler source map: {}", translated.sourceFiles());
            throw error;
        }
    }

    private static ComplementaryShadowSmokeProbe.Inputs verifyShadow(LegacyPack pack, Map<String, String> options, Map<String, String> environment,
                                     String dimension) throws IOException {
        var contract = new PackContract(pack, options, environment, dimension);
        var shadow = contract.resolve("shadow").orElseThrow();
        var vertex = new ShaderPreprocessor(pack, options, environment).process(shadow.vertex());
        var fragment = new ShaderPreprocessor(pack, options, environment).process(shadow.fragment());
        var device = RenderSystem.getDevice();
        var translated = new TerrainShaderTranslator().translate(vertex, fragment, device.getDeviceInfo().isZZeroToOne());
        var outputs = translated.outputSlots().stream().mapToInt(java.util.OptionalInt::orElseThrow).boxed().toList();
        if (!outputs.equals(List.of(0)) && !outputs.equals(List.of(0, 1))) {
            throw new IOException("Unexpected Complementary shadow targets: " + outputs);
        }
        try (var program = GpuProgram.withVertices(device, "complementary_shadow_compile", translated.vertex(), translated.fragment(),
                java.util.Collections.nCopies(outputs.size(), GpuFormat.RGBA8_UNORM), TerrainVertexData.FORMAT,
                PrimitiveTopology.QUADS, translated.samplers(), DepthStencilState.DEFAULT, List.of(TerrainShaderTranslator.UNIFORM_BLOCK))) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SHADOW_COMPILE_PASS: {} / {}, outputs {}",
                    shadow.vertex(), shadow.fragment(), outputs);
            return new ComplementaryShadowSmokeProbe.Inputs(translated, texture(pack, contract, "texture.noise"),
                    texture(pack, contract, "texture.gbuffers.gaux4"));
        } catch (RuntimeException error) {
            Prismora.LOGGER.error("Shadow compiler source map: {}", translated.sourceFiles());
            throw error;
        }
    }

    private static byte[] texture(LegacyPack pack, PackContract contract, String key) throws IOException {
        var reference = contract.properties().get(key);
        if (reference == null) throw new IOException("Missing Complementary texture property " + key);
        var path = ShaderPreprocessor.resolve("shaders/shaders.properties", reference);
        var image = pack.files().get(path);
        if (image == null) throw new IOException("Missing Complementary texture " + path);
        return image;
    }
}
