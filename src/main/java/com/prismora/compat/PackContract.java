package com.prismora.compat;
import java.io.IOException;
import java.util.*;

/** Evaluated configuration shared by the runtime and requirements auditor. */
public final class PackContract {
 private final Map<String,String> values,properties,definitions;private final Map<String,LegacyPack.Program> programs;private final String folder;
 private static final Map<String,String> FALLBACK=Map.ofEntries(
  Map.entry("gbuffers_line","gbuffers_basic"),Map.entry("gbuffers_textured","gbuffers_basic"),Map.entry("gbuffers_textured_lit","gbuffers_textured"),Map.entry("gbuffers_skybasic","gbuffers_basic"),Map.entry("gbuffers_skytextured","gbuffers_textured"),Map.entry("gbuffers_terrain","gbuffers_textured_lit"),Map.entry("gbuffers_terrain_solid","gbuffers_terrain"),Map.entry("gbuffers_terrain_cutout","gbuffers_terrain"),Map.entry("gbuffers_damagedblock","gbuffers_terrain"),Map.entry("gbuffers_block","gbuffers_terrain"),Map.entry("gbuffers_beaconbeam","gbuffers_textured"),Map.entry("gbuffers_item","gbuffers_textured_lit"),Map.entry("gbuffers_entities","gbuffers_textured_lit"),Map.entry("gbuffers_entities_glowing","gbuffers_entities"),Map.entry("gbuffers_armor_glint","gbuffers_textured"),Map.entry("gbuffers_spidereyes","gbuffers_textured"),Map.entry("gbuffers_hand","gbuffers_textured_lit"),Map.entry("gbuffers_weather","gbuffers_textured_lit"),Map.entry("gbuffers_water","gbuffers_terrain"),Map.entry("gbuffers_hand_water","gbuffers_hand"),Map.entry("gbuffers_entities_translucent","gbuffers_entities"),Map.entry("gbuffers_block_translucent","gbuffers_block"),Map.entry("gbuffers_lightning","gbuffers_basic"),Map.entry("shadow_solid","shadow"),Map.entry("shadow_cutout","shadow"));
 public PackContract(LegacyPack pack,Map<String,String> selected,Map<String,String> environment,String dimension)throws IOException{
  values=pack.validated(selected);programs=pack.programs(dimension);folder=switch(dimension){case "minecraft:the_nether"->"world-1";case "minecraft:the_end"->"world1";default->"world0";};
  if(pack.files().containsKey("shaders/shaders.properties")){var processor=new ShaderPreprocessor(pack,values,environment);properties=processor.properties("shaders/shaders.properties");definitions=processor.definitions();}else{properties=Map.of();var merged=new HashMap<>(environment);merged.putAll(values);definitions=Map.copyOf(merged);}
 }
 public Map<String,String> properties(){return properties;}
 public Map<String,LegacyPack.Program> programs(){return programs;}
 public boolean enabled(String name)throws IOException{
  String selected=values.getOrDefault("program."+folder+"/"+name,values.get("program."+name));if(selected!=null)return selected.equals("true");
  String expression=properties.getOrDefault("program."+folder+"/"+name+".enabled",properties.getOrDefault("program."+name+".enabled","true"));return Expression.test(expression,definitions);
 }
 public Optional<LegacyPack.Program> resolve(String requested)throws IOException{var visited=new HashSet<String>();String name=requested;while(name!=null&&visited.add(name)){if(programs.containsKey(name)&&enabled(name))return Optional.of(programs.get(name));name=FALLBACK.get(name);}return Optional.empty();}
}
