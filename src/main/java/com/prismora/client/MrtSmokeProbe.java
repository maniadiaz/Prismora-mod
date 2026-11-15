package com.prismora.client;
import com.prismora.Prismora;
import com.prismora.compat.LegacyTranslator;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.render.runtime.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Quantitative offscreen MRT integration test. Opt-in and inactive in normal play. */
public final class MrtSmokeProbe {
 private static final String HEADER="#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
 private static final String VERTEX=HEADER+"void main(){vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}";
 private static final List<GpuFormat> FORMATS=List.of(GpuFormat.RGBA16_FLOAT,GpuFormat.RGBA8_SNORM);
 private static final List<AutoCloseable> resources=new ArrayList<>();private static GpuTargets targets;private static int state,ticks;private static volatile boolean read;
 public static void tick(){
  var mc=Minecraft.getInstance();if(++ticks>2400)throw new IllegalStateException("MRT smoke timed out");if(!mc.isGameLoadFinished()||mc.gui.overlay()!=null||ticks<80)return;
  var device=RenderSystem.getDevice();
  if(state==0){
   targets=new GpuTargets(device,Map.of(0,new GpuTargets.Spec(FORMATS.get(0),1,1,true,0,0,0,0),12,new GpuTargets.Spec(FORMATS.get(1),1,1,false,0,0,0,0)),8,8,1024*1024);
   var vertex=shader("probe.vsh","void main(){gl_Position=ftransform();}");
   var fragment=shader("probe.fsh","/* RENDERTARGETS: 12,0 */"+'\n'+"void main(){gl_FragData[1]=vec4(2.5,0.25,0.5,1.0);gl_FragData[0]=vec4(-0.75,0.5,0.25,1.0);}");
   var translated=translate(vertex,fragment);
   var shaders=translated.shaders();
   var outputs=translated.outputSlots().stream().mapToInt(OptionalInt::orElseThrow).mapToObj(targets::write).toList();
   var write=own(new GpuProgram(device,"mrt_smoke_write",shaders.vertex(),shaders.fragment(),outputs.stream().map(view->view.texture().getFormat()).toList(),shaders.samplers()));
   MrtPass.draw(device,write,outputs,Map.of(),null);targets.flip(List.of(0,12));
   var sampler=own(device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty()));
   var update=own(new GpuProgram(device,"mrt_smoke_update",VERTEX,HEADER+"uniform sampler2D HdrSampler;uniform sampler2D SignedSampler;layout(location=0) out vec4 hdr;layout(location=1) out vec4 signedColor;void main(){hdr=texelFetch(HdrSampler,ivec2(0),0)+vec4(1.0,0.0,0.0,0.0);signedColor=texelFetch(SignedSampler,ivec2(0),0)+vec4(0.25,0.0,0.0,0.0);}",FORMATS,List.of("HdrSampler","SignedSampler")));
   try{MrtPass.draw(device,update,List.of(targets.read(0),targets.write(12)),Map.of("HdrSampler",targets.read(0),"SignedSampler",targets.read(12)),sampler);throw new IllegalStateException("Read/write alias was accepted");}catch(IllegalArgumentException expected){}
   MrtPass.draw(device,update,List.of(targets.write(0),targets.write(12)),Map.of("HdrSampler",targets.read(0),"SignedSampler",targets.read(12)),sampler);targets.flip(List.of(0,12));
   verify(3.5f,-0.5f,"PRISMORA_MRT_HDR_SNORM_PINGPONG_PASS");state=1;
  }else if(state==1&&read){targets.clear(false);verify(3.5f,0f,"PRISMORA_MRT_PERSISTENCE_CLEAR_PASS");state=2;
  }else if(state==2&&read){
   var original=targets.read(0).texture();try{var unexpected=targets.replacement(1024,1024,1);unexpected.close();throw new IllegalStateException("Attachment budget was not enforced");}catch(IllegalArgumentException expected){}if(original.isClosed())throw new IllegalStateException("Failed replacement closed the live targets");
   var replacement=targets.replacement(16,12,1024*1024);targets.close();if(!original.isClosed())throw new IllegalStateException("Replaced target was not released");targets=replacement;verify(0f,0f,"PRISMORA_MRT_RESIZE_TRANSACTION_PASS");state=3;
  }else if(state==3&&read){verifyFormats();state=4;
  }else if(state==4&&read){verifyDepth();state=5;
  }else if(state==5&&read){targets.close();targets.close();for(int i=resources.size()-1;i>=0;i--)try{resources.get(i).close();}catch(Exception e){throw new RuntimeException(e);}resources.clear();Prismora.LOGGER.info("PRISMORA_MRT_SMOKE_PASS: native GPU MRT/HDR/SNORM/flip/persistence/resize/budget/release verified");state=6;mc.stop();}
 }
 private static <T extends AutoCloseable>T own(T value){resources.add(value);return value;}
 private static ShaderPreprocessor.Result shader(String name,String source){
  String path="shaders/"+name;
  return new ShaderPreprocessor.Result(List.of(new ShaderPreprocessor.Line(path,1,source)),Map.of(1,path));
 }
 private static LegacyTranslator.MrtProgram translate(ShaderPreprocessor.Result vertex,ShaderPreprocessor.Result fragment){
  try{return new LegacyTranslator().translateFullscreenMrt(vertex,fragment);}
  catch(java.io.IOException e){throw new IllegalStateException("MRT GLSL translation failed",e);}
 }
 private static void verifyFormats(){
  read=false;var device=RenderSystem.getDevice();var formats=List.of(TargetFormat.values());var specs=new TreeMap<Integer,GpuTargets.Spec>();for(int i=0;i<formats.size();i++)specs.put(i,new GpuTargets.Spec(formats.get(i),1,1,false,0,0,0,0));var set=own(new GpuTargets(device,specs,4,4,1024*1024));
  String[] values={"vec4(2.5,0.25,0.5,0.0)","vec4(0.25)","vec4(-2.0,3.5,0.25,0.0)","vec4(0.2,0.4,0.6,0.0)","vec4(-0.75,0.5,0.25,0.0)","vec4(-2.5,4.0,0.5,0.25)","vec4(0.2,0.4,0.6,0.75)","vec4(-0.75,0.5,0.25,-0.5)"};
  String[] expected={"vec4(2.5,0.25,0.5,1.0)","vec4(0.25,0.0,0.0,1.0)","vec4(-2.0,3.5,0.25,1.0)","vec4(0.2,0.4,0.6,1.0)","vec4(-0.75,0.5,0.25,1.0)",values[5],values[6],values[7]};
  var fragment=new StringBuilder(HEADER);for(int i=0;i<8;i++)fragment.append("layout(location=").append(i).append(") out vec4 o").append(i).append(";\n");fragment.append("void main(){");for(int i=0;i<8;i++)fragment.append("o").append(i).append('=').append(values[i]).append(';');fragment.append('}');
  var writer=own(GpuProgram.withTargets(device,"mrt_all_formats",VERTEX,fragment.toString(),formats,List.of()));MrtPass.draw(device,writer,java.util.stream.IntStream.range(0,8).mapToObj(set::write).toList(),Map.of(),null);set.flip(specs.keySet());
  var inputs=new TreeMap<String,GpuTextureView>();var checker=new StringBuilder(HEADER);for(int i=0;i<8;i++){inputs.put("T"+i,set.read(i));checker.append("uniform sampler2D T").append(i).append(";\n");}checker.append("layout(location=0) out vec4 color;void main(){bool good=true;");for(int i=0;i<8;i++)checker.append("good=good&&all(lessThan(abs(texelFetch(T").append(i).append(",ivec2(0),0)-").append(expected[i]).append("),vec4(0.016)));\n");checker.append("color=good?vec4(0,1,0,1):vec4(1,0,1,1);}");
  var output=own(device.createTexture("Prismora format proof",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC,GpuFormat.RGBA8_UNORM,1,1,1,1));var view=own(device.createTextureView(output));var sampler=own(device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty()));var check=own(new GpuProgram(device,"mrt_format_check",VERTEX,checker.toString(),List.of(GpuFormat.RGBA8_UNORM),inputs.keySet()));MrtPass.draw(device,check,List.of(view),inputs,sampler);readPixel(output,"PRISMORA_MRT_FORMATS_PASS");
 }
 private static void verifyDepth(){
  read=false;
  var device=RenderSystem.getDevice();
  var depth=own(new GpuDepthTargets(device,4,4,1024));
  var scene=depth.view(GpuDepthTargets.Stage.SCENE);
  var colors=own(new GpuTargets(device,Map.of(0,new GpuTargets.Spec(TargetFormat.RGBA8,1,1,false,0,0,0,0)),4,4,1024));
  float[] depths={0.25f,0.5f,0.75f,0.125f};
  for(int i=0;i<depths.length;i++){
   var fragment=HEADER+"layout(location=0) out vec4 color;void main(){gl_FragDepth="+depths[i]+";color=vec4(1);}";
   var pass=own(GpuProgram.withDepth(device,"depth_writer_"+i,VERTEX,fragment,List.of(TargetFormat.RGBA8),List.of(),DepthStencilState.DEFAULT));
   MrtPass.draw(device,pass,List.of(colors.write(0)),Map.of(),null,Optional.of(scene));
   if(i==0)depth.snapshot(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY);
   if(i==1)depth.snapshot(GpuDepthTargets.Stage.BEFORE_HAND);
  }
  var inputs=Map.of("Scene",scene,"BeforeTranslucency",depth.view(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY),"BeforeHand",depth.view(GpuDepthTargets.Stage.BEFORE_HAND));
  var output=own(device.createTexture("Prismora depth proof",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC,GpuFormat.RGBA8_UNORM,1,1,1,1));
  var view=own(device.createTextureView(output));
  var sampler=own(device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty()));
  var fragment=HEADER+"uniform sampler2D Scene;uniform sampler2D BeforeTranslucency;uniform sampler2D BeforeHand;layout(location=0) out vec4 color;void main(){ivec2 p=ivec2(1);vec3 depths=vec3(texelFetch(Scene,p,0).r,texelFetch(BeforeTranslucency,p,0).r,texelFetch(BeforeHand,p,0).r);bool good=all(lessThan(abs(depths-vec3(0.75,0.25,0.5)),vec3(0.0001)));color=good?vec4(0,1,0,1):vec4(1,0,1,1);}";
  var check=own(new GpuProgram(device,"mrt_depth_check",VERTEX,fragment,List.of(GpuFormat.RGBA8_UNORM),inputs.keySet()));
  MrtPass.draw(device,check,List.of(view),inputs,sampler);
  readPixel(output,"PRISMORA_MRT_DEPTH_SNAPSHOTS_PASS");
 }
 private static void readPixel(GpuTexture output,String marker){var device=RenderSystem.getDevice();var buffer=own(device.createBuffer(()->"Prismora MRT pixel",GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,4));var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(output,buffer,0,()->{try(var mapped=buffer.map(true,false)){var bytes=mapped.data();if((bytes.get(0)&255)!=0||(bytes.get(1)&255)!=255||(bytes.get(2)&255)!=0)throw new IllegalStateException("MRT GPU pixel validation failed: "+marker);Prismora.LOGGER.info("{}",marker);read=true;}},0);encoder.submit();}
 private static void verify(float hdr,float signed,String marker){
  read=false;var device=RenderSystem.getDevice();
  var output=own(device.createTexture("Prismora MRT readback",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC,GpuFormat.RGBA8_UNORM,1,1,1,1));var view=own(device.createTextureView(output));
  var sampler=own(device.createSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.NEAREST,FilterMode.NEAREST,1,OptionalDouble.empty()));
  String fragment=HEADER+"uniform sampler2D HdrSampler;uniform sampler2D SignedSampler;layout(location=0) out vec4 color;void main(){float a=texelFetch(HdrSampler,ivec2(0),0).r;float b=texelFetch(SignedSampler,ivec2(0),0).r;bool good=abs(a-"+hdr+")<0.001&&abs(b-("+signed+"))<0.015;color=good?vec4(0.0,1.0,0.0,1.0):vec4(1.0,0.0,1.0,1.0);}";
  var check=own(new GpuProgram(device,"mrt_smoke_check_"+state,VERTEX,fragment,List.of(GpuFormat.RGBA8_UNORM),List.of("HdrSampler","SignedSampler")));MrtPass.draw(device,check,List.of(view),Map.of("HdrSampler",targets.read(0),"SignedSampler",targets.read(12)),sampler);
  readPixel(output,marker);
 }
}
