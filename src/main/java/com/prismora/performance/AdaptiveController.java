package com.prismora.performance;
import java.util.*;

/** Bounded sampling and hysteresis. Never treats a CPU/VSync limit alone as GPU pressure. */
public final class AdaptiveController {
 public enum Action { NONE, REDUCE, SUSPEND }
 public record Metrics(double frameP50,double frameP95,double gpuP95,int samples,boolean cpuBound) {
  public double fps(){return frameP50>0?1000/frameP50:0;}
 }
 private final int window,warmup,pressureWindows;private final long cooldown;
 private final List<Double> frames=new ArrayList<>(),gpu=new ArrayList<>();
 private int warming,pressure;private long nextDecision;private Metrics metrics;
 public AdaptiveController(){this(120,90,2,30_000_000_000L);}
 public AdaptiveController(int window,int warmup,int pressureWindows,long cooldown){if(window<2||warmup<0||pressureWindows<1||cooldown<0)throw new IllegalArgumentException();this.window=window;this.warmup=warmup;this.pressureWindows=pressureWindows;this.cooldown=cooldown;warming=warmup;}
 public Metrics metrics(){return metrics;}
 public void reset(long now){frames.clear();gpu.clear();warming=warmup;pressure=0;nextDecision=now+cooldown;}
 public Action observe(double frameMs,double gpuMs,long now,PerformanceSettings settings){
  if(!Double.isFinite(frameMs)||!Double.isFinite(gpuMs)||frameMs<0.1||frameMs>250||gpuMs<=0||gpuMs>250)return Action.NONE;
  if(warming>0){warming--;return Action.NONE;}
  frames.add(frameMs);gpu.add(gpuMs);if(frames.size()<window)return Action.NONE;
  frames.sort(Double::compare);gpu.sort(Double::compare);double p50=at(frames,0.5),p95=at(frames,0.95),g95=at(gpu,0.95),budget=1000.0/settings.targetFps();
  boolean slow=p95>budget*1.15;boolean gpuPressure=g95>budget*0.9 || (slow && g95>budget*0.65);
  metrics=new Metrics(p50,p95,g95,frames.size(),slow&&!gpuPressure);frames.clear();gpu.clear();
  if(!settings.adaptive()||!gpuPressure){pressure=0;return Action.NONE;}
  if(now<nextDecision)return Action.NONE;
  if(++pressure<pressureWindows)return Action.NONE;
  pressure=0;nextDecision=now+cooldown;warming=warmup;
  return !settings.minimum()?Action.REDUCE:settings.fallback()?Action.SUSPEND:Action.NONE;
 }
 private static double at(List<Double> values,double percentile){return values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*percentile)-1));}
}
