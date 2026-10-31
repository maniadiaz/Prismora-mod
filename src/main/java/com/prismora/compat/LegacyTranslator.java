package com.prismora.compat;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Explicit legacy postprocess subset. Unknown contracts are rejected rather than given fake uniforms. */
public final class LegacyTranslator {
 public record Program(String vertex,String fragment,Set<String> samplers,boolean dynamic,Map<Integer,String> sourceFiles) {}
 private record Edit(int start,int end,String replacement) {}
 private static final Pattern VARYING=Pattern.compile("\\b(varying|in|out)\\s+(?:(flat|smooth)\\s+)?(float|int|vec[234]|ivec[234]|mat[234])\\s+(\\w+)\\s*(\\[\\s*\\d+\\s*])?\\s*;");
 private static final Pattern UNIFORM=Pattern.compile("\\buniform\\s+(sampler2D|sampler2DShadow|float|int|bool|vec[234]|ivec[234]|mat[234])\\s+([^;{}]+)\\s*;");
 private static final Map<String,String> VALUES=Map.ofEntries(
  Map.entry("gbufferProjection","prismoraMatrix(16)"),Map.entry("gbufferProjectionInverse","prismoraMatrix(32)"),Map.entry("gbufferModelView","prismoraMatrix(48)"),Map.entry("gbufferModelViewInverse","prismoraMatrix(64)"),
  Map.entry("viewWidth","prismoraResolution().x"),Map.entry("viewHeight","prismoraResolution().y"),Map.entry("aspectRatio","(prismoraResolution().x/prismoraResolution().y)"),Map.entry("worldTime","int(prismoraDayTicks())"),Map.entry("frameTimeCounter","mod(prismoraTime(),3600.0)"),Map.entry("cameraPosition","prismoraCamera()"),Map.entry("sunAngle","fract(prismoraSunAngle()/6.28318530718)"),Map.entry("rainStrength","(1.0-prismoraValue(12))"));
 public Program translate(ShaderPreprocessor.Result vertex,ShaderPreprocessor.Result fragment)throws IOException{
  String vs=vertex.source(),fs=fragment.source();var locations=new LinkedHashMap<String,Integer>();int slot=0;
  for(String source:List.of(vs,fs)){var matcher=VARYING.matcher(mask(source));while(matcher.find()){String direction=matcher.group(1);boolean interstage=direction.equals("varying")||(source==vs&&direction.equals("out"))||(source==fs&&direction.equals("in"));if(interstage&&!locations.containsKey(matcher.group(4))){locations.put(matcher.group(4),slot);int size=matcher.group(3).startsWith("mat")?Integer.parseInt(matcher.group(3).substring(3)):1;if(matcher.group(5)!=null)size*=Integer.parseInt(matcher.group(5).replaceAll("[^0-9]",""));slot+=size;}}}
  var samplers=new LinkedHashSet<String>();boolean[] dynamic={false};String outVs=stage(vs,true,locations,samplers,dynamic),outFs=stage(fs,false,locations,samplers,dynamic);
  var files=new LinkedHashMap<Integer,String>(vertex.sourceFiles());files.putAll(fragment.sourceFiles());return new Program(outVs,outFs,Collections.unmodifiableSet(samplers),dynamic[0],Collections.unmodifiableMap(files));
 }
 private String stage(String source,boolean vertex,Map<String,Integer> locations,Set<String> samplers,boolean[] dynamic)throws IOException{
  String masked=mask(source);var edits=new ArrayList<Edit>();var macros=new StringBuilder();var uniforms=UNIFORM.matcher(masked);
  while(uniforms.find()){
   String type=uniforms.group(1);
   for(String declaration:uniforms.group(2).split(",",-1)){
   String name=declaration.trim(),definition;
   if(!name.matches("[A-Za-z_]\\w*"))throw new IOException("Unsupported uniform array/initializer: "+name);
   if(type.startsWith("sampler")){if(!type.equals("sampler2D"))throw new IOException("Shadow comparison sampler requires shadow bridge: "+name);samplers.add(name);definition="uniform sampler2D "+name+"Sampler;\n#define "+name+" "+name+"Sampler\n";}
   else{String value=VALUES.get(name);if(value==null)throw new IOException("Unsupported legacy uniform: "+type+" "+name);dynamic[0]=true;definition="#define "+name+" ("+value+")\n";}
   macros.append(definition);
   }
   edits.add(new Edit(uniforms.start(),uniforms.end()," "+"\n".repeat((int)source.substring(uniforms.start(),uniforms.end()).chars().filter(c->c=='\n').count())));
  }
  var varying=VARYING.matcher(masked);
  while(varying.find()){
   String direction=varying.group(1),name=varying.group(4);boolean interstage=direction.equals("varying")||(vertex&&direction.equals("out"))||(!vertex&&direction.equals("in"));if(!interstage)continue;
   int start=varying.start();String prefix=source.substring(Math.max(0,source.lastIndexOf('\n',start)+1),start);
   if(prefix.contains("layout"))continue;
   edits.add(new Edit(start,start+direction.length(),"layout(location="+locations.get(name)+") "+(vertex?"out":"in")));
  }
  source=apply(source,edits);masked=mask(source);
  if(Pattern.compile("\\buniform\\s+").matcher(masked).find())throw new IOException("Unsupported legacy uniform declaration");
  if(Pattern.compile("\\battribute\\b").matcher(masked).find())throw new IOException("Vertex attributes require geometry bridge");
  source=source.replaceAll("(?m)^\\s*#extension[^\\n]*$","");
  if(vertex){
   source=replaceCode(source,"\\bftransform\\s*\\(\\s*\\)","vec4(prismoraQuadUv()*2.0-1.0,0.0,1.0)");
   source=replaceCode(source,"\\bgl_Vertex\\b","vec4(prismoraQuadUv()*2.0-1.0,0.0,1.0)");
   source=replaceCode(source,"\\bgl_MultiTexCoord0\\b","vec4(prismoraQuadUv(),0.0,1.0)");
   source=replaceCode(source,"\\bgl_(?:ModelViewProjectionMatrix|ModelViewMatrix|ProjectionMatrix)\\b","mat4(1.0)");
   source=replaceCode(source,"\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]","mat4(1.0)");
  }
  String depthHelpers="";
  if(samplers.contains("depthtex0")){
   if(vertex)throw new IOException("Vertex depth sampling requires an additional geometry contract");
   // Convert at the sampling site, retaining the original floating-point depth attachment.
   // A color intermediate would quantize most far geometry to 1.0 in RGBA8.
   source=replaceCode(source,"\\b(?:texture2D|texture)\\s*\\(\\s*depthtex0\\s*,","prismoraDepth(depthtex0,");
   source=replaceCode(source,"\\b(?:texture2DLod|textureLod)\\s*\\(\\s*depthtex0\\s*,","prismoraDepthLod(depthtex0,");
   source=replaceCode(source,"\\btexelFetch\\s*\\(\\s*depthtex0\\s*,","prismoraDepthFetch(depthtex0,");
   String remaining=mask(source).replaceAll("prismoraDepth(?:Lod|Fetch)?\\s*\\(\\s*depthtex0\\s*,","");
   if(Pattern.compile("\\bdepthtex0\\b").matcher(remaining).find())throw new IOException("Unsupported indirect/offset depthtex0 sampling contract");
   depthHelpers="vec4 prismoraDepth(sampler2D s,vec2 uv){return vec4(vec3(1.0-texture(s,uv).r),1.0);}\nvec4 prismoraDepth(sampler2D s,vec2 uv,float bias){return vec4(vec3(1.0-texture(s,uv,bias).r),1.0);}\nvec4 prismoraDepthLod(sampler2D s,vec2 uv,float lod){return vec4(vec3(1.0-textureLod(s,uv,lod).r),1.0);}\nvec4 prismoraDepthFetch(sampler2D s,ivec2 p,int lod){return vec4(vec3(1.0-texelFetch(s,p,lod).r),1.0);}\n";
  }
  source=replaceCode(source,"\\btexture2D\\b","texture");source=replaceCode(source,"\\btexture2DLod\\b","textureLod");source=replaceCode(source,"\\btexture2DGradARB\\b","textureGrad");
  var outputs=new StringBuilder();
  if(!vertex){
   masked=mask(source);boolean color=Pattern.compile("\\bgl_FragColor\\b").matcher(masked).find(),data=Pattern.compile("\\bgl_FragData\\b").matcher(masked).find();
   if(color&&data)throw new IOException("Mixed gl_FragColor/gl_FragData output contracts");
   if(color){outputs.append("layout(location=0) out vec4 prismoraFragColor;\n");source=replaceCode(source,"\\bgl_FragColor\\b","prismoraFragColor");}
   if(data){outputs.append("layout(location=0) out vec4 prismoraFragData[1];\n");source=replaceCode(source,"\\bgl_FragData\\b","prismoraFragData");}
   // Modern single output without explicit location.
   if(!color&&!data){var out=Pattern.compile("(?m)^(\\s*)out(\\s+vec4\\s+\\w+\\s*;)").matcher(mask(source));var list=new ArrayList<Edit>();int location=0;while(out.find()){if(location>0)throw new IOException("Multiple render targets require MRT bridge");list.add(new Edit(out.start(),out.end(),out.group(1)+"layout(location="+(location++)+") out"+source.substring(out.start(2),out.end(2))));}source=apply(source,list);}
  }
  String quad=vertex?"vec2 prismoraQuadUv(){ return vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2)); }\n":"";
  String include=dynamic[0]?"#include \"prismora:uniforms.glsl\"\n":"";
  return "#version 330\n#extension GL_ARB_separate_shader_objects : require\n"+include+macros+outputs+quad+depthHelpers+source;
 }
 private static String apply(String source,List<Edit> edits){edits.sort(Comparator.comparingInt(Edit::start).reversed());var text=new StringBuilder(source);for(var edit:edits)text.replace(edit.start(),edit.end(),edit.replacement());return text.toString();}
 private static String replaceCode(String source,String regex,String replacement){var matcher=Pattern.compile(regex).matcher(mask(source));var edits=new ArrayList<Edit>();while(matcher.find())edits.add(new Edit(matcher.start(),matcher.end(),replacement));return apply(source,edits);}
 public static String mask(String source){var out=new StringBuilder(source);boolean block=false,line=false;for(int i=0;i<source.length();i++){char c=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;if(line){if(c=='\n')line=false;else out.setCharAt(i,' ');continue;}if(block){if(c=='*'&&next=='/'){out.setCharAt(i,' ');out.setCharAt(++i,' ');block=false;}else if(c!='\n')out.setCharAt(i,' ');continue;}if(c=='/'&&(next=='/'||next=='*')){line=next=='/';block=next=='*';out.setCharAt(i,' ');out.setCharAt(++i,' ');}}return out.toString();}
}
