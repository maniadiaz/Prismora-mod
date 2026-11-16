package com.prismora.mixin;

import com.prismora.client.TerrainSmokeProbe;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SectionCompiler.class, remap = false)
abstract class TerrainMaterialsMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void prismora$beginSection(SectionPos section, RenderSectionRegion region, VertexSorting sorting,
                                      SectionBufferBuilderPack pack, CallbackInfoReturnable<SectionCompiler.Results> callback) {
        TerrainSmokeProbe.beginSection(section);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void prismora$endSection(SectionPos section, RenderSectionRegion region, VertexSorting sorting,
                                    SectionBufferBuilderPack pack, CallbackInfoReturnable<SectionCompiler.Results> callback) {
        TerrainSmokeProbe.endSection();
    }
    @Redirect(method = "compile", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;JLnet/minecraftforge/client/model/data/ModelData;)V"))
    private void prismora$observeBlockMaterial(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
                                               BlockAndTintGetter world, BlockPos position, BlockState state, BlockStateModel model,
                                               long seed, ModelData modelData) {
        if (!TerrainSmokeProbe.capturingSection()) {
            renderer.tesselateBlock(output, x, y, z, world, position, state, model, seed, modelData);
            return;
        }
        int material = TerrainSmokeProbe.materialId(state);
        renderer.tesselateBlock((qx, qy, qz, quad, instance) -> {
            output.put(qx, qy, qz, quad, instance);
            TerrainSmokeProbe.recordMaterial(material, qx, qy, qz, quad, instance);
        }, x, y, z, world, position, state, model, seed, modelData);
    }
}
