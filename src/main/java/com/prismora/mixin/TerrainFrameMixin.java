package com.prismora.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.prismora.client.TerrainSmokeProbe;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, remap = false)
abstract class TerrainFrameMixin {
    @Shadow @Final private LevelTargetBundle targets;
    @Shadow @Final private TextureManager textureManager;
    @Shadow private GpuSampler chunkLayerSampler;

    @Inject(method = "addMainPass", at = @At("TAIL"))
    private void prismora$addTerrainProbe(FrameGraphBuilder graph, FeatureRenderDispatcher.PreparedFrame features,
                                         GpuBufferSlice fog, ChunkSectionsToRender chunks, boolean consistentDepth, CallbackInfo callback) {
        if (!Boolean.getBoolean("prismora.terrainSmoke") || !TerrainSmokeProbe.shouldDraw()) {
            return;
        }
        var pass = graph.addPass("Prismora terrain MRT probe");
        pass.reads(targets.main);
        pass.disableCulling();
        pass.executes(() -> TerrainSmokeProbe.draw(chunks, chunkLayerSampler,
                textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView()));
    }
}
