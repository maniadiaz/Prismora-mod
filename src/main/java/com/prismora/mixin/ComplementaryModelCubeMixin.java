package com.prismora.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prismora.client.ComplementaryFeatureCapture;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModelPart.Cube.class, remap = false)
abstract class ComplementaryModelCubeMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFFIFFIIFFF)V"))
    private void prismora$captureCube(VertexConsumer output, float x, float y, float z, int color,
                                       float u, float v, int overlay, int light, float nx, float ny, float nz) {
        output.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
        ComplementaryFeatureCapture.modelVertex(x, y, z, color, u, v, light);
    }
}
