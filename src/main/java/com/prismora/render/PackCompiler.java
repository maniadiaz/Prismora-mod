package com.prismora.render;

import com.google.gson.*;
import com.prismora.pack.PackLoader;
import com.prismora.performance.PerformanceSettings;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Builds a post chain with optional-pass elimination and bounded scaled targets. */
public final class PackCompiler {
 public Map<String,byte[]> compile(PackLoader.ShaderPack pack) {
  return compile(pack,PerformanceSettings.preset(PerformanceSettings.Profile.HIGH),0,0);
 }
 public Map<String,byte[]> compile(PackLoader.ShaderPack pack,PerformanceSettings settings,int width,int height) {
  Map<String,byte[]> resources=new TreeMap<>();
  pack.files().forEach((path,bytes)->{if(path.matches("shaders/include/[a-z0-9_/]+\\.glsl"))resources.put("assets/prismora/"+path,bytes);});
  JsonObject chain=new JsonObject(),targets=new JsonObject();JsonArray passes=new JsonArray();
  Map<String,String> aliases=new HashMap<>();
  for(var value:pack.manifest().getAsJsonArray("passes")) {
   var source=value.getAsJsonObject();String output=source.get("output").getAsString();
   if(source.has("feature") && source.get("feature").getAsString().equals("bloom") && !settings.bloom()) {
    String bypass=source.get("bypass").getAsString();
    String input=source.getAsJsonObject("inputs").get(bypass).getAsString();
    aliases.put(output,resolve(input,aliases));
    if(output.equals("screen") && !resolve(input,aliases).equals("sceneColor"))passes.add(copy(target(resolve(input,aliases)),"minecraft:main"));
    continue;
   }
   aliases.remove(output);
   var pass=new JsonObject();
   for(String stage:List.of("vertex","fragment")) {
    String file=source.get(stage).getAsString();String path=file.substring("shaders/".length());
    resources.put("assets/prismora/shaders/pack/"+path,pack.files().get(file));
    pass.addProperty(stage+"_shader","prismora:pack/"+path.substring(0,path.lastIndexOf('.')));
   }
   JsonArray inputs=new JsonArray();
   for(var input:source.getAsJsonObject("inputs").entrySet()) {
    String name=resolve(input.getValue().getAsString(),aliases);
    var item=new JsonObject();item.addProperty("sampler_name",input.getKey());item.addProperty("target",target(name));
    item.addProperty(name.equals("sceneDepth")?"use_depth_buffer":"bilinear",true);inputs.add(item);
   }
   if(pack.manifest().has("dynamicUniforms") && pack.manifest().get("dynamicUniforms").getAsBoolean()) {
    var data=new JsonObject();data.addProperty("sampler_name","PrismoraData");data.addProperty("location","prismora:uniforms");data.addProperty("width",16);data.addProperty("height",1);inputs.add(data);
   }
   pass.add("inputs",inputs);
   String outputId=output.equals("screen")?"prismora:__screen":target(output);
   JsonObject descriptor=new JsonObject();
   JsonObject declared=pack.manifest().has("targets")?pack.manifest().getAsJsonObject("targets").getAsJsonObject(output):null;
   double scale=settings.scale()*(source.has("scale")?source.get("scale").getAsDouble():1);
   if(width>0 && height>0) {
    descriptor.addProperty("width",dimension(declared,"width",width,scale));
    descriptor.addProperty("height",dimension(declared,"height",height,scale));
   } else if(declared!=null)for(String key:List.of("width","height"))if(declared.has(key))descriptor.add(key,declared.get(key).deepCopy());
   targets.add(outputId,descriptor);pass.addProperty("output",outputId);
   if(source.has("uniforms"))pass.add("uniforms",source.get("uniforms").deepCopy());
   passes.add(pass);
   if(output.equals("screen"))passes.add(copy(outputId,"minecraft:main"));
  }
  resources.put("assets/prismora/shaders/copy.fsh",("#version 330\n#extension GL_ARB_separate_shader_objects : require\nuniform sampler2D InSampler;\nlayout(location=0) in vec2 texCoord;\nlayout(location=0) out vec4 fragColor;\nvoid main(){ fragColor=texture(InSampler,texCoord); }\n").getBytes(StandardCharsets.UTF_8));
  chain.add("targets",targets);chain.add("passes",passes);
  resources.put("assets/minecraft/post_effect/end_of_frame.json",chain.toString().getBytes(StandardCharsets.UTF_8));
  resources.put("pack.mcmeta","{\"pack\":{\"description\":\"Prismora active shaderpack\",\"min_format\":[97,1],\"max_format\":[97,1]}}".getBytes(StandardCharsets.UTF_8));
  return Collections.unmodifiableMap(resources);
 }
 private static int dimension(JsonObject declared,String key,int window,double scale) {
  int size=declared!=null&&declared.has(key)?declared.get(key).getAsInt():window;
  return Math.clamp((int)Math.ceil(size*scale),1,8192);
 }
 private static JsonObject copy(String input,String output) {
  var pass=new JsonObject();pass.addProperty("vertex_shader","minecraft:core/screenquad");pass.addProperty("fragment_shader","prismora:copy");
  var item=new JsonObject();item.addProperty("sampler_name","In");item.addProperty("target",input);item.addProperty("bilinear",true);
  var inputs=new JsonArray();inputs.add(item);pass.add("inputs",inputs);pass.addProperty("output",output);return pass;
 }
 private static String resolve(String name,Map<String,String> aliases) {
  for(int i=0;i<17;i++){String next=aliases.get(name);if(next==null)return name;name=next;}
  throw new IllegalArgumentException("Cyclic bypass graph");
 }
 private static String target(String name) { return (name.equals("sceneColor")||name.equals("sceneDepth"))?"minecraft:main":"prismora:"+name; }
}
