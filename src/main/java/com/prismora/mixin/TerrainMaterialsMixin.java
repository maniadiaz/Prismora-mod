package com.prismora.mixin;

import com.prismora.client.TerrainSmokeProbe;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SectionCompiler.class, remap = false)
abstract class TerrainMaterialsMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;JLnet/minecraftforge/client/model/data/ModelData;)V"))
    private void prismora$observeBlockMaterial(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
                                               BlockAndTintGetter world, BlockPos position, BlockState state, BlockStateModel model,
                                               long seed, ModelData modelData) {
        var material = TerrainSmokeProbe.materialId(state);
        if (material < 0) {
            renderer.tesselateBlock(output, x, y, z, world, position, state, model, seed, modelData);
            return;
        }
        renderer.tesselateBlock((qx, qy, qz, quad, instance) -> {
            output.put(qx, qy, qz, quad, instance);
            TerrainSmokeProbe.recordMaterial(material, quad);
        }, x, y, z, world, position, state, model, seed, modelData);
    }
}
