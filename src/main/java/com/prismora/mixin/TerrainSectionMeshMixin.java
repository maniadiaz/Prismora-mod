package com.prismora.mixin;

import com.prismora.render.runtime.TerrainSectionGeometry;
import com.prismora.render.runtime.TerrainVertexData;
import java.util.List;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompiledSectionMesh.class, remap = false)
abstract class TerrainSectionMeshMixin implements TerrainSectionGeometry {
    @Unique private volatile List<TerrainVertexData> prismora$geometry = List.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void prismora$copyGeometry(TranslucencyPointOfView pointOfView, SectionCompiler.Results results,
                                       long startTime, CallbackInfo callback) {
        prismora$setFaces(((TerrainSectionGeometry) (Object) results).prismora$faces());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void prismora$releaseGeometry(CallbackInfo callback) {
        prismora$setFaces(List.of());
    }

    @Override
    public List<TerrainVertexData> prismora$faces() {
        return prismora$geometry;
    }

    @Override
    public void prismora$setFaces(List<TerrainVertexData> faces) {
        prismora$geometry = faces;
    }
}
