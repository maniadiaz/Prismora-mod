package com.prismora.pack;
import com.google.gson.*;
import java.io.IOException;
import java.util.*;

public final class PackOptions {
 public enum Widget { SLIDER, TOGGLE, CHOICE }
 public record Option(String id,String label,double min,double max,double step,double defaultValue,String pass,String block,int index,Widget widget,List<Double> values,List<String> labels) {
  public double normalize(double requested){
   double value=Double.isFinite(requested)?Math.clamp(requested,min,max):defaultValue;
   if(widget==Widget.TOGGLE)return value>=0.5?1:0;
   if(widget==Widget.CHOICE)return values.stream().min(Comparator.comparingDouble(v->Math.abs(v-value))).orElse(defaultValue);
   return Math.clamp(min+Math.round((value-min)/step)*step,min,max);
  }
 }
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
    Widget widget=Widget.valueOf(o.has("widget")?o.get("widget").getAsString().toUpperCase(Locale.ROOT):"SLIDER");
    List<Double> values=new ArrayList<>();List<String> labels=new ArrayList<>();
    if(widget==Widget.TOGGLE && (min!=0||max!=1||step!=1||(def!=0&&def!=1)))throw new IOException("Toggle requires values 0 and 1");
    if(widget==Widget.CHOICE){
     for(var entry:o.getAsJsonArray("values")){double v=entry.getAsDouble();if(!Double.isFinite(v)||v<min||v>max||values.contains(v))throw new IOException("Invalid choice value");values.add(v);}
     if(values.size()<2||values.size()>16||!values.contains(def))throw new IOException("Invalid choice list");
     if(o.has("labels")){for(var entry:o.getAsJsonArray("labels")){String text=entry.getAsString();if(text.length()>100)throw new IOException("Choice label too long");labels.add(text);}if(labels.size()!=values.size())throw new IOException("Choice labels do not match values");}
    }
    result.add(new Option(id,label,min,max,step,def,pass,block,index,widget,List.copyOf(values),List.copyOf(labels)));
   }
   return List.copyOf(result);
  } catch(RuntimeException e){throw new IOException("Invalid pack option: "+e.getMessage(),e);}
 }
 public static PackLoader.ShaderPack apply(PackLoader.ShaderPack pack,Map<String,Double> overrides) {
  var manifest=pack.manifest().deepCopy();
  try {
   for(var option:read(manifest)) {
    double requested=overrides.getOrDefault(option.id(),option.defaultValue());double value=option.normalize(requested);
    for(var p:manifest.getAsJsonArray("passes"))if(p.getAsJsonObject().get("name").getAsString().equals(option.pass()))p.getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray(option.block()).get(option.index()).getAsJsonObject().addProperty("value",value);
   }
  }catch(IOException e){throw new IllegalArgumentException(e);}
  return new PackLoader.ShaderPack(pack.id(),pack.name(),manifest,pack.files());
 }
}
