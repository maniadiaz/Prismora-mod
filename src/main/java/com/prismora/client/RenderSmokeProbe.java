package com.prismora.client;
import com.prismora.Prismora;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.util.ARGB;

/** Real world, ShaderC, GPU readback and disable integration test. */
final class RenderSmokeProbe {
 private static int ticks,worldTicks,state,priorGeneration;
 private static boolean sampled;
 private static com.mojang.blaze3d.systems.TimerQuery timer;
 private static String timerLabel;
 private static final java.util.Map<String,java.util.List<Long>> gpu=new java.util.HashMap<>();
 static void frameStart(){
  if(state>=13)return;
  if(timer==null)timer=new com.mojang.blaze3d.systems.TimerQuery();
  if(timer.getStatus()==com.mojang.blaze3d.systems.TimerQuery.Status.NOT_RECORDING){
   if(timerLabel!=null && timer.get()>0)gpu.computeIfAbsent(timerLabel,k->new java.util.ArrayList<>()).add(timer.get());
   timerLabel=null;
   if(state==1 && worldTicks>30)timerLabel="vanilla";
   if(state==6 && !ShaderController.INSTANCE.busy() && worldTicks>20)timerLabel="bloom";
   if(timerLabel!=null)timer.beginProfile();
  }
 }
 static void frameEnd(){if(timer!=null && timer.getStatus()==com.mojang.blaze3d.systems.TimerQuery.Status.STARTED)timer.endProfile();}
 private static void reportGpu(){
  gpu.forEach((name,samples)->{samples.sort(Long::compare);Prismora.LOGGER.info("PRISMORA_GPU_BENCH: {} median={} ms samples={}",name,samples.get(samples.size()/2)/1e6,samples.size());});
  if(timer!=null){timer.close();timer=null;}
 }
 static void tick() {
  Minecraft mc=Minecraft.getInstance();ticks++;
  if(ticks>6000)throw new IllegalStateException("Render smoke timed out");
  if(state==0 && mc.gui.screen()!=null && ticks>60) {
   state=1;
   String id="PrismoraSmoke";
   if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves/"+id+"/level.dat"))) mc.createWorldOpenFlows().openWorld(id,()->mc.stop());
   else mc.createWorldOpenFlows().createFreshLevel(id,new LevelSettings(id,GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234L,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
  }
  if(mc.level==null || mc.player==null)return;
  if(state==1 && worldTicks==30 && !System.getProperty("prismora.expectedStartup","").isEmpty()){
   if(!ShaderController.INSTANCE.selected().equals(System.getProperty("prismora.expectedStartup")))throw new IllegalStateException("Startup selection not restored");
   if(!mc.gameRenderer.getAppliedPostEffects().contains(net.minecraft.resources.Identifier.withDefaultNamespace("end_of_frame")))throw new IllegalStateException("Restored effect not applied");
   Prismora.LOGGER.info("PRISMORA_RESTORE_PASS: saved selection restored on fresh client startup");
  }
  if(state==1 && ++worldTicks>80) {
   var controller=ShaderController.INSTANCE;controller.refresh();
   var entry=controller.entries().stream().filter(e->e.id().equals(System.getProperty("prismora.smokePack","Prismora-Diagnostic"))).findFirst().orElseThrow();
   controller.select(entry);state=2;worldTicks=0;
  }
  if(state==2 && !ShaderController.INSTANCE.busy() && ++worldTicks>60) {
   if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());
   if(!mc.gameRenderer.getAppliedPostEffects().contains(net.minecraft.resources.Identifier.withDefaultNamespace("end_of_frame")))throw new IllegalStateException("Effect not applied");
   state=3;
   Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
    try(image) {
     int pixel=image.getPixel(image.getWidth()/3,image.getHeight()/3);
     if(Math.abs(ARGB.red(pixel)-51)>5 || Math.abs(ARGB.green(pixel)-204)>5 || Math.abs(ARGB.blue(pixel)-102)>5)throw new IllegalStateException("Diagnostic pixel mismatch: "+Integer.toHexString(pixel));
     try { image.writeToFile(mc.gameDirectory.toPath().resolve("prismora-diagnostic.png")); }catch(java.io.IOException e){throw new RuntimeException(e);}
     Prismora.LOGGER.info("PRISMORA_GPU_PASS: diagnostic pixel {}",Integer.toHexString(pixel));sampled=true;
    }
   });
  }
  if(state==3 && sampled) { ShaderController.INSTANCE.select(null);state=4;worldTicks=0; }
  if(state==4 && !ShaderController.INSTANCE.busy() && ++worldTicks>40) {
   if(mc.getResourcePackRepository().getSelectedIds().contains("prismora/active"))throw new IllegalStateException("Internal pack still selected after disable");
   Prismora.LOGGER.info("PRISMORA_RENDER_SMOKE_PASS: world loaded, shader compiled, pixels verified, shader disabled");
   var controller=ShaderController.INSTANCE;
   var color=controller.entries().stream().filter(e->e.id().equals("Prismora-Color")).findFirst().orElseThrow();
   var original=color.pack();var files=new java.util.TreeMap<>(original.files());files.put("shaders/post.fsh","#version 330\nthis is not valid GLSL".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var invalid=new com.prismora.pack.PackLoader.ShaderPack("invalid", "Invalid",original.manifest(),files);
   controller.select(new com.prismora.pack.ShaderPackManager.Entry("invalid","Invalid",invalid,""));state=5;
  }
  if(state==5 && !ShaderController.INSTANCE.busy()) {
   if(ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException("Expected visible shader error");
   if(mc.getResourcePackRepository().getSelectedIds().contains("prismora/active"))throw new IllegalStateException("Invalid shader remained active");
   Prismora.LOGGER.info("PRISMORA_ERROR_RECOVERY_PASS: invalid GLSL reported, disabled, game still running");
   var bloom=ShaderController.INSTANCE.entries().stream().filter(e->e.id().equals("Prismora-Bloom")).findFirst().orElseThrow();
   ShaderController.INSTANCE.select(bloom);state=6;worldTicks=0;
  }
  if(state==6 && !ShaderController.INSTANCE.busy() && ++worldTicks>80) {
   if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());
   state=7;sampled=false;
   Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
    try(image){
     int first=image.getPixel(image.getWidth()/4,image.getHeight()/4);int last=image.getPixel(image.getWidth()*3/4,image.getHeight()*3/4);
     if(first==last)throw new IllegalStateException("Bloom output unexpectedly uniform");
     try{image.writeToFile(mc.gameDirectory.toPath().resolve("prismora-bloom.png"));}catch(java.io.IOException e){throw new RuntimeException(e);}
     Prismora.LOGGER.info("PRISMORA_BLOOM_PASS: four-pass shader applied, nonuniform world image verified");sampled=true;
    }
   });
  }
  if(state==7 && sampled){
   var option=ShaderController.INSTANCE.options().stream().filter(o->o.id().equals("bloom_strength")).findFirst().orElseThrow();
   ShaderController.INSTANCE.setOption(option,0.15);state=8;
  }
  if(state==8 && !ShaderController.INSTANCE.busy()){
   try{
    var config=new com.prismora.client.config.PrismoraConfig(mc.gameDirectory.toPath().resolve("config/prismora.json"));config.load();
    if(Math.abs(config.options("Prismora-Bloom").get("bloom_strength")-0.15)>0.0001)throw new IllegalStateException("Option did not persist");
   }catch(java.io.IOException e){throw new RuntimeException(e);}
   Prismora.LOGGER.info("PRISMORA_OPTIONS_PASS: bloom strength persisted and recompiled");
   mc.gui.setScreen(new com.prismora.client.gui.ShaderOptionsScreen(new com.prismora.client.gui.ShaderPackScreen(null)));state=9;worldTicks=0;
  }
  if(state==9 && mc.gui.overlay()==null && ++worldTicks>40){
   Screenshot.grab(mc.gameDirectory,"prismora-options.png",mc.gameRenderer.mainRenderTarget(),1,message->{});
   mc.gui.setScreen(null);priorGeneration=ShaderController.INSTANCE.generation();
   try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("shaderpacks/Prismora-Bloom/shaders/composite.fsh"),"\n// Smoke hot reload\n",java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new RuntimeException(e);}
   state=10;
  }
  if(state==10 && ShaderController.INSTANCE.generation()>priorGeneration && !ShaderController.INSTANCE.busy()){
   if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());
   Prismora.LOGGER.info("PRISMORA_HOT_RELOAD_PASS: shader edit detected and recompiled");mc.getWindow().setWindowed(960,540);state=11;worldTicks=0;
  }
  if(state==11 && mc.gui.overlay()==null && ++worldTicks>40){
   if(mc.gameRenderer.mainRenderTarget().width!=960 || mc.gameRenderer.mainRenderTarget().height!=540)throw new IllegalStateException("Render target did not resize");
   if(!mc.gameRenderer.getAppliedPostEffects().contains(net.minecraft.resources.Identifier.withDefaultNamespace("end_of_frame")))throw new IllegalStateException("Effect lost on resize");
   Prismora.LOGGER.info("PRISMORA_RESIZE_PASS: 960x540 render target and active effect verified");
   mc.getWindow().setWindowed(854,480);ShaderController.INSTANCE.select(null);state=12;
  }
  if(state==12 && !ShaderController.INSTANCE.busy()){reportGpu();mc.stop();state=13;}
 }
}
