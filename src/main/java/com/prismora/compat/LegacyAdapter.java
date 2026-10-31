package com.prismora.compat;
import com.google.gson.*;
import com.prismora.pack.PackLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Converts a verified color-only composite/final contract into the existing Mojang post bridge. */
public final class LegacyAdapter {
 private LegacyAdapter() {}
 public static PackLoader.ShaderPack prepare(PackLoader.ShaderPack snapshot,Map<String,String> values,String dimension)throws IOException{
  var pack=LegacyPack.inspect(snapshot.id(),snapshot.files());
  if(pack.requiresGeometry())throw new IOException("This shaderpack requires gbuffers/shadow rendering. Options are available; geometry bridge is required before activation.");
  var contract=new PackContract(pack,values,ShaderPreprocessor.environment(),dimension);var programs=contract.programs();var names=new ArrayList<String>();
  String folder=switch(dimension){case "minecraft:the_nether"->"world-1";case "minecraft:the_end"->"world1";default->"world0";};
  for(var source:ProgramCatalog.dimension(ProgramCatalog.scan(pack.files()),folder))if(source.stage().equals("csh")&&source.baseProgram().matches("composite\\d*|final")&&contract.enabled(source.baseProgram()))throw new IOException(source.path()+": active compute stage requires the compute bridge");
  for(String name:programs.keySet())if(name.matches("composite\\d*|final"))names.add(name);
  names.sort(Comparator.comparingInt(LegacyAdapter::order));if(!names.contains("final"))throw new IOException("Legacy postprocess pack needs final.fsh");
  var files=new TreeMap<>(snapshot.files());var manifest=new JsonObject();manifest.addProperty("formatVersion",1);manifest.addProperty("name",pack.name());var passes=new JsonArray();var sourceFiles=new JsonObject();String current="sceneColor";boolean dynamic=false;int count=0;
  String defaultVertex="shaders/prismora_default.vsh";
  if(names.stream().anyMatch(n->programs.get(n).vertex()==null)){
   var augmented=new TreeMap<>(pack.files());augmented.put(defaultVertex,"#version 120\nvarying vec2 texcoord;\nvoid main(){gl_Position=ftransform();texcoord=gl_MultiTexCoord0.xy;}\n".getBytes(StandardCharsets.UTF_8));pack=LegacyPack.inspect(pack.id(),augmented);
  }
  for(String name:names){
   if(!contract.enabled(name)){if(name.equals("final"))throw new IOException("Final shader is disabled; no final output is available");continue;}
   var program=programs.get(name);if(program.geometry()!=null||program.compute()!=null)throw new IOException(name+": geometry/compute shader stage needs additional bridge");
   if(program.fragment()==null)throw new IOException(name+": missing fragment shader");
   String vertex=program.vertex();var sourcePack=pack;
   if(vertex==null)vertex=defaultVertex;
   var vs=new ShaderPreprocessor(sourcePack,values,ShaderPreprocessor.environment()).process(vertex);var fs=new ShaderPreprocessor(sourcePack,values,ShaderPreprocessor.environment()).process(program.fragment());
   String original=fs.source();var draw=Pattern.compile("(?:DRAWBUFFERS:([0-9A-F]+)|RENDERTARGETS:\\s*([0-9, ]+))").matcher(original);
   if(draw.find()){String output=draw.group(1)!=null?draw.group(1):draw.group(2).replace(" ","");if(!output.equals("0"))throw new IOException(name+": multiple/nonzero color attachments require MRT bridge ("+output+")");}
   var translated=new LegacyTranslator().translate(vs,fs);dynamic|=translated.dynamic();translated.sourceFiles().forEach((id,path)->sourceFiles.addProperty(String.valueOf(id),path));
   String stem="shaders/prismora_legacy/p"+count;files.put(stem+".vsh",translated.vertex().getBytes(StandardCharsets.UTF_8));files.put(stem+".fsh",translated.fragment().getBytes(StandardCharsets.UTF_8));
   var pass=new JsonObject();pass.addProperty("name",name);pass.addProperty("vertex",stem+".vsh");pass.addProperty("fragment",stem+".fsh");var inputs=new JsonObject();
   for(String sampler:translated.samplers()){
    if(Set.of("colortex0","gcolor","texture","tex").contains(sampler))inputs.addProperty(sampler,current);
    else if(sampler.equals("depthtex0"))inputs.addProperty(sampler,"sceneDepth");
    else throw new IOException(program.fragment()+": unsupported post sampler "+sampler+"; an explicit attachment/depth/texture bridge is required");
   }
   // A pass with no samplers is legal (e.g. a diagnostic constant color).
   pass.add("inputs",inputs);String output=name.equals("final")?"screen":"legacy"+(count++);pass.addProperty("output",output);passes.add(pass);current=output;
  }
  manifest.add("passes",passes);manifest.addProperty("dynamicUniforms",dynamic);manifest.add("legacySourceFiles",sourceFiles);manifest.addProperty("legacyOriginal",snapshot.id());return new PackLoader.ShaderPack(snapshot.id(),snapshot.name(),manifest,Collections.unmodifiableMap(files));
 }
 private static int order(String name){if(name.equals("final"))return Integer.MAX_VALUE;if(name.equals("composite"))return 0;return Integer.parseInt(name.substring(9));}
 public static String mapDiagnostics(String message,JsonObject sourceFiles){
  var matcher=Pattern.compile("(?m)(?<![A-Za-z0-9_])([0-9]+):([0-9]+):").matcher(message);var output=new StringBuffer();
  while(matcher.find()){String file=sourceFiles.has(matcher.group(1))?sourceFiles.get(matcher.group(1)).getAsString():matcher.group(1);matcher.appendReplacement(output,Matcher.quoteReplacement(file+":"+matcher.group(2)+":"));}matcher.appendTail(output);return output.toString();
 }
}
