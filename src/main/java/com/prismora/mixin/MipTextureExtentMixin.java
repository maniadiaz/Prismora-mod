package com.prismora.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A rectangular mip chain still has one texel on its shorter axis at the tail. */
@Mixin(targets = "com.mojang.renderpearl.backend.common.BaseGpuTexture", remap = false)
abstract class MipTextureExtentMixin {
    @Inject(method = {"getWidth", "getHeight"}, at = @At("RETURN"), cancellable = true)
    private void prismora$clampMipExtent(int level, CallbackInfoReturnable<Integer> callback) {
        if (level > 0 && callback.getReturnValueI() == 0) {
            callback.setReturnValue(1);
        }
    }
}
