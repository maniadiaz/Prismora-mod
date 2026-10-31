package com.prismora.client;
import com.prismora.Prismora;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real-client probe; disabled in normal play. */
@Mod.EventBusSubscriber(modid=Prismora.MOD_ID,value=Dist.CLIENT)
public final class SmokeProbe {
 private static int ticks,frames,uiFrames,performanceFrames;
 @SubscribeEvent public static void frame(ScreenEvent.Render.Post event) {
  if(Boolean.getBoolean("prismora.smoke")) frames++;
  if(Boolean.getBoolean("prismora.uiSmoke") && event.getScreen() instanceof com.prismora.client.gui.ShaderPackScreen) {
   uiFrames++;
   if(uiFrames==50 && Minecraft.getInstance().gui.overlay()==null)net.minecraft.client.Screenshot.grab(Minecraft.getInstance().gameDirectory,"prismora-selection.png",Minecraft.getInstance().gameRenderer.mainRenderTarget(),1,message->{});
  }
 }
 @SubscribeEvent public static void performanceFrame(ScreenEvent.Render.Post event){
  if(Boolean.getBoolean("prismora.uiSmoke") && event.getScreen() instanceof com.prismora.client.gui.PerformanceScreen && Minecraft.getInstance().gui.overlay()==null && ++performanceFrames==30)net.minecraft.client.Screenshot.grab(Minecraft.getInstance().gameDirectory,"prismora-performance.png",Minecraft.getInstance().gameRenderer.mainRenderTarget(),1,message->{});
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
  if(Boolean.getBoolean("prismora.legacyRenderSmoke")){LegacyRenderSmokeProbe.tick();return;}
  if(Boolean.getBoolean("prismora.legacyUiSmoke")){LegacyUiSmokeProbe.tick();return;}
  if(Boolean.getBoolean("prismora.performanceSmoke")){PerformanceSmokeProbe.tick();return;}
  if(Boolean.getBoolean("prismora.renderSmoke")) { RenderSmokeProbe.tick(); return; }
  if(!Boolean.getBoolean("prismora.smoke")) return;
  Minecraft mc=Minecraft.getInstance();
  if(mc.gui.screen()==null) return;
  if(++ticks==60) mc.gui.setScreen(new TitleScreen());
  if(Boolean.getBoolean("prismora.uiSmoke") && ticks==80) {
   var video=new net.minecraft.client.gui.screens.options.VideoSettingsScreen(new TitleScreen(),mc,mc.options);mc.gui.setScreen(video);
   var button=video.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("prismora.title").getString())).map(child->(net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow();
   button.onPress(null);
   if(!(mc.gui.screen() instanceof com.prismora.client.gui.ShaderPackScreen))throw new IllegalStateException("Video settings button did not open Prismora");
   if(java.util.Arrays.stream(mc.options.keyMappings).filter(key->key.getName().equals("key.prismora.open")).count()!=1)throw new IllegalStateException("Prismora keybind not registered exactly once");
   Prismora.LOGGER.info("PRISMORA_VIDEO_BUTTON_PASS: video settings button opens Prismora; configurable keybind registered");
  }
  if(Boolean.getBoolean("prismora.uiSmoke") && ticks==105)mc.gui.setScreen(new com.prismora.client.gui.PerformanceScreen(new TitleScreen()));
  if(Boolean.getBoolean("prismora.uiSmoke") && ticks==140) {
   if(performanceFrames<10)throw new IllegalStateException("Performance UI did not render");
   Prismora.LOGGER.info("PRISMORA_PERFORMANCE_UI_PASS: quality controls rendered {} frames",performanceFrames);
   if(uiFrames<10) throw new IllegalStateException("Prismora UI did not render");
   Prismora.LOGGER.info("PRISMORA_UI_PASS: {} rendered shader selection frames",uiFrames);
   mc.gui.setScreen(new TitleScreen());
  }
  if(ticks>=160 && mc.gui.screen() instanceof TitleScreen && frames>=30) {
   Prismora.LOGGER.info("PRISMORA_SMOKE_PASS: title screen, {} ticks, {} rendered frames",ticks,frames);
   mc.stop();
  }
  if(ticks>1200) throw new IllegalStateException("Prismora smoke timed out");
 }
}
