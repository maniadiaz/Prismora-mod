package com.prismora.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.prismora.client.TerrainSmokeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderSystem.class, remap = false)
abstract class TerrainPipelineMixin {
    @Inject(method = "getCompiledPipelineNullable", at = @At("HEAD"), cancellable = true)
    private static void prismora$terrainPipeline(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> callback) {
        if (Boolean.getBoolean("prismora.terrainSmoke")) {
            var override = TerrainSmokeProbe.pipeline(pipeline);
            if (override != null) {
                callback.setReturnValue(override);
            }
        }
    }
}
