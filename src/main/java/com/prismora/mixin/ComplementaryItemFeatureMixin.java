package com.prismora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prismora.client.ComplementaryFeatureCapture;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemFeatureRenderer.class, remap = false)
abstract class ComplementaryItemFeatureMixin {
    @Redirect(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void prismora$captureItem(VertexConsumer output, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        output.putBakedQuad(pose, quad, instance);
        ComplementaryFeatureCapture.item(pose, quad, instance);
    }
}
