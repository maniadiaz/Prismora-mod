package com.prismora.mixin;

import com.prismora.client.ComplementaryFeatureCapture;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelFeatureRenderer.class, remap = false)
abstract class ComplementaryModelFeatureMixin {
    @Inject(method = "prepareModel", at = @At("HEAD"))
    private void prismora$beginModel(ModelFeatureRenderer.Submit<?> submit, CallbackInfo callback) {
        ComplementaryFeatureCapture.beginModel(submit.renderType());
    }

    @Inject(method = "prepareModel", at = @At("RETURN"))
    private void prismora$endModel(ModelFeatureRenderer.Submit<?> submit, CallbackInfo callback) {
        ComplementaryFeatureCapture.endModel();
    }
}
