package com.prismora.compat;

import com.google.gson.*;
import com.prismora.pack.PackLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Shaderpack metadata from the OptiFine contract; no third-party shader code is bundled. */
public record LegacyPack(String id,String name,Map<String,byte[]> files,Map<String,String> properties,
 Map<String,Option> options,Map<String,Screen> screens,Map<String,Map<String,String>> profiles,
 Map<String,Map<String,Program>> dimensions,Map<String,Map<String,String>> languages,Map<String,String> blocks,List<String> notes) {
 public record Option(String name,String defaultValue,List<String> values,boolean toggle,boolean slider,String file,int line,boolean constant) {
  public String validate(String value)throws IOException{if(!values.contains(value))throw new IOException("Invalid value for "+name+": "+value);return value;}
 }
 public record Screen(String name,List<String> entries,int columns) {}
 public record Program(String name,String vertex,String fragment,String geometry,String compute) {}
 private static final Pattern DEFINE=Pattern.compile("^\\s*(//\\s*)?#\\s*define\\s+([A-Za-z_]\\w*)(?:[ \\t]+([^/\\s]+))?[ \\t]*(?://(.*))?$");
 private static final Pattern CONSTANT=Pattern.compile("^\\s*const\\s+(?:int|float|bool)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;]+);\\s*//(.*)$");
 private static final Pattern VALUES=Pattern.compile("\\[([^]\\r\\n]+)]");
 public static boolean isLegacy(PackLoader.ShaderPack pack){return pack!=null && pack.manifest().has("sourceFormat") && pack.manifest().get("sourceFormat").getAsString().equals("optifine");}
 public PackLoader.ShaderPack snapshot(){var json=new JsonObject();json.addProperty("sourceFormat","optifine");json.addProperty("name",name);json.addProperty("programCount",dimensions.values().stream().mapToInt(Map::size).sum());json.addProperty("optionCount",options.size());json.addProperty("requiresGeometry",requiresGeometry());return new PackLoader.ShaderPack(id,name,json,files);}
 public boolean requiresGeometry(){return dimensions.values().stream().flatMap(map->map.keySet().stream()).anyMatch(n->n.startsWith("gbuffers_")||n.startsWith("shadow")||n.startsWith("dh_")||n.startsWith("clrwl_"));}
 public Map<String,Program> programs(String dimension){var result=new TreeMap<String,Program>();String folder=switch(dimension){case "minecraft:the_nether"->"world-1";case "minecraft:the_end"->"world1";default->"world0";};for(var source:ProgramCatalog.dimension(ProgramCatalog.scan(files),folder)){if(source.role().equals("unknown"))continue;var previous=result.getOrDefault(source.name(),new Program(source.name(),null,null,null,null));result.put(source.name(),new Program(source.name(),source.stage().equals("vsh")?source.path():previous.vertex(),source.stage().equals("fsh")?source.path():previous.fragment(),source.stage().equals("gsh")?source.path():previous.geometry(),source.stage().equals("csh")?source.path():previous.compute()));}return Collections.unmodifiableMap(result);}
 public String text(String language,String key,String fallback){var lang=languages.getOrDefault(language.toLowerCase(Locale.ROOT),Map.of());return lang.getOrDefault(key,languages.getOrDefault("en_us",Map.of()).getOrDefault(key,fallback));}
 public Map<String,String> validated(Map<String,String> overrides)throws IOException {
  var result=new LinkedHashMap<String,String>();for(var option:options.values())result.put(option.name(),option.defaultValue());
  for(var entry:overrides.entrySet()){
   if(entry.getKey().startsWith("program.")){if(!entry.getKey().matches("program\\.(?:world-?\\d+/)?[a-z0-9_]+")||!Set.of("true","false").contains(entry.getValue()))throw new IOException("Invalid program switch");result.put(entry.getKey(),entry.getValue());}
   else{var option=options.get(entry.getKey());if(option==null){if(ProgramCatalog.reservedDefines(files).contains(entry.getKey()))continue;throw new IOException("Unknown pack option: "+entry.getKey());}result.put(entry.getKey(),option.validate(entry.getValue()));}
  }return result;
 }
 public static LegacyPack inspect(String id,Map<String,byte[]> files)throws IOException {
  var dimensions=new TreeMap<String,Map<String,Program>>();
  for(var source:ProgramCatalog.scan(files)){
   if(source.role().equals("unknown"))continue;
   String path=source.path(),dimension=source.dimension(),name=source.name(),stage=source.stage();
   var programs=dimensions.computeIfAbsent(dimension,key->new TreeMap<>());var prior=programs.getOrDefault(name,new Program(name,null,null,null,null));
   programs.put(name,new Program(name,stage.equals("vsh")?path:prior.vertex(),stage.equals("fsh")?path:prior.fragment(),stage.equals("gsh")?path:prior.geometry(),stage.equals("csh")?path:prior.compute()));
  }
  if(dimensions.isEmpty())throw new IOException("No shader programs found: expected shaders/final.fsh, composite.fsh or gbuffers_*.fsh");
  var properties=PropertiesReader.parse(source(files,"shaders/shaders.properties"));
  var options=new TreeMap<String,Option>();var notes=new ArrayList<String>();var reserved=ProgramCatalog.reservedDefines(files);
  Set<String> sliders=new HashSet<>(tokens(properties.getOrDefault("sliders","")));
  for(var file:files.entrySet()){
   if(!file.getKey().startsWith("shaders/")||!file.getKey().matches(".*\\.(vsh|fsh|glsl|gsh|csh)$"))continue;
   String[] lines=new String(file.getValue(),StandardCharsets.UTF_8).split("\\R",-1);Set<String> guards=new HashSet<>();
   for(int i=0;i<Math.min(lines.length-1,20);i++){var guard=Pattern.compile("\\s*#\\s*ifndef\\s+(\\w+).*?").matcher(lines[i]);if(guard.matches()&&lines[i+1].matches("\\s*#\\s*define\\s+"+Pattern.quote(guard.group(1))+"\\s*"))guards.add(guard.group(1));}
   for(int i=0;i<lines.length;i++){
    var define=DEFINE.matcher(lines[i]);var constant=CONSTANT.matcher(lines[i]);String optionName,defaultValue,comment;boolean toggle,isConstant=false;
    if(define.matches()){
     optionName=define.group(2);if(guards.contains(optionName)||reserved.contains(optionName))continue;
     toggle=define.group(3)==null;defaultValue=toggle?(define.group(1)==null?"true":"false"):define.group(3);comment=Objects.toString(define.group(4),"");
     if(define.group(1)!=null&&!toggle)continue;
     if(!toggle&&!VALUES.matcher(comment).find())continue;
    }else if(constant.matches()){
     optionName=constant.group(1);defaultValue=constant.group(2).trim();comment=constant.group(3);toggle=defaultValue.equals("true")||defaultValue.equals("false");isConstant=true;
     if(!VALUES.matcher(comment).find()&&!toggle)continue;
    }else continue;
    var values=new ArrayList<String>();var range=VALUES.matcher(comment);
    if(toggle){values.add("false");values.add("true");}
    else if(range.find())values.addAll(tokens(range.group(1)));
    if(!values.contains(defaultValue))values.add(0,defaultValue);
    if(values.size()<2||values.size()>512||optionName.length()>128)continue;
    if(values.stream().anyMatch(v->!v.matches("[A-Za-z0-9_+.\\-]+")))continue;
    var option=new Option(optionName,defaultValue,List.copyOf(values),toggle,sliders.contains(optionName),file.getKey(),i+1,isConstant);
    var previous=options.putIfAbsent(optionName,option);if(previous!=null&&!previous.defaultValue().equals(defaultValue)&&notes.size()<100)notes.add("Different defaults for "+optionName+" at "+file.getKey()+":"+(i+1));
   }
  }
  if(options.size()>2048)throw new IOException("Too many shader options");
  var screens=new LinkedHashMap<String,Screen>();
  for(var property:properties.entrySet())if(property.getKey().equals("screen")||(property.getKey().startsWith("screen.")&&!property.getKey().endsWith(".columns"))){String name=property.getKey().equals("screen")?"":property.getKey().substring(7);int columns=2;try{columns=Integer.parseInt(properties.getOrDefault(property.getKey()+".columns","2"));}catch(NumberFormatException e){throw new IOException("Invalid columns for "+name,e);}if(columns<1||columns>4)throw new IOException("Screen columns must be 1..4");screens.put(name,new Screen(name,tokens(property.getValue()),columns));}
  screens.putIfAbsent("",new Screen("",List.of("<profile>","*"),2));
  if(screens.size()>256)throw new IOException("Too many option screens");
  for(var screen:screens.values())for(String entry:screen.entries())if(entry.startsWith("[")&&entry.endsWith("]")&&!screens.containsKey(entry.substring(1,entry.length()-1)))notes.add("Missing screen "+entry);
  var rawProfiles=new TreeMap<String,String>();properties.forEach((key,value)->{if(key.startsWith("profile."))rawProfiles.put(key.substring(8),value);});
  if(rawProfiles.size()>128)throw new IOException("Too many profiles");var profiles=new LinkedHashMap<String,Map<String,String>>();
  for(String name:rawProfiles.keySet())resolveProfile(name,rawProfiles,profiles,new HashSet<>());
  var languages=new HashMap<String,Map<String,String>>();
  for(var file:files.entrySet())if(file.getKey().matches("shaders/lang/[A-Za-z_]+\\.lang")){String language=file.getKey().substring(13,file.getKey().length()-5).toLowerCase(Locale.ROOT);languages.put(language,PropertiesReader.parse(new String(file.getValue(),StandardCharsets.UTF_8)));}
  var blocks=PropertiesReader.parse(source(files,"shaders/block.properties"));
  var pack=new LegacyPack(id,id.replaceFirst("(?i)\\.zip$",""),files,properties,Collections.unmodifiableMap(options),Collections.unmodifiableMap(screens),Collections.unmodifiableMap(profiles),Collections.unmodifiableMap(dimensions),Collections.unmodifiableMap(languages),blocks,List.copyOf(notes.subList(0,Math.min(notes.size(),200))));
  // Validate known profile values, retain program switches; unsupported options are reported on apply.
  return pack;
 }
 private static Map<String,String> resolveProfile(String name,Map<String,String> raw,Map<String,Map<String,String>> done,Set<String> visiting)throws IOException {
  if(done.containsKey(name))return done.get(name);if(!raw.containsKey(name))throw new IOException("Unknown parent profile "+name);if(!visiting.add(name))throw new IOException("Profile inheritance cycle at "+name);
  var values=new LinkedHashMap<String,String>();
  for(String token:tokens(raw.get(name))){if(token.startsWith("profile.")){values.putAll(resolveProfile(token.substring(8),raw,done,visiting));continue;}boolean negated=token.startsWith("!");String body=negated?token.substring(1):token;int separator=body.indexOf(':');if(separator<0)separator=body.indexOf('=');String key=separator<0?body:body.substring(0,separator),value=separator<0?(negated?"false":"true"):body.substring(separator+1);if(key.isEmpty()||value.isEmpty()||(negated&&separator>=0))throw new IOException("Invalid profile assignment: "+token);values.put(key,value);}
  visiting.remove(name);var result=Collections.unmodifiableMap(values);done.put(name,result);return result;
 }
 private static List<String> tokens(String text){return text.isBlank()?List.of():List.of(text.trim().split("\\s+"));}
 public static String source(Map<String,byte[]> files,String path){var bytes=files.get(path);return bytes==null?"":new String(bytes,StandardCharsets.UTF_8);}
}
