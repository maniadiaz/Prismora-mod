package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.prismora.Prismora;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.PackContract;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.TargetFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

final class ComplementaryPostCompileProbe {
    record Pass(String name, TerrainShaderTranslator.Program shader, List<GpuFormat> formats, List<String> mipInputs) {}

    private ComplementaryPostCompileProbe() {}

    static List<Pass> verify(LegacyPack pack, Map<String, String> options, Map<String, String> environment, String dimension)
            throws IOException {
        var device = RenderSystem.getDevice();
        var contract = new PackContract(pack, options, environment, dimension);
        var programs = contract.programs().values().stream()
                .filter(program -> program.name().matches("deferred\\d*|composite\\d*|final"))
                .sorted(Comparator.comparingInt(program -> order(program.name())))
                .toList();
        var passes = new ArrayList<Pass>();
        var translator = new TerrainShaderTranslator();
        for (var program : programs) {
            String name = program.name();
            if (!contract.enabled(name)) {
                continue;
            }
            if (program.vertex() == null || program.fragment() == null || program.geometry() != null || program.compute() != null) {
                throw new IOException("Unsupported stages in the Complementary post program " + name);
            }
            var vertex = new ShaderPreprocessor(pack, options, environment).process(program.vertex());
            var fragment = new ShaderPreprocessor(pack, options, environment).process(program.fragment());
            var shader = translator.translateScreen(vertex, fragment);
            var outputs = shader.outputSlots().stream().mapToInt(OptionalInt::orElseThrow).boxed().toList();
            List<GpuFormat> formats = name.equals("final")
                    ? List.of(GpuFormat.RGBA8_UNORM)
                    : outputs.stream().map(ComplementaryPostCompileProbe::format).toList();
            var requests = java.util.regex.Pattern.compile("\\bconst\\s+bool\\s+(colortex[0-9]+)MipmapEnabled\\s*=\\s*true\\s*;")
                    .matcher(fragment.source());
            var mipInputs = requests.results().map(result -> result.group(1)).distinct().toList();
            var pass = new Pass(name, shader, formats, mipInputs);
            try (var compiled = compile(pass)) {
                passes.add(pass);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Complementary post program failed: " + name, failure);
            }
            Prismora.LOGGER.info("Complementary post {} linked, outputs={}, samplers={}", name, outputs, shader.samplers());
        }
        if (passes.isEmpty() || !passes.getLast().name().equals("final")) {
            throw new IOException("Complementary has no enabled final program");
        }
        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_POST_COMPILE_PASS: {}", passes.stream().map(Pass::name).toList());
        return List.copyOf(passes);
    }

    static GpuProgram compile(Pass pass) {
        var shader = pass.shader();
        return GpuProgram.withVertices(RenderSystem.getDevice(), "complementary_post_" + pass.name(), shader.vertex(),
                shader.fragment(), pass.formats(), null, PrimitiveTopology.TRIANGLES, shader.samplers(), null,
                List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
    }

    private static int order(String name) {
        if (name.equals("final")) {
            return 300;
        }
        boolean deferred = name.startsWith("deferred");
        String index = name.substring(deferred ? 8 : 9);
        return (deferred ? 0 : 100) + (index.isEmpty() ? 0 : Integer.parseInt(index));
    }

    static GpuFormat format(int slot) {
        return switch (slot) {
            case 0 -> TargetFormat.R11F_G11F_B10F.physical();
            case 1 -> TargetFormat.RGB8_SNORM.physical();
            case 2 -> TargetFormat.RGB16F.physical();
            case 3, 5, 12 -> TargetFormat.RGBA8.physical();
            case 4 -> TargetFormat.RGBA8_SNORM.physical();
            case 6 -> TargetFormat.RGB8.physical();
            case 7, 8 -> TargetFormat.RGBA16F.physical();
            case 13 -> TargetFormat.R8.physical();
            default -> throw new IllegalArgumentException("Unsupported Complementary post output " + slot);
        };
    }
}
