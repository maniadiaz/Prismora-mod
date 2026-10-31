package com.prismora.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Dynamic data via a 16x1 RGBA8 texture, without changing vanilla uniform layouts. */
public final class Uniforms {
 private static final Identifier ID=Identifier.fromNamespaceAndPath("prismora","textures/effect/uniforms.png");
 private static DynamicTexture texture;
 private static final long START=System.nanoTime();
 public static void ensure(){
  if(texture==null){texture=new DynamicTexture("Prismora dynamic uniforms",16,1,true);Minecraft.getInstance().getTextureManager().register(ID,texture);}
 }
 public static void update(){
  ensure();var mc=Minecraft.getInstance();var state=mc.gameRenderer.gameRenderState().levelRenderState;
  var camera=state.cameraRenderState.pos;float sun=state.skyRenderState.sunAngle;
  float[] values={mc.getWindow().getWidth(),mc.getWindow().getHeight(),(System.nanoTime()-START)/1e9f,
   mc.level==null?0:(float)(mc.level.getOverworldClockTime()%24000),sun,(float)camera.x,(float)camera.y,(float)camera.z,
   -(float)Math.sin(sun),(float)Math.cos(sun),0,mc.level==null?0:1,state.skyRenderState.rainBrightness,0,0,0};
  for(int i=0;i<values.length;i++)texture.getPixels().setPixel(i,0,FloatTextureCodec.encode(values[i]));
  texture.upload();
 }
 public static void release(){if(texture!=null){Minecraft.getInstance().getTextureManager().release(ID);texture=null;}}
}
