package com.prismora.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Extends RenderPearl's software cache without changing OpenGL device limits. */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlStateManager", remap = false)
abstract class GlTextureCacheMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/stream/IntStream;range(II)Ljava/util/stream/IntStream;"), index = 1)
    private static int prismora$extendTextureStateCache(int original) {
        return Math.max(original, 32);
    }
}
