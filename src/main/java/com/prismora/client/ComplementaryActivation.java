package com.prismora.client;

import com.prismora.compat.BlockMaterials;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.pack.PackLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ComplementaryActivation {
    private ComplementaryActivation() {}

    public static boolean supports(PackLoader.ShaderPack pack) {
        if (!LegacyPack.isLegacy(pack)) return false;
        var files = pack.files();
        var water = files.get("shaders/program/gbuffers_water.glsl");
        return water != null && files.containsKey("shaders/world0/gbuffers_terrain.vsh")
                && files.containsKey("shaders/lib/textures/cloud-water.png")
                && new String(water, StandardCharsets.UTF_8).contains("Complementary Shaders");
    }

    record Prepared(TerrainPackCompileProbe.Programs programs, BlockMaterials materials) {
        void install() {
            TerrainSmokeProbe.enableRuntime(programs, materials);
        }
    }

    static Prepared prepare(PackLoader.ShaderPack pack, String dimension) throws IOException {
        var legacy = LegacyPack.inspect(pack.id(), pack.files());
        return new Prepared(TerrainPackCompileProbe.verify(legacy, dimension),
                BlockMaterials.load(legacy, Map.of(), ShaderPreprocessor.environment()));
    }
}
