package com.prismora.pack;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;

public final class PackOptions {
 public record Option(String id,String label,double min,double max,double step,double defaultValue,String pass,String block,int index) {}
 private PackOptions() {}
 public static List<Option> read(JsonObject manifest) throws IOException {
  List<Option> result=new ArrayList<>();if(!manifest.has("options"))return List.of();
  try {
   var options=manifest.getAsJsonObject("options");if(options.size()>16)throw new IOException("Too many pack options");
   for(var item:options.entrySet()) {
    String id=item.getKey();var o=item.getValue().getAsJsonObject();
    if(!id.matches("[a-z][a-z0-9_]{0,39}"))throw new IOException("Invalid option id");
    double min=o.get("min").getAsDouble(),max=o.get("max").getAsDouble(),step=o.get("step").getAsDouble(),def=o.get("default").getAsDouble();
    if(!Double.isFinite(min)||!Double.isFinite(max)||!Double.isFinite(step)||!Double.isFinite(def)||min>=max||step<=0||step>max-min||def<min||def>max)throw new IOException("Invalid option range");
    String pass=o.get("pass").getAsString(),block=o.get("block").getAsString();int index=o.get("index").getAsInt();if(o.get("index").getAsDouble()!=index || index>64)throw new IOException("Invalid uniform index");
    JsonObject found=null;for(var p:manifest.getAsJsonArray("passes"))if(p.getAsJsonObject().get("name").getAsString().equals(pass))found=p.getAsJsonObject();
    if(found==null||index<0||!found.getAsJsonObject("uniforms").getAsJsonArray(block).get(index).getAsJsonObject().get("type").getAsString().equals("float"))throw new IOException("Option must address an existing float uniform");
    String label=o.has("label")?o.get("label").getAsString():id;if(label.length()>100)throw new IOException("Option label too long");
    result.add(new Option(id,label,min,max,step,def,pass,block,index));
   }
   return List.copyOf(result);
  } catch(RuntimeException e){throw new IOException("Invalid pack option: "+e.getMessage(),e);}
 }
 public static PackLoader.ShaderPack apply(PackLoader.ShaderPack pack,Map<String,Double> overrides) {
  var manifest=pack.manifest().deepCopy();
  try {
   for(var option:read(manifest)) {
    double requested=overrides.getOrDefault(option.id(),option.defaultValue());double value=Double.isFinite(requested)?Math.clamp(requested,option.min(),option.max()):option.defaultValue();
    for(var p:manifest.getAsJsonArray("passes"))if(p.getAsJsonObject().get("name").getAsString().equals(option.pass()))p.getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray(option.block()).get(option.index()).getAsJsonObject().addProperty("value",value);
   }
  }catch(IOException e){throw new IllegalArgumentException(e);}
  return new PackLoader.ShaderPack(pack.id(),pack.name(),manifest,pack.files());
 }
}
