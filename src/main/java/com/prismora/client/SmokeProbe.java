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
 private static int ticks,frames;
 @SubscribeEvent public static void frame(ScreenEvent.Render.Post event) {
  if(Boolean.getBoolean("prismora.smoke")) frames++;
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
  if(!Boolean.getBoolean("prismora.smoke")) return;
  Minecraft mc=Minecraft.getInstance();
  if(mc.gui.screen()==null) return;
  if(++ticks==60) mc.gui.setScreen(new TitleScreen());
  if(ticks>=160 && mc.gui.screen() instanceof TitleScreen && frames>=30) {
   Prismora.LOGGER.info("PRISMORA_SMOKE_PASS: title screen, {} ticks, {} rendered frames",ticks,frames);
   mc.stop();
  }
  if(ticks>1200) throw new IllegalStateException("Prismora smoke timed out");
 }
}
