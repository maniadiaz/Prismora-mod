package com.prismora.render.runtime;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Direct ShaderC pipeline for MRT, independent of the one-output vanilla post chain. */
public final class GpuProgram implements AutoCloseable {
 private final CompiledRenderPipeline compiled;
 public GpuProgram(GpuDevice device,String name,String vertex,String fragment,List<GpuFormat> formats,Collection<String> samplers){
  this(device,name,vertex,fragment,formats.stream().map(format->new ColorTargetState(Optional.empty(),format,ColorTargetState.WRITE_ALL)).toList(),samplers,Optional.empty(),null);
 }
 public static GpuProgram withTargets(GpuDevice device,String name,String vertex,String fragment,List<TargetFormat> formats,Collection<String> samplers){return new GpuProgram(device,name,vertex,fragment,formats.stream().map(TargetFormat::state).toList(),samplers,Optional.empty(),null);}
 public static GpuProgram withDepth(GpuDevice device,String name,String vertex,String fragment,List<TargetFormat> formats,Collection<String> samplers,DepthStencilState depth){
  return new GpuProgram(device,name,vertex,fragment,formats.stream().map(TargetFormat::state).toList(),samplers,Optional.of(depth),null);
 }
 public static GpuProgram withVertices(GpuDevice device,String name,String vertex,String fragment,List<GpuFormat> formats,VertexFormat vertexFormat){
  return new GpuProgram(device,name,vertex,fragment,formats.stream().map(format->new ColorTargetState(Optional.empty(),format,ColorTargetState.WRITE_ALL)).toList(),List.of(),Optional.empty(),vertexFormat);
 }
 private GpuProgram(GpuDevice device,String name,String vertex,String fragment,List<ColorTargetState> descriptions,Collection<String> samplers,Optional<DepthStencilState> depth,VertexFormat vertexFormat){
  var states=List.copyOf(descriptions);
  if(states.isEmpty()||states.size()>Math.min(8,device.getDeviceInfo().limits().maxColorAttachments()))throw new IllegalArgumentException("Unsupported simultaneous color target count");
  var id=Identifier.fromNamespaceAndPath("prismora","runtime/"+name);var builder=RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).withDepthStencilState(depth);
  if(vertexFormat!=null)builder.withVertexBinding(0,vertexFormat);
  for(int i=0;i<states.size();i++)builder.withColorTargetState(i,states.get(i));
  var layout=BindGroupLayout.builder();for(String sampler:new TreeSet<>(samplers))layout.withUniform(sampler,UniformType.COMBINED_IMAGE_SAMPLER);if(!samplers.isEmpty())builder.withBindGroupLayout(layout.build());
  try(var sources=new ShaderSource(){public String getShader(Identifier shader,ShaderType type){return type==ShaderType.VERTEX?vertex:fragment;}public CachedIncludeSource getInclude(Identifier include){return null;}public void close(){}}){compiled=device.compilePipeline(builder.build(),sources,Runnable::run).join().finishCompile();if(compiled==null)throw new IllegalStateException("Pipeline compilation failed for "+name);}
 }
 public CompiledRenderPipeline compiled(){return compiled;}
 @Override public void close(){compiled.close();}
}
