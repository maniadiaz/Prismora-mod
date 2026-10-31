package com.prismora.mixin;
import com.prismora.render.Uniforms;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Captures the world matrix after hurt/view bob and portal distortion, before upload. */
@Mixin(value=GameRenderer.class,remap=false)
public abstract class WorldProjectionMixin {
 @Redirect(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),require=1)
 private GpuBufferSlice prismora$worldProjection(ProjectionMatrixBuffer buffer,Matrix4f projection){
  Uniforms.captureWorld(projection);
  return buffer.getBuffer(projection);
 }
}
