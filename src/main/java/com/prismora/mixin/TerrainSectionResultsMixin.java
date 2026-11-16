package com.prismora.mixin;

import com.prismora.render.runtime.TerrainSectionGeometry;
import com.prismora.render.runtime.TerrainVertexData;
import java.util.List;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = SectionCompiler.Results.class, remap = false)
abstract class TerrainSectionResultsMixin implements TerrainSectionGeometry {
    @Unique private List<TerrainVertexData> prismora$geometry = List.of();

    @Override
    public List<TerrainVertexData> prismora$faces() {
        return prismora$geometry;
    }

    @Override
    public void prismora$setFaces(List<TerrainVertexData> faces) {
        prismora$geometry = faces;
    }
}
