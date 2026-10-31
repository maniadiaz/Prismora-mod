package com.prismora.compat;
import java.util.*;
import java.util.regex.Pattern;

/** Includes optional integration programs and compute suffixes instead of silently losing files. */
public final class ProgramCatalog {
 public record Source(String path,String dimension,String name,String baseProgram,String stage,String role,String integration){}
 private static final Pattern FILE=Pattern.compile("shaders/(?:((?:world-?\\d+))/)?([A-Za-z0-9_]+)\\.(vsh|fsh|gsh|csh)");
 private ProgramCatalog(){}
 public static List<Source> scan(Map<String,byte[]> files){
  var result=new ArrayList<Source>();
  for(String path:new TreeSet<>(files.keySet())){var m=FILE.matcher(path);if(!m.matches())continue;
   String name=m.group(2),stage=m.group(3),base=stage.equals("csh")?name.replaceFirst("_[a-z]$",""):name;
   String integration=base.startsWith("dh_")?"distant_horizons":base.startsWith("clrwl_")?"flywheel":"base";
   String role=base.startsWith("gbuffers_")?"geometry":base.matches("shadow(?:_solid|_cutout)?")?"shadow":base.matches("shadowcomp\\d*")?"shadow_composite":base.matches("prepare\\d*")?"prepare":base.matches("deferred\\d*")?"deferred":base.matches("composite\\d*")?"composite":base.equals("final")?"final":!integration.equals("base")?"integration":"unknown";
   result.add(new Source(path,m.group(1)==null?"base":m.group(1),name,base,stage,role,integration));
  }return List.copyOf(result);
 }
 public static List<Source> dimension(List<Source> catalog,String folder){var result=new TreeMap<String,Source>();for(String dimension:List.of("base",folder))for(var source:catalog)if(source.dimension().equals(dimension))result.put(source.name()+"."+source.stage(),source);return List.copyOf(result.values());}
 public static Set<String> reservedDefines(Map<String,byte[]> files){
  var result=new TreeSet<>(List.of("VERTEX_SHADER","FRAGMENT_SHADER","GEOMETRY_SHADER","COMPUTE_SHADER","OVERWORLD","NETHER","END"));
  for(var source:scan(files))if(!source.role().equals("unknown")){
   result.add(source.baseProgram().toUpperCase(Locale.ROOT));
   String code=LegacyTranslator.mask(LegacyPack.source(files,source.path()));
   if(code.lines().allMatch(line->line.isBlank()||line.stripLeading().startsWith("#"))){var m=Pattern.compile("(?m)^\\s*#\\s*define\\s+([A-Za-z_]\\w*)\\s*$").matcher(code);while(m.find())result.add(m.group(1));}
  }return Collections.unmodifiableSet(result);
 }
}
