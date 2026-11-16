package com.prismora.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prismora.client.ComplementaryFeatureCapture;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuadParticleRenderState.class, remap = false)
abstract class ComplementaryParticleMixin {
    @Inject(method = "buildLayer", at = @At("HEAD"))
    private void prismora$beginLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer, CallbackInfo callback) {
        ComplementaryFeatureCapture.beginParticles(layer.textureAtlasLocation());
    }

    @Inject(method = "buildLayer", at = @At("RETURN"))
    private void prismora$endLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer, CallbackInfo callback) {
        ComplementaryFeatureCapture.endParticles();
    }

    @Inject(method = "renderVertex", at = @At("HEAD"))
    private void prismora$captureVertex(VertexConsumer builder, Quaternionf rotation, float x, float y, float z,
                                        float nx, float ny, float scale, float u, float v, int color, int light,
                                        CallbackInfo callback) {
        ComplementaryFeatureCapture.particleVertex(rotation, x, y, z, nx, ny, scale, u, v, color, light);
    }
}
