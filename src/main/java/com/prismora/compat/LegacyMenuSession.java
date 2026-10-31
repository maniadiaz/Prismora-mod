package com.prismora.compat;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Shared draft across submenus; only Apply mutates persistent configuration/render state. */
public final class LegacyMenuSession {
 private final LegacyPack pack;private final Map<String,String> draft=new LinkedHashMap<>();
 public LegacyMenuSession(LegacyPack pack,Map<String,String> saved)throws IOException{this.pack=pack;draft.putAll(pack.validated(saved));}
 public LegacyPack pack(){return pack;}public Map<String,String> values(){return Map.copyOf(draft);}
 public String value(String name){return draft.getOrDefault(name,pack.options().get(name).defaultValue());}
 public void set(String name,String value)throws IOException{var option=pack.options().get(name);if(option==null)throw new IOException("Unknown option "+name);draft.put(name,option.validate(value));}
 public void reset(){draft.clear();for(var option:pack.options().values())draft.put(option.name(),option.defaultValue());}
 public void profile(String name)throws IOException{var values=pack.profiles().get(name);if(values==null)throw new IOException("Unknown profile "+name);pack.validated(values);draft.putAll(values);}
 public String matchedProfile(){return pack.profiles().entrySet().stream().filter(p->p.getValue().entrySet().stream().allMatch(v->Objects.equals(draft.getOrDefault(v.getKey(),v.getKey().startsWith("program.")?"true":null),v.getValue()))).max(Comparator.comparingInt(p->p.getValue().size())).map(Map.Entry::getKey).orElse("");}
 public List<String> entries(String screenName){
  var screen=pack.screens().get(screenName);if(screen==null)return List.of();var result=new ArrayList<String>();
  Set<String> configured=new HashSet<>();for(var menu:pack.screens().values())for(String token:menu.entries())if(pack.options().containsKey(token))configured.add(token);
  for(String token:screen.entries())if(token.equals("*")){for(String option:pack.options().keySet())if(!configured.contains(option)&&!result.contains(option))result.add(option);}else result.add(token);
  return List.copyOf(result);
 }
 public Path presetPath(Path directory){
  try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pack.id().getBytes(StandardCharsets.UTF_8))).substring(0,20);return directory.resolve("shader-"+hash+".json");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public Path exportPreset(Path directory)throws IOException{
  Files.createDirectories(directory);var json=new JsonObject();json.addProperty("format",1);json.addProperty("pack",pack.id());json.add("values",new Gson().toJsonTree(draft));Path path=presetPath(directory);Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(json));return path;
 }
 public void importPreset(Path directory)throws IOException{
  Path path=presetPath(directory);if(Files.size(path)>256*1024)throw new IOException("Preset exceeds 256 KiB");
  try{var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();if(json.get("format").getAsInt()!=1||!pack.id().equals(json.get("pack").getAsString()))throw new IOException("Preset belongs to another pack or format");var values=new LinkedHashMap<String,String>();for(var value:json.getAsJsonObject("values").entrySet())values.put(value.getKey(),value.getValue().getAsString());var checked=pack.validated(values);draft.clear();draft.putAll(checked);}catch(RuntimeException e){throw new IOException("Invalid preset",e);}
 }
}
