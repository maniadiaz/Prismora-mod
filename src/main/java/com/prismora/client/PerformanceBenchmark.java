package com.prismora.client;
import com.google.gson.*;
import com.mojang.blaze3d.systems.*;
import com.prismora.render.PrismoraRenderer;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;

/** Opt-in benchmark of a stationary world; wall frame intervals include any FPS limiter. */
final class PerformanceBenchmark {
 private static TimerQuery timer;private static boolean recording;private static int warmup,excluded;
 private static long lastFrame;private static String label;
 private static final List<Double> frames=new ArrayList<>(),gpu=new ArrayList<>();
 private static final JsonArray rows=new JsonArray();
 static void begin(String name){label=name;frames.clear();gpu.clear();excluded=0;warmup=90;lastFrame=0;recording=true;}
 static boolean ready(){return frames.size()>=180;}
 static void beforeFrame(){
  if(!recording)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.gui.screen()!=null||mc.gui.overlay()!=null)return;
  long now=System.nanoTime();double dt=lastFrame==0?0:(now-lastFrame)/1e6;lastFrame=now;
  if(timer==null)timer=new TimerQuery();
  if(timer.getStatus()==TimerQuery.Status.NOT_RECORDING){
   if(warmup>0)warmup--;else if(timer.get()>0 && dt>0.1 && dt<250 && frames.size()<180){frames.add(dt);gpu.add(timer.get()/1e6);}
   if(warmup==0 && (dt>=250||dt<=0.1))excluded++;
   timer.beginProfile();
  }
 }
 static void afterFrame(){if(timer!=null&&timer.getStatus()==TimerQuery.Status.STARTED)timer.endProfile();}
 static void finish(){
  recording=false;frames.sort(Double::compare);gpu.sort(Double::compare);var row=new JsonObject();row.addProperty("profile",label);row.addProperty("samples",frames.size());row.addProperty("excludedOutliers",excluded);row.addProperty("warmupQueries",90);
  row.addProperty("frameP50Ms",percentile(frames,0.5));row.addProperty("frameP95Ms",percentile(frames,0.95));row.addProperty("fpsFromMedian",1000/percentile(frames,0.5));row.addProperty("gpuP50Ms",percentile(gpu,0.5));row.addProperty("gpuP95Ms",percentile(gpu,0.95));row.addProperty("meets30FpsP95",percentile(frames,0.95)<=1000.0/30);
  var chain=PrismoraRenderer.activeChain();long pixels=0;
  if(chain.has("targets"))for(var target:chain.getAsJsonObject("targets").entrySet()){var size=target.getValue().getAsJsonObject();pixels+=(long)size.get("width").getAsInt()*size.get("height").getAsInt();}
  row.addProperty("privateTargetPixels",pixels);row.addProperty("nominalTargetBytes",pixels*8);row.addProperty("nativePasses",chain.has("passes")?chain.getAsJsonArray("passes").size():0);rows.add(row);
 }
 private static double percentile(List<Double> sorted,double p){return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*p)-1));}
 static void save(){
  var mc=Minecraft.getInstance();var device=RenderSystem.getDevice().getDeviceInfo();var report=new JsonObject();report.addProperty("modVersion",com.prismora.Prismora.class.getPackage().getImplementationVersion());report.addProperty("gpu",device.name());report.addProperty("backend",device.backendName());report.addProperty("driver",device.driverInfo());report.addProperty("os",System.getProperty("os.name"));report.addProperty("width",mc.getWindow().getWidth());report.addProperty("height",mc.getWindow().getHeight());report.addProperty("scope","Whole GameRenderer GPU time; wall frames include FPS caps. Profiles on one GPU are not tests of other GPU models. No synthetic load used in these measurements. Frame intervals outside 0.1..250 ms are excluded and counted separately.");report.add("profiles",rows);
  try{Files.writeString(mc.gameDirectory.toPath().resolve("prismora-performance-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException e){throw new RuntimeException(e);}
  if(timer!=null){timer.close();timer=null;}
 }
}
