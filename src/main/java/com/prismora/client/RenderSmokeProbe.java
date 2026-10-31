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
 private static int ticks,worldTicks,state;
 private static boolean sampled;
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
  if(state==1 && ++worldTicks>80) {
   var controller=ShaderController.INSTANCE;controller.refresh();
   var entry=controller.entries().stream().filter(e->e.id().equals("Prismora-Diagnostic")).findFirst().orElseThrow();
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
   mc.stop();state=6;
  }
 }
}
