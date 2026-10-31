package com.prismora.compat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.prismora.pack.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyPackTest {
 @TempDir Path temp;
 static Map<String,byte[]> fixture(){
  var files=new TreeMap<String,byte[]>();
  files.put("shaders/final.fsh",("#version 120\n#define BLOOM // Bloom toggle\n#define EXPOSURE 1.0 // [0.5 1.0 2.0]\nvoid main(){gl_FragColor=vec4(1.0);}\n").getBytes(StandardCharsets.UTF_8));
  files.put("shaders/world-1/final.fsh","void main(){}".getBytes(StandardCharsets.UTF_8));
  files.put("shaders/shaders.properties",("screen=<profile> [CAMERA]\nscreen.CAMERA=BLOOM EXPOSURE\nscreen.CAMERA.columns=2\nsliders=EXPOSURE\nprofile.LOW=!BLOOM EXPOSURE:0.5\nprofile.HIGH=profile.LOW BLOOM EXPOSURE:2.0\n").getBytes(StandardCharsets.UTF_8));
  files.put("shaders/lang/es_MX.lang","option.BLOOM=Resplandor\nscreen.CAMERA=Camara\n".getBytes(StandardCharsets.UTF_8));
  files.put("shaders/block.properties","block.100=minecraft:stone minecraft:dirt\n".getBytes(StandardCharsets.UTF_8));return files;
 }
 @Test void discoversProgramsOptionsScreensAndTranslations()throws Exception{
  var pack=LegacyPack.inspect("demo.zip",fixture());assertEquals(2,pack.options().size());assertTrue(pack.options().get("EXPOSURE").slider());assertEquals("Resplandor",pack.text("es_mx","option.BLOOM","fallback"));assertEquals(List.of("BLOOM","EXPOSURE"),pack.screens().get("CAMERA").entries());assertEquals("minecraft:stone minecraft:dirt",pack.blocks().get("block.100"));
 }
 @Test void profilesInheritAndDimensionsOverrideBase()throws Exception{var pack=LegacyPack.inspect("demo",fixture());assertEquals("true",pack.profiles().get("HIGH").get("BLOOM"));assertEquals("2.0",pack.profiles().get("HIGH").get("EXPOSURE"));assertEquals("shaders/world-1/final.fsh",pack.programs("minecraft:the_nether").get("final").fragment());assertEquals("shaders/final.fsh",pack.programs("minecraft:overworld").get("final").fragment());}
 @Test void rejectsProfileCyclesAndInvalidOptionValues()throws Exception{
  var files=fixture();files.put("shaders/shaders.properties","profile.A=profile.B\nprofile.B=profile.A".getBytes(StandardCharsets.UTF_8));assertThrows(IOException.class,()->LegacyPack.inspect("bad",files));
  var pack=LegacyPack.inspect("ok",fixture());assertThrows(IOException.class,()->pack.validated(Map.of("EXPOSURE","bad; injected()")));
 }
 @Test void folderAndWrappedZipHaveSameMetadata()throws Exception{
  Path folder=temp.resolve("folder");for(var f:fixture().entrySet()){var path=folder.resolve(f.getKey());Files.createDirectories(path.getParent());Files.write(path,f.getValue());}
  var direct=new PackLoader().load(folder);assertTrue(LegacyPack.isLegacy(direct));Path zip=temp.resolve("pack.zip");
  try(var out=new ZipOutputStream(Files.newOutputStream(zip))){for(var f:fixture().entrySet()){out.putNextEntry(new ZipEntry("release/"+f.getKey()));out.write(f.getValue());out.closeEntry();}}
  assertEquals(direct.manifest().get("optionCount"),new PackLoader().load(zip).manifest().get("optionCount"));
 }
 @Test void propertiesHandleEscapesAndContinuation()throws Exception{var p=PropertiesReader.parse("screen=A \\\n B\nname=Hello\\u0020World\n");assertEquals("A B",p.get("screen"));assertEquals("Hello World",p.get("name"));}
 @Test void includeGuardsAreNotExposedAsOptions()throws Exception{var f=fixture();f.put("shaders/lib/test.glsl","#ifndef LIB_TEST\n#define LIB_TEST\n#define FEATURE // toggle\n#endif\n".getBytes(StandardCharsets.UTF_8));assertFalse(LegacyPack.inspect("x",f).options().containsKey("LIB_TEST"));}
}
