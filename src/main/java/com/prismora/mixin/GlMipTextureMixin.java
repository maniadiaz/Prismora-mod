package com.prismora.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** OpenGL allocation must keep both dimensions at least one along the mip tail. */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice", remap = false)
abstract class GlMipTextureMixin {
    @ModifyArgs(method = "createTexture(Ljava/lang/String;ILcom/mojang/renderpearl/api/GpuFormat;IIIIZ)Lcom/mojang/renderpearl/api/textures/GpuTexture;", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_texImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"))
    private void prismora$clampMipExtent(Args args) {
        if ((int) args.get(1) > 0) {
            args.set(3, Math.max(1, (int) args.get(3)));
            args.set(4, Math.max(1, (int) args.get(4)));
        }
    }
}
