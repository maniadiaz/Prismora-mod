package com.prismora.render;

import com.google.gson.*;
import com.prismora.pack.PackLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Converts the versioned pack contract into vanilla post chain resources. */
public final class PackCompiler {
 public Map<String,byte[]> compile(PackLoader.ShaderPack pack) {
  Map<String,byte[]> resources=new TreeMap<>();
  JsonObject chain=new JsonObject(),targets=new JsonObject();JsonArray passes=new JsonArray();
  for(var value:pack.manifest().getAsJsonArray("passes")) {
   var source=value.getAsJsonObject();var pass=new JsonObject();
   for(String stage:List.of("vertex","fragment")) {
    String file=source.get(stage).getAsString();String path=file.substring("shaders/".length());
    String id="prismora:pack/"+path.substring(0,path.lastIndexOf('.'));
    resources.put("assets/prismora/shaders/pack/"+path,pack.files().get(file));
    pass.addProperty(stage+"_shader",id);
   }
   JsonArray inputs=new JsonArray();
   for(var input:source.getAsJsonObject("inputs").entrySet()) {
    var item=new JsonObject();item.addProperty("sampler_name",input.getKey());item.addProperty("target",target(input.getValue().getAsString()));inputs.add(item);
   }
   pass.add("inputs",inputs);
   String output=source.get("output").getAsString();
   // Always render into a private target and blit to main to avoid texture feedback.
   String outputId=output.equals("screen")?"prismora:final":target(output);
   targets.add(outputId,new JsonObject());pass.addProperty("output",outputId);
   if(source.has("uniforms")) pass.add("uniforms",source.get("uniforms").deepCopy());
   passes.add(pass);
   if(output.equals("screen")) {
    var copy=new JsonObject();copy.addProperty("vertex_shader","minecraft:core/screenquad");copy.addProperty("fragment_shader","prismora:copy");
    var item=new JsonObject();item.addProperty("sampler_name","In");item.addProperty("target",outputId);
    var copyInputs=new JsonArray();copyInputs.add(item);copy.add("inputs",copyInputs);copy.addProperty("output","minecraft:main");passes.add(copy);
   }
  }
  resources.put("assets/prismora/shaders/copy.fsh",("#version 330\n#extension GL_ARB_separate_shader_objects : require\nuniform sampler2D InSampler;\nlayout(location=0) in vec2 texCoord;\nlayout(location=0) out vec4 fragColor;\nvoid main(){ fragColor=texture(InSampler,texCoord); }\n").getBytes(StandardCharsets.UTF_8));
  chain.add("targets",targets);chain.add("passes",passes);
  resources.put("assets/minecraft/post_effect/end_of_frame.json",chain.toString().getBytes(StandardCharsets.UTF_8));
  resources.put("pack.mcmeta","{\"pack\":{\"description\":\"Prismora active shaderpack\",\"min_format\":[97,1],\"max_format\":[97,1]}}".getBytes(StandardCharsets.UTF_8));
  return Collections.unmodifiableMap(resources);
 }
 private static String target(String name) { return name.equals("sceneColor")?"minecraft:main":"prismora:"+name; }
}
