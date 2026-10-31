package com.prismora.render;
import com.prismora.Prismora;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in feasibility probe; intentionally does not claim to produce a G-buffer. */
@Mod.EventBusSubscriber(modid=Prismora.MOD_ID,value=Dist.CLIENT)
public final class GeometryProbe {
 @SubscribeEvent public static void register(AddFramePassEvent event) {
  if(!Boolean.getBoolean("prismora.geometryProbe"))return;
  event.addPass(Identifier.fromNamespaceAndPath("prismora","geometry_probe"),new FramePassManager.PassDefinition(){
   private ResourceHandle<RenderTarget> target;
   private boolean logged;
   public void extracts(LevelTargetBundle bundle,FramePass pass,LevelRenderState state){bundle.main=pass.readsAndWrites(bundle.main);target=bundle.main;}
   public void executes(LevelRenderState state){
    if(!logged){
     RenderTarget main=target.get();
     Prismora.LOGGER.info("PRISMORA_GEOMETRY_PROBE_PASS: target={}x{}, entities={}, depth={}, camera={}",main.width,main.height,state.entityRenderStates.size(),main.hasDepth(),state.cameraRenderState.pos);
     logged=true;
    }
   }
  });
 }
}
