package com.prismora.render.runtime;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.*;
import org.joml.Vector4f;
import java.util.*;

/** Owned ping-pong attachments. A failed replacement leaves the previous set untouched. */
public final class GpuTargets implements AutoCloseable {
 public record Spec(GpuFormat format,double scaleX,double scaleY,boolean persistent,float r,float g,float b,float a){
  public Spec{if(!format.hasColorAspect()||!Double.isFinite(scaleX)||!Double.isFinite(scaleY)||scaleX<=0||scaleY<=0||scaleX>4||scaleY>4||!Float.isFinite(r)||!Float.isFinite(g)||!Float.isFinite(b)||!Float.isFinite(a))throw new IllegalArgumentException("Invalid color target specification");}
  public Spec(TargetFormat format,double scaleX,double scaleY,boolean persistent,float r,float g,float b,float a){this(format.physical(),scaleX,scaleY,persistent,r,g,b,format.alphaOne()?1f:a);}
  public int width(int width){return Math.max(1,Math.toIntExact((long)Math.floor(width*scaleX)));}
  public int height(int height){return Math.max(1,Math.toIntExact((long)Math.floor(height*scaleY)));}
 }
 private static final class Pair {GpuTexture a,b;GpuTextureView av,bv;boolean flipped;}
 private final GpuDevice device;private final Map<Integer,Spec> specs;private final Map<Integer,Pair> targets=new TreeMap<>();private final List<AutoCloseable> resources=new ArrayList<>();private boolean closed;private final long bytes;
 public GpuTargets(GpuDevice device,Map<Integer,Spec> specs,int width,int height,long budget){
  this.device=device;this.specs=Map.copyOf(specs);bytes=estimate(specs,width,height);if(bytes>budget)throw new IllegalArgumentException("Attachment budget exceeded: "+bytes+" > "+budget);
  try{for(var entry:new TreeMap<>(specs).entrySet()){
   var spec=entry.getValue();int w=spec.width(width),h=spec.height(height),limit=device.getDeviceInfo().limits().maxTextureSizeForFormat(spec.format());if(w>limit||h>limit)throw new IllegalArgumentException("Attachment exceeds device format size limit");var pair=new Pair();
   int usage=GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_COPY_DST;
   pair.a=own(device.createTexture("Prismora colortex"+entry.getKey()+" A",usage,spec.format(),w,h,1,1));pair.av=own(device.createTextureView(pair.a));pair.b=own(device.createTexture("Prismora colortex"+entry.getKey()+" B",usage,spec.format(),w,h,1,1));pair.bv=own(device.createTextureView(pair.b));targets.put(entry.getKey(),pair);
  }clear(true);}catch(RuntimeException e){try{close();}catch(RuntimeException cleanup){e.addSuppressed(cleanup);}throw e;}
 }
 public static long estimate(Map<Integer,Spec> specs,int width,int height){if(width<1||height<1||specs.isEmpty()||specs.size()>16)throw new IllegalArgumentException("Invalid target set size");long bytes=0;for(var entry:specs.entrySet()){if(entry.getKey()<0||entry.getKey()>15)throw new IllegalArgumentException("Color attachment index must be 0..15");var spec=entry.getValue();bytes=Math.addExact(bytes,Math.multiplyExact(Math.multiplyExact((long)spec.width(width),spec.height(height)),2L*spec.format().blockSize()));}return bytes;}
 private <T extends AutoCloseable>T own(T value){resources.add(value);return value;}
 private Pair pair(int id){if(closed)throw new IllegalStateException("Targets are closed");var pair=targets.get(id);if(pair==null)throw new IllegalArgumentException("Missing colortex"+id);return pair;}
 public GpuTextureView read(int id){var p=pair(id);return p.flipped?p.bv:p.av;}
 public GpuTextureView write(int id){var p=pair(id);return p.flipped?p.av:p.bv;}
 public void flip(Collection<Integer> ids){for(int id:new LinkedHashSet<>(ids))pair(id);for(int id:new LinkedHashSet<>(ids)){var pair=pair(id);pair.flipped=!pair.flipped;}}
 public void clear(boolean includePersistent){if(closed)throw new IllegalStateException("Targets are closed");var encoder=device.createCommandEncoder();for(var entry:targets.entrySet()){var spec=specs.get(entry.getKey());if(includePersistent||!spec.persistent()){var color=new Vector4f(spec.r(),spec.g(),spec.b(),spec.a());encoder.clearColorTexture(entry.getValue().a,color);encoder.clearColorTexture(entry.getValue().b,color);}}}
 public GpuTargets replacement(int width,int height,long budget){if(closed)throw new IllegalStateException("Targets are closed");return new GpuTargets(device,specs,width,height,budget);}
 public long allocatedBytes(){return bytes;}
 @Override public void close(){if(closed)return;closed=true;RuntimeException failure=null;for(int i=resources.size()-1;i>=0;i--)try{resources.get(i).close();}catch(Exception e){if(failure==null)failure=new IllegalStateException("Failed to release attachment",e);else failure.addSuppressed(e);}resources.clear();if(failure!=null)throw failure;}
}
