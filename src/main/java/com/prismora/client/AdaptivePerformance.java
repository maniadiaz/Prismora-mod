package com.prismora.client;
import com.prismora.performance.AdaptiveController;
import com.mojang.blaze3d.systems.TimerQuery;
import net.minecraft.client.Minecraft;

/** Samples only active world rendering. No query pool is used while adaptation is disabled. */
public final class AdaptivePerformance {
 private static final AdaptiveController controller=new AdaptiveController();
 private static TimerQuery timer;private static long lastFrame;private static boolean active,closed;
 public static AdaptiveController.Metrics metrics(){return controller.metrics();}
 public static void reset(){controller.reset(System.nanoTime());lastFrame=0;}
 public static void beforeFrame(){
  if(closed)return;
  var mc=Minecraft.getInstance();var shaders=ShaderController.INSTANCE;
  boolean eligible=mc.isWindowActive() && mc.getFramerateLimitTracker().getThrottleReason()==com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason.NONE && mc.level!=null && mc.player!=null && mc.gui.screen()==null && mc.gui.overlay()==null && !shaders.busy() && !shaders.suspended() && !shaders.selected().isEmpty() && shaders.effectivePerformance().adaptive();
  if(!eligible){if(active)reset();active=false;if(!shaders.effectivePerformance().adaptive() && timer!=null){timer.close();timer=null;}return;}
  long now=System.nanoTime();if(!active){reset();active=true;}double interval=lastFrame==0?0:(now-lastFrame)/1e6;lastFrame=now;
  if(timer==null)timer=new TimerQuery();
  if(timer.getStatus()==TimerQuery.Status.NOT_RECORDING){
   if(timer.get()>0){var action=controller.observe(interval,timer.get()/1e6,now,shaders.effectivePerformance());if(action!=AdaptiveController.Action.NONE){shaders.adapt(action);reset();return;}}
   timer.beginProfile();
  }
 }
 public static void afterFrame(){if(!closed && timer!=null && timer.getStatus()==TimerQuery.Status.STARTED)timer.endProfile();}
 public static void close(){closed=true;if(timer!=null){timer.close();timer=null;}}
}
