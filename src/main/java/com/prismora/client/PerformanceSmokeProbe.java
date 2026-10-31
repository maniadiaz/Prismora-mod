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
 private static int state,ticks,wait,benchmarkIndex;
 private static boolean sampled;
 static void tick(){
  var mc=Minecraft.getInstance();var controller=ShaderController.INSTANCE;
  if(++ticks>7000)throw new IllegalStateException("Performance probe timed out");
  if(state==0 && mc.gui.screen()!=null && ticks>60){
   state=1;String id=SmokeWorlds.id();
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
   var policy=new com.prismora.performance.AdaptiveController(10,0,1,0);var action=com.prismora.performance.AdaptiveController.Action.NONE;
   for(int i=0;i<10;i++)action=policy.observe(45,40,System.nanoTime(),controller.effectivePerformance());
   if(action!=com.prismora.performance.AdaptiveController.Action.SUSPEND)throw new IllegalStateException("Synthetic pressure policy did not request fallback");
   controller.adapt(action);state=7;wait=0;
  }
  if(state==7 && !controller.busy()){
   if(!controller.suspended() || controller.selected().isEmpty() || mc.getResourcePackRepository().getSelectedIds().contains("prismora/active"))throw new IllegalStateException("Fallback lost selection or left shader active");
   Prismora.LOGGER.info("PRISMORA_ADAPTIVE_FALLBACK_PASS: synthetic policy pressure caused real vanilla fallback; selection preserved");
   mc.gui.setScreen(new com.prismora.client.gui.PerformanceScreen(new TitleScreen()));state=8;wait=0;
  }
  if(state==8 && mc.gui.overlay()==null && ++wait>30){Screenshot.grab(mc.gameDirectory,"prismora-performance.png",mc.gameRenderer.mainRenderTarget(),1,message->{});mc.gui.setScreen(null);controller.reload();state=9;wait=0;}
  if(state==9 && !controller.busy() && mc.gui.overlay()==null && ++wait>40){
   if(controller.suspended())throw new IllegalStateException("Manual reload did not resume shader");
   controller.select(controller.entries().stream().filter(e->e.id().equals("Prismora-Color")).findFirst().orElseThrow());state=10;wait=0;
  }
  if(state==10 && !controller.busy() && mc.gui.overlay()==null && ++wait>40){checkError();controller.applyOptions(java.util.Map.of("warm_tint",0.0,"exposure",1.1));state=11;wait=0;}
  if(state==11 && !controller.busy() && mc.gui.overlay()==null && ++wait>40){
   checkError();var uniforms=PrismoraRenderer.activeChain().getAsJsonArray("passes").get(0).getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray("ColorOptions");
   if(uniforms.get(0).getAsJsonObject().get("value").getAsDouble()!=0 || Math.abs(uniforms.get(1).getAsJsonObject().get("value").getAsDouble()-1.1)>1e-5)throw new IllegalStateException("Option widgets did not affect compiled uniforms");
   Prismora.LOGGER.info("PRISMORA_OPTION_WIDGETS_PASS: toggle and choice applied to compiled GPU pipeline");
   mc.gui.setScreen(new com.prismora.client.gui.ShaderOptionsScreen(new TitleScreen()));state=12;wait=0;
  }
  if(state==12 && ++wait>30){Screenshot.grab(mc.gameDirectory,"prismora-option-widgets.png",mc.gameRenderer.mainRenderTarget(),1,message->{});mc.gui.setScreen(null);mc.getWindow().setWindowed(1280,720);state=13;wait=0;}
  if(state==13 && !controller.busy() && mc.gui.overlay()==null && PrismoraRenderer.compiledWidth()==1280 && ++wait>60){benchmarkIndex=0;controller.select(null);state=20;wait=0;}
  if(state==20 && !controller.busy() && mc.gui.overlay()==null && ++wait>60){
   checkError();String name=benchmarkIndex==0?"vanilla":PerformanceSettings.Profile.values()[benchmarkIndex-1].name();PerformanceBenchmark.begin(name);state=21;
  }
  if(state==21 && PerformanceBenchmark.ready()){
   PerformanceBenchmark.finish();benchmarkIndex++;
   if(benchmarkIndex==5){PerformanceBenchmark.save();Prismora.LOGGER.info("PRISMORA_PROFILE_BENCHMARK_PASS: vanilla and all four profiles measured at 1280x720");controller.performance(PerformanceSettings.preset(PerformanceSettings.Profile.MEDIUM));state=30;}
   else{var preset=PerformanceSettings.preset(PerformanceSettings.Profile.values()[benchmarkIndex-1]);controller.performance(new PerformanceSettings(preset.profile(),preset.scale(),preset.bloom(),false,preset.targetFps(),preset.fallback()));state=22;}
  }
  if(state==22 && !controller.busy()){
   if(!controller.selected().equals("Prismora-Bloom"))controller.select(controller.entries().stream().filter(e->e.id().equals("Prismora-Bloom")).findFirst().orElseThrow());
   state=20;wait=0;
  }
  if(state==30 && !controller.busy()){controller.select(null);state=31;}
  if(state==31 && !controller.busy()){mc.getWindow().setWindowed(854,480);Prismora.LOGGER.info("PRISMORA_PERFORMANCE_SMOKE_PASS: profiles, scaled output, pruning, adaptive fallback, controls and benchmark");mc.stop();state=32;}

 }
 private static void checkError(){if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());}
}
