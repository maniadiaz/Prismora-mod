package com.prismora.mixin;

import com.prismora.client.ComplementaryPresentProbe;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
abstract class ComplementaryPresentMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void prismora$presentProbe(CallbackInfo callback) {
        if (Boolean.getBoolean("prismora.terrainSmoke")) {
            ComplementaryPresentProbe.draw(((GameRenderer) (Object) this).mainRenderTarget());
        }
    }
}
