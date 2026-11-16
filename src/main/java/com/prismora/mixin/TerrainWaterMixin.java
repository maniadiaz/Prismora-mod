package com.prismora.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prismora.client.TerrainSmokeProbe;
import net.minecraft.client.renderer.block.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FluidRenderer.class, remap = false)
abstract class TerrainWaterMixin {
    @Inject(method = "vertex", at = @At("HEAD"))
    private void prismora$captureVertex(VertexConsumer builder, float x, float y, float z, int color,
                                        float u, float v, int light, CallbackInfo callback) {
        TerrainSmokeProbe.recordWaterVertex(x, y, z, color, u, v, light);
    }
}
