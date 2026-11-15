package com.prismora.render.runtime;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.textures.*;
import java.util.*;

public final class MrtPass {
 private MrtPass(){}
 public static void draw(GpuDevice device,GpuProgram program,List<GpuTextureView> outputs,Map<String,GpuTextureView> inputs,GpuSampler sampler){
  if(outputs.isEmpty())throw new IllegalArgumentException("A pass requires an output");var textures=Collections.newSetFromMap(new IdentityHashMap<GpuTexture,Boolean>());int width=outputs.getFirst().getWidth(0),height=outputs.getFirst().getHeight(0);
  var descriptor=RenderPassDescriptor.builder(()->"Prismora MRT");for(var output:outputs){if(!textures.add(output.texture()))throw new IllegalArgumentException("Duplicate output texture");if(output.getWidth(0)!=width||output.getHeight(0)!=height)throw new IllegalArgumentException("MRT outputs require matching viewport dimensions");descriptor.withColorAttachment(output);}
  for(var input:inputs.values())if(textures.contains(input.texture()))throw new IllegalArgumentException("A pass cannot sample its own output");
  try(var pass=device.createCommandEncoder().createRenderPass(descriptor.build())){pass.setPipeline(program.compiled());for(var input:inputs.entrySet())pass.setUniform(input.getKey(),input.getValue(),sampler);pass.draw(3,1,0,0);}
 }
}
