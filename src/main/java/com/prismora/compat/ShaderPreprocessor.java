package com.prismora.compat;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Resolves pack includes and active branches before legacy translation. */
public final class ShaderPreprocessor {
 public record Line(String file,int number,String text) {}
 public record Result(List<Line> lines,Map<Integer,String> sourceFiles) {
  public String source(){var ids=new HashMap<String,Integer>();sourceFiles.forEach((id,path)->ids.put(path,id));var out=new StringBuilder();String previous="";int expected=-1;for(var line:lines){int id=ids.getOrDefault(line.file(),0);if(!previous.equals(line.file())||expected!=line.number())out.append("#line ").append(line.number()).append(' ').append(id).append('\n');out.append(line.text()).append('\n');previous=line.file();expected=line.number()+1;}return out.toString();}
 }
 private record Branch(boolean parent,boolean matched,boolean elseSeen) {}
 private final LegacyPack pack;private final Map<String,String> selected,environment,macros=new HashMap<>();
 private final List<Line> output=new ArrayList<>();private final Map<Integer,String> sourceFiles=new LinkedHashMap<>();
 private static final Pattern DIRECTIVE=Pattern.compile("^\\s*#\\s*(\\w+)\\b(.*)$");
 private static final Pattern INCLUDE=Pattern.compile("[\"<]([^\">]+)[\">].*");
 private static final Pattern OPTION_DEFINE=Pattern.compile("^\\s*(?://\\s*)?#\\s*define\\s+([A-Za-z_]\\w*)(?:\\s+.*)?$");
 private static final Pattern DEFINE=Pattern.compile("([A-Za-z_]\\w*)(.*)");
 private static final Pattern FUNCTION=Pattern.compile("^\\(([^)]*)\\)\\s*(.*)$");
 private static final Pattern VERSION=Pattern.compile("^(\\d+).*");
 private static final Pattern CONSTANT=Pattern.compile("^\\s*const\\s+\\w+\\s+(\\w+)\\s*=.*");
 private int characters;
 private boolean propertyMode;
 public ShaderPreprocessor(LegacyPack pack,Map<String,String> selected,Map<String,String> environment)throws IOException{
  this.pack=pack;this.selected=pack.validated(selected);this.environment=Map.copyOf(environment);macros.putAll(environment);int id=1;for(String file:new TreeSet<>(pack.files().keySet()))if(file.startsWith("shaders/"))sourceFiles.put(id++,file);
 }
 public Result process(String path)throws IOException{for(var entry:new TreeMap<>(environment).entrySet())emit(path,1,"#define "+entry.getKey()+" "+entry.getValue());expand(path,new ArrayDeque<>());return new Result(List.copyOf(output),Collections.unmodifiableMap(sourceFiles));}
 public Map<String,String> properties(String path)throws IOException{
  propertyMode=true;
  for(var entry:selected.entrySet())if(entry.getKey().matches("[A-Za-z_]\\w*")){if(entry.getValue().equals("false"))macros.remove(entry.getKey());else macros.put(entry.getKey(),entry.getValue().equals("true")?"":entry.getValue());}
  expand(path,new ArrayDeque<>());var text=new StringBuilder();for(var line:output)text.append(line.text()).append('\n');return PropertiesReader.parse(text.toString());
 }
 public Map<String,String> definitions(){return Map.copyOf(macros);}
 private void emit(String path,int line,String text)throws IOException{characters+=text.length();if(characters>4*1024*1024||output.size()>150000)throw new IOException("Expanded shader exceeds limits");output.add(new Line(path,line,text));}
 private void expand(String path,Deque<String> includes)throws IOException{
  if(includes.size()>64)throw new IOException("Include depth exceeds 64: "+path);
  if(!pack.files().containsKey(path))throw new IOException("Missing include "+path);
  String source=LegacyPack.source(pack.files(),path);
  if(includes.contains(path)){
   var guard=Pattern.compile("(?s)^\\s*(?:/\\*.*?\\*/\\s*|//[^\\n]*\\n\\s*)*#\\s*ifndef\\s+(\\w+)\\s*\\R\\s*#\\s*define\\s+\\1").matcher(source);
   if(guard.find()&&macros.containsKey(guard.group(1)))return;throw new IOException("Include cycle: "+includes+" -> "+path);
  }
  includes.addLast(path);var branches=new ArrayDeque<Branch>();boolean active=true;String[] lines=source.split("\\R",-1);
  for(int i=0;i<lines.length;i++){
   String line=lines[i];int number=i+1;
   while(line.stripTrailing().endsWith("\\")&&i+1<lines.length){line=line.stripTrailing();line=line.substring(0,line.length()-1)+lines[++i];}
   var directive=DIRECTIVE.matcher(line);
   String kind=directive.matches()?directive.group(1):"",body=directive.matches()?directive.group(2).trim():"";
   try{
    switch(kind){
     case "if","ifdef","ifndef"->{if(branches.size()>=128)throw new IOException("Conditional nesting exceeds 128");boolean condition=active&&(kind.equals("ifdef")?macros.containsKey(stripComment(body)):kind.equals("ifndef")?!macros.containsKey(stripComment(body)):Expression.test(stripComment(body),macros));branches.push(new Branch(active,condition,false));active=condition;continue;}
     case "elif"->{if(branches.isEmpty())throw new IOException("Unmatched #elif");var b=branches.pop();if(b.elseSeen())throw new IOException("#elif after #else");boolean condition=b.parent()&&!b.matched()&&Expression.test(stripComment(body),macros);branches.push(new Branch(b.parent(),b.matched()||condition,false));active=condition;continue;}
     case "else"->{if(branches.isEmpty())throw new IOException("Unmatched #else");var b=branches.pop();if(b.elseSeen())throw new IOException("Duplicate #else");active=b.parent()&&!b.matched();branches.push(new Branch(b.parent(),true,true));continue;}
     case "endif"->{if(branches.isEmpty())throw new IOException("Unmatched #endif");active=branches.pop().parent();continue;}
    }
    if(!active)continue;
    if(kind.equals("include")){
     var name=INCLUDE.matcher(body);if(!name.matches())throw new IOException("Malformed include");expand(resolve(path,name.group(1)),includes);continue;
    }
    var optionDefine=OPTION_DEFINE.matcher(line);
    if((!propertyMode||!path.endsWith(".properties"))&&optionDefine.matches()&&pack.options().containsKey(optionDefine.group(1))&&!pack.options().get(optionDefine.group(1)).constant()){
     var option=pack.options().get(optionDefine.group(1));String value=selected.get(option.name());if(option.toggle()&&value.equals("false")){macros.remove(option.name());emit(path,number,"#undef "+option.name());continue;}line="#define "+option.name()+(option.toggle()?"":" "+value);kind="define";body=option.name()+(option.toggle()?"":" "+value);
    }
    if(kind.equals("define")){
     var define=DEFINE.matcher(body);if(!define.matches())throw new IOException("Malformed define");String name=define.group(1),value=stripComment(define.group(2)).trim();
     // Function-like macro bodies stay intact for ShaderC; using one in #if is reported explicitly.
     if(!define.group(2).startsWith("("))macros.put(name,value);
     else{var function=FUNCTION.matcher(define.group(2));if(!function.matches())throw new IOException("Malformed function macro");macros.put(name,"@fn:"+function.group(1).replace(" ","")+":"+stripComment(function.group(2)));}
     emit(path,number,line);continue;
    }
    if(kind.equals("undef")){macros.remove(body);emit(path,number,line);continue;}
    if(kind.equals("version")){var version=VERSION.matcher(body);if(version.matches())macros.put("__VERSION__",version.group(1));continue;}
    if(kind.equals("error"))throw new IOException("#error "+body);
    var constant=CONSTANT.matcher(line);
    if(constant.matches()){var option=pack.options().get(constant.group(1));if(option!=null&&option.constant())line=line.replaceFirst("(=)\\s*[^;]+;","$1 "+Matcher.quoteReplacement(selected.get(option.name()))+";");}
    emit(path,number,line);
   }catch(IOException e){throw new IOException(path+":"+number+": "+e.getMessage(),e);}
  }
  if(!branches.isEmpty())throw new IOException(path+": unclosed #if");includes.removeLast();
 }
 private static String stripComment(String text){return text.replaceFirst("//.*$","").replaceAll("/\\*.*?\\*/","").trim();}
 public static String resolve(String parent,String include)throws IOException{
  if(include.contains("\\")||include.contains(":")||include.indexOf('\0')>=0)throw new IOException("Unsafe include path");
  var parts=new ArrayDeque<String>();String candidate=include.startsWith("/")?"shaders"+include:parent.substring(0,parent.lastIndexOf('/')+1)+include;
  for(String part:candidate.split("/")){if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(parts.size()<=1)throw new IOException("Include escapes shader root");parts.removeLast();}else parts.addLast(part);}
  if(parts.isEmpty()||!parts.getFirst().equals("shaders"))throw new IOException("Include escapes shader root");return String.join("/",parts);
 }
 public static Map<String,String> environment(){var env=new HashMap<String,String>();env.put("PRISMORA","1");env.put("MC_VERSION","260300");env.put("MC_GL_VERSION","330");env.put("MC_GLSL_VERSION","330");String os=System.getProperty("os.name").toLowerCase(Locale.ROOT);env.put(os.contains("win")?"MC_OS_WINDOWS":os.contains("mac")?"MC_OS_MAC":"MC_OS_LINUX","1");return Map.copyOf(env);}
}
