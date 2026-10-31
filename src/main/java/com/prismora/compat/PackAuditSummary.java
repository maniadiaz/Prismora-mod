package com.prismora.compat;
import com.google.gson.*;
import java.util.*;

/** Publishable metadata only: excludes shader source, property expressions and full option tables. */
public final class PackAuditSummary {
 private PackAuditSummary(){}
 public static JsonObject summarize(JsonObject audit){
  var result=new JsonObject();result.add("pack",audit.get("pack"));result.add("scope",audit.get("scope"));result.add("unresolvedConfigurationsOrPrograms",audit.get("unresolvedConfigurationsOrPrograms"));var configurations=new JsonArray();result.add("configurations",configurations);
  for(var value:audit.getAsJsonArray("configurations")){
   var input=value.getAsJsonObject();var config=new JsonObject();for(String key:List.of("profile","scenario","analysisOnly","error"))if(input.has(key))config.add(key,input.get(key));configurations.add(config);
   if(input.has("properties"))config.add("propertyKeys",new Gson().toJsonTree(new TreeSet<>(input.getAsJsonObject("properties").keySet())));
   var counts=new TreeMap<String,Integer>();var uniforms=new TreeMap<String,Set<String>>();var attributes=new TreeMap<String,Set<String>>();var formats=new TreeSet<String>();var outputs=new TreeSet<String>();var blocks=new TreeSet<String>();var builtins=new TreeSet<String>();
   if(input.has("programs"))for(var item:input.getAsJsonArray("programs")){var program=item.getAsJsonObject();counts.merge(program.get("status").getAsString(),1,Integer::sum);for(String field:List.of("uniforms","attributes"))if(program.has(field))for(var entry:program.getAsJsonObject(field).entrySet())(field.equals("uniforms")?uniforms:attributes).computeIfAbsent(entry.getKey(),ignored->new TreeSet<>()).add(entry.getValue().getAsString());if(program.has("formats"))for(var entry:program.getAsJsonObject("formats").entrySet())formats.add(entry.getValue().getAsString());for(String field:List.of("outputDirectives","uniformOrStorageBlocks","builtins"))if(program.has(field))for(var symbol:program.getAsJsonArray(field))(field.equals("outputDirectives")?outputs:field.equals("uniformOrStorageBlocks")?blocks:builtins).add(symbol.getAsString());}
   var gson=new Gson();config.add("programStates",gson.toJsonTree(counts));config.add("uniforms",gson.toJsonTree(uniforms));config.add("attributes",gson.toJsonTree(attributes));config.add("formats",gson.toJsonTree(formats));config.add("outputDirectives",gson.toJsonTree(outputs));config.add("uniformOrStorageBlocks",gson.toJsonTree(blocks));config.add("builtins",gson.toJsonTree(builtins));
  }return result;
 }
}
