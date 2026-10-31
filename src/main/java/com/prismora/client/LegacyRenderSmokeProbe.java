package com.prismora.client;
import com.prismora.Prismora;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.util.ARGB;
import java.nio.file.*;
import java.util.*;

/** Full legacy include/options/translation -> ShaderC -> GPU/readback and source-error integration. */
public final class LegacyRenderSmokeProbe {
 private static int state,ticks,wait;private static boolean captured;private static byte[] original;
 public static void tick(){
  var mc=Minecraft.getInstance();var controller=ShaderController.INSTANCE;
  if(++ticks>5000)throw new IllegalStateException("Legacy render probe timed out");
  if(state==0&&mc.gui.screen()!=null&&ticks>60){state=1;String id="PrismoraSmoke";if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+id+"/level.dat")))mc.createWorldOpenFlows().openWorld(id,()->mc.stop());else mc.createWorldOpenFlows().createFreshLevel(id,new LevelSettings(id,GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());}
  if(mc.level==null||mc.player==null)return;
  if(state==1&&++wait>80){controller.refresh();controller.select(controller.entries().stream().filter(e->e.id().equals("PostFixture")).findFirst().orElseThrow());state=9;wait=0;}
  if(state==9&&!controller.busy()&&mc.gui.overlay()==null){controller.applyLegacyOptions("PostFixture",Map.of("GAIN","true","EXPOSURE","2.0"));state=2;wait=0;}
  if(state==2&&!controller.busy()&&mc.gui.overlay()==null&&++wait>60){checkError();captured=false;capture(51,204,102,"PRISMORA_LEGACY_GPU_PASS");state=3;}
  if(state==3&&captured){controller.applyLegacyOptions("PostFixture",Map.of("GAIN","false","EXPOSURE","1.0"));state=4;wait=0;}
  if(state==4&&!controller.busy()&&mc.gui.overlay()==null&&++wait>60){checkError();captured=false;capture(26,102,51,"PRISMORA_LEGACY_OPTIONS_PASS");state=5;}
  if(state==5&&captured){mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 100 0");state=10;wait=0;}
  if(state==10&&mc.level.dimension().identifier().toString().equals("minecraft:the_nether")&&!controller.busy()&&mc.gui.overlay()==null&&++wait>100){checkError();captured=false;capture(51,77,204,"PRISMORA_LEGACY_NETHER_PASS");state=11;}
  if(state==11&&captured){mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 0 100 0");state=12;wait=0;}
  if(state==12&&mc.level.dimension().identifier().toString().equals("minecraft:overworld")&&!controller.busy()&&mc.gui.overlay()==null&&++wait>100){checkError();captured=false;capture(26,102,51,"PRISMORA_LEGACY_DIMENSION_PASS");state=13;}
  if(state==13&&captured){
   try{Path file=mc.gameDirectory.toPath().resolve("shaderpacks/PostFixture/shaders/lib/color.glsl");original=Files.readAllBytes(file);Files.writeString(file,"vec3 applyGain(vec3 color){\nreturn DOES_NOT_EXIST;\n}\n");}catch(Exception e){throw new RuntimeException(e);}state=6;wait=0;
  }
  if(state==6&&!controller.busy()&&!controller.error().isEmpty()){
   if(!controller.error().contains("shaders/lib/color.glsl:2"))throw new IllegalStateException("Original include/line not reported: "+controller.error());
   try{Files.write(mc.gameDirectory.toPath().resolve("shaderpacks/PostFixture/shaders/lib/color.glsl"),original);}catch(Exception e){throw new RuntimeException(e);}
   Prismora.LOGGER.info("PRISMORA_LEGACY_SOURCE_ERROR_PASS: original include filename and line reported; fallback active");state=7;wait=0;
  }
  if(state==7&&++wait>20){Prismora.LOGGER.info("PRISMORA_LEGACY_RENDER_SMOKE_PASS");mc.stop();state=8;}
 }
 private static void checkError(){if(!ShaderController.INSTANCE.error().isEmpty())throw new IllegalStateException(ShaderController.INSTANCE.error());}
 private static void capture(int red,int green,int blue,String marker){var mc=Minecraft.getInstance();Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){int pixel=image.getPixel(Math.min(image.getWidth()-1,image.getWidth()/2+20),image.getHeight()/2);if(Math.abs(ARGB.red(pixel)-red)>5||Math.abs(ARGB.green(pixel)-green)>5||Math.abs(ARGB.blue(pixel)-blue)>5)throw new IllegalStateException("Legacy pixel mismatch: "+Integer.toHexString(pixel));Prismora.LOGGER.info("{}: {}",marker,Integer.toHexString(pixel));captured=true;}});}
}
