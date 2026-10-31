package com.prismora.client;
import com.prismora.Prismora;
import com.prismora.performance.PerformanceSettings;
import com.prismora.render.PrismoraRenderer;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Real GPU validation of profiles, actual target sizes, UI and persistence. */
final class PerformanceSmokeProbe {
 private static int state,ticks,wait;
 private static boolean sampled;
 static void tick(){
  var mc=Minecraft.getInstance();var controller=ShaderController.INSTANCE;
  if(++ticks>7000)throw new IllegalStateException("Performance probe timed out");
  if(state==0 && mc.gui.screen()!=null && ticks>60){
   state=1;String id="PrismoraSmoke";
   if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves/"+id+"/level.dat")))mc.createWorldOpenFlows().openWorld(id,()->mc.stop());
   else mc.createWorldOpenFlows().createFreshLevel(id,new LevelSettings(id,GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
  }
  if(mc.level==null||mc.player==null)return;
  if(state==1 && ++wait>100){controller.refresh();controller.performance(PerformanceSettings.preset(PerformanceSettings.Profile.HIGH));state=2;}
  if(state==2 && !controller.busy()){var pack=controller.entries().stream().filter(e->e.id().equals("Prismora-Bloom")).findFirst().orElseThrow();controller.select(pack);state=3;wait=0;}
  if(state==3 && !controller.busy() && mc.gui.overlay()==null && ++wait>50){
   checkError();int width=mc.getWindow().getWidth();var targets=PrismoraRenderer.activeChain().getAsJsonObject("targets");
   if(targets.getAsJsonObject("prismora:bright").get("width").getAsInt()!=(int)Math.ceil(width*0.5))throw new IllegalStateException("High bloom target scale mismatch");
   controller.performance(PerformanceSettings.preset(PerformanceSettings.Profile.LOW));state=4;wait=0;
  }
  if(state==4 && !controller.busy() && mc.gui.overlay()==null && ++wait>50){
   checkError();int width=mc.getWindow().getWidth();var targets=PrismoraRenderer.activeChain().getAsJsonObject("targets");
   if(targets.getAsJsonObject("prismora:bright").get("width").getAsInt()!=(int)Math.ceil(width*0.25))throw new IllegalStateException("Low bloom target scale mismatch");
   Prismora.LOGGER.info("PRISMORA_SCALED_TARGETS_PASS: high and low target dimensions verified");
   state=5;sampled=false;
   Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){int a=image.getPixel(image.getWidth()/4,image.getHeight()/4),b=image.getPixel(image.getWidth()*3/4,image.getHeight()*3/4);if(a==b)throw new IllegalStateException("Scaled bloom output is uniform");sampled=true;}});
  }
  if(state==5 && sampled){controller.performance(PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY));state=6;wait=0;}
  if(state==6 && !controller.busy() && mc.gui.overlay()==null && ++wait>40){
   checkError();if(PrismoraRenderer.activeChain().getAsJsonArray("passes").size()!=0)throw new IllegalStateException("Entry still runs bloom passes");
   try{var saved=new com.prismora.client.config.PrismoraConfig(mc.gameDirectory.toPath().resolve("config/prismora.json"));saved.load();if(saved.performance().profile()!=PerformanceSettings.Profile.ENTRY)throw new IllegalStateException("Preset not persisted");}catch(java.io.IOException e){throw new RuntimeException(e);}
   Prismora.LOGGER.info("PRISMORA_ENTRY_PROFILE_PASS: zero bloom passes and persisted settings");
   mc.gui.setScreen(new com.prismora.client.gui.PerformanceScreen(new TitleScreen()));state=7;wait=0;
  }
  if(state==7 && ++wait>30){Screenshot.grab(mc.gameDirectory,"prismora-performance.png",mc.gameRenderer.mainRenderTarget(),1,message->{});mc.gui.setScreen(null);controller.performance(PerformanceSettings.preset(PerformanceSettings.Profile.MEDIUM));state=8;wait=0;}
  if(state==8 && !controller.busy() && mc.gui.overlay()==null && ++wait>40){controller.select(null);state=9;}
  if(state==9 && !controller.busy()){Prismora.LOGGER.info("PRISMORA_PERFORMANCE_SMOKE_PASS: profiles, scaled output, optional pass pruning and UI");mc.stop();state=10;}
 }
 private static void checkError(){if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());}
}
