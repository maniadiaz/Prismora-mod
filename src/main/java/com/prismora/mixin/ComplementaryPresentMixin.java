package com.prismora.mixin;

import com.prismora.client.ComplementaryPresentProbe;
import com.prismora.client.ComplementaryFeatureCapture;
import com.prismora.client.TerrainSmokeProbe;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
abstract class ComplementaryPresentMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void prismora$beginWorld(CallbackInfo callback) {
        if ((Boolean.getBoolean("prismora.terrainSmoke") || TerrainSmokeProbe.runtimeActive())) {
            ComplementaryFeatureCapture.beginWorld();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void prismora$beginHandCapture(CallbackInfo callback) {
        ComplementaryFeatureCapture.beginHand();
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void prismora$endHandCapture(CallbackInfo callback) {
        ComplementaryFeatureCapture.endHand();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void prismora$presentProbe(CallbackInfo callback) {
        if ((Boolean.getBoolean("prismora.terrainSmoke") || TerrainSmokeProbe.runtimeActive())) {
            TerrainSmokeProbe.renderComplementaryFrame();
            ComplementaryPresentProbe.draw(((GameRenderer) (Object) this).mainRenderTarget());
        }
    }
}
