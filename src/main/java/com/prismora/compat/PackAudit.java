package com.prismora.compat;
import com.google.gson.*;
import com.prismora.pack.PackLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Reproducible requirements audit; does not claim that a program compiles or renders. */
public final class PackAudit {
 private static final Pattern UNIFORM=Pattern.compile("\\buniform\\s+(\\w+)\\s+([^;{}]+);"),ATTRIBUTE=Pattern.compile("\\b(?:attribute|in)\\s+(\\w+)\\s+(\\w+)\\s*(?:\\[[^]]*])?\\s*;"),OUTPUT=Pattern.compile("(?:RENDERTARGETS:\\s*([0-9, ]+)|DRAWBUFFERS:([0-9A-F]+))"),FORMAT=Pattern.compile("\\b(colortex\\d+|gcolor|gdepth|gnormal)Format\\s*=\\s*(\\w+)"),BLOCK=Pattern.compile("\\b(uniform|buffer)\\s+(\\w+)\\s*\\{"),BUILTIN=Pattern.compile("\\b(?:gl_[A-Za-z0-9_]+|imageLoad|imageStore|atomic[A-Za-z0-9_]*|memoryBarrier[A-Za-z0-9_]*|barrier)\\b");
 private PackAudit(){}
 private record Scenario(String name,Map<String,String> environment,Set<String> integrations){}
 private static List<Scenario> scenarios(){
  var base=ShaderPreprocessor.environment();var features=new TreeMap<>(base);
  for(String flag:List.of("IS_IRIS","IRIS_FEATURE_CUSTOM_IMAGES","IRIS_FEATURE_SSBO","IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE","IRIS_FEATURE_FADE_VARIABLE","IRIS_FEATURE_ENTITY_TRANSLUCENT","IRIS_HAS_TRANSLUCENCY_SORTING","IRIS_HAS_CONNECTED_TEXTURES","IRIS_INLINE_GLINT"))features.put(flag,"1");
  features.put("IRIS_VERSION","10800");features.put("MC_GL_VERSION","430");var older=new TreeMap<>(features);older.put("IRIS_VERSION","10700");var dh=new TreeMap<>(features);dh.put("DISTANT_HORIZONS","1");
  return List.of(new Scenario("base",base,Set.of()),new Scenario("requested_features",features,Set.of()),new Scenario("requested_features_older_abi",older,Set.of()),new Scenario("requested_features_distant_horizons",dh,Set.of("distant_horizons")),new Scenario("requested_features_flywheel",features,Set.of("flywheel")));
 }
 public static JsonObject audit(LegacyPack pack)throws Exception{
  var report=new JsonObject();report.addProperty("pack",pack.id());report.addProperty("scope","Evaluated conditional branches; syntactic GLSL requirements before ShaderC macro expansion. No rendering certification.");
  var catalog=ProgramCatalog.scan(pack.files());report.add("programCatalog",new Gson().toJsonTree(catalog));var configurations=new JsonArray();report.add("configurations",configurations);
  var profiles=new LinkedHashMap<String,Map<String,String>>();profiles.put("DEFAULT",Map.of());profiles.putAll(pack.profiles());int failures=0;
  for(String option:List.of("RP_MODE","SHADER_STYLE","WORLD_BLUR","TAA_DEFINE","COLORED_LIGHTING","SHADOW_QUALITY","REFLECTION_RES")){var definition=pack.options().get(option);if(definition!=null)for(String value:definition.values())if(!value.equals(definition.defaultValue()))profiles.put("OPTION:"+option+"="+value,Map.of(option,value));}
  if(pack.options().containsKey("POM")&&pack.options().containsKey("RP_MODE"))for(String value:pack.options().get("RP_MODE").values())profiles.put("OPTION:PBR_POM="+value,Map.of("RP_MODE",value,"POM","true"));
  for(var profile:profiles.entrySet())for(var scenario:scenarios()){
   if(profile.getKey().startsWith("OPTION:")&&!scenario.name().equals("requested_features"))continue;
   System.out.println("Auditing profile "+profile.getKey()+" / "+scenario.name());
   var config=new JsonObject();config.addProperty("profile",profile.getKey());config.addProperty("scenario",scenario.name());config.addProperty("analysisOnly",true);configurations.add(config);
   try{
    var values=pack.validated(profile.getValue());config.add("options",new Gson().toJsonTree(values));
    var env=scenario.environment();config.add("environment",new Gson().toJsonTree(env));
    var properties=pack.files().containsKey("shaders/shaders.properties")?new ShaderPreprocessor(pack,values,env).properties("shaders/shaders.properties"):Map.<String,String>of();config.add("properties",new Gson().toJsonTree(new TreeMap<>(properties)));var programs=new JsonArray();config.add("programs",programs);
    for(String dimension:List.of("world0","world-1","world1")){var contract=new PackContract(pack,values,env,dimension.equals("world-1")?"minecraft:the_nether":dimension.equals("world1")?"minecraft:the_end":"minecraft:overworld");for(var entry:ProgramCatalog.dimension(catalog,dimension)){
     var program=new JsonObject();program.addProperty("dimension",dimension);program.addProperty("path",entry.path());program.addProperty("role",entry.role());program.addProperty("stage",entry.stage());programs.add(program);
     if(!entry.integration().equals("base")&&!scenario.integrations().contains(entry.integration())){program.addProperty("status","external_integration_not_enabled");continue;}
     if(entry.role().equals("unknown")){program.addProperty("status","unknown_program_role");failures++;continue;}
     if(!contract.enabled(entry.baseProgram())){program.addProperty("status","disabled_by_configuration");continue;}
     try{
      var processed=new ShaderPreprocessor(pack,values,env).process(entry.path());String source=processed.source(),code=LegacyTranslator.mask(source);program.addProperty("status","requirements_extracted");program.addProperty("sourceSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))));program.addProperty("expandedLines",processed.lines().size());program.add("sourceFiles",new Gson().toJsonTree(new TreeSet<>(processed.lines().stream().map(ShaderPreprocessor.Line::file).toList())));
      var uniforms=new TreeMap<String,String>();var u=UNIFORM.matcher(code);while(u.find())for(String declaration:u.group(2).split(",")){String name=declaration.trim().replaceFirst("\\s*=.*$","");uniforms.put(name,u.group(1));}program.add("uniforms",new Gson().toJsonTree(uniforms));
      var attributes=new TreeMap<String,String>();if(entry.stage().equals("vsh")){var a=ATTRIBUTE.matcher(code);while(a.find())attributes.put(a.group(2),a.group(1));}program.add("attributes",new Gson().toJsonTree(attributes));
      var formats=new TreeMap<String,String>();var f=FORMAT.matcher(code);while(f.find())formats.put(f.group(1),f.group(2));program.add("formats",new Gson().toJsonTree(formats));
      var outputs=new TreeSet<String>();var o=OUTPUT.matcher(source);while(o.find())outputs.add(o.group(1)!=null?o.group(1).trim():o.group(2));program.add("outputDirectives",new Gson().toJsonTree(outputs));
      var blocks=new TreeSet<String>();var b=BLOCK.matcher(code);while(b.find())blocks.add(b.group(1)+" "+b.group(2));program.add("uniformOrStorageBlocks",new Gson().toJsonTree(blocks));var builtins=new TreeSet<String>();var s=BUILTIN.matcher(code);while(s.find())builtins.add(s.group());program.add("builtins",new Gson().toJsonTree(builtins));
     }catch(Exception e){program.addProperty("status","preprocess_error");program.addProperty("error",e.getMessage());failures++;}
    }
   }}catch(Exception e){config.addProperty("error",e.getMessage());failures++;}
  }
  report.addProperty("unresolvedConfigurationsOrPrograms",failures);return report;
 }
 public static void main(String[] args)throws Exception{
  if(args.length<2||args.length>3)throw new IllegalArgumentException("Usage: auditShaderPack -PshaderPack=<zip> -PauditOutput=<json>");var snapshot=new PackLoader().load(Path.of(args[0]));var pack=LegacyPack.inspect(snapshot.id(),snapshot.files());var report=audit(pack);Path output=Path.of(args[1]);if(output.toAbsolutePath().getParent()!=null)Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));if(args.length==3){Path summary=Path.of(args[2]);Files.createDirectories(summary.toAbsolutePath().getParent());Files.writeString(summary,new GsonBuilder().setPrettyPrinting().create().toJson(PackAuditSummary.summarize(report)));}System.out.println("Audit written to "+output+"; unresolved="+report.get("unresolvedConfigurationsOrPrograms"));if(report.get("unresolvedConfigurationsOrPrograms").getAsInt()!=0)throw new IllegalStateException("Unresolved audit requirements; see "+output);
 }
}
