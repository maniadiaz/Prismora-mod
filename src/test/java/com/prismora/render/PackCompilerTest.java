package com.prismora.render;
import com.prismora.pack.PackLoader;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackCompilerTest {
 @Test void preservesTargetsAndDynamicInputs() throws Exception {
  var result=new PackCompiler().compile(new PackLoader().load(Path.of("examples/Prismora-Multipass")));
  var chain=JsonParser.parseString(new String(result.get("assets/minecraft/post_effect/end_of_frame.json"),StandardCharsets.UTF_8)).getAsJsonObject();
  assertEquals(256,chain.getAsJsonObject("targets").getAsJsonObject("prismora:color0").get("width").getAsInt());
  var inputs=chain.getAsJsonArray("passes").get(0).getAsJsonObject().getAsJsonArray("inputs");
  assertEquals("prismora:uniforms",inputs.get(1).getAsJsonObject().get("location").getAsString());
  assertEquals(4,chain.getAsJsonArray("passes").size());
 }
 @Test void emitsPrivateTargetThenSafeCopyToMain() throws Exception {
  var pack=new PackLoader().load(Path.of("examples/Prismora-Color"));
  var result=new PackCompiler().compile(pack);
  var chain=JsonParser.parseString(new String(result.get("assets/minecraft/post_effect/end_of_frame.json"),StandardCharsets.UTF_8)).getAsJsonObject();
  var passes=chain.getAsJsonArray("passes");assertEquals(2,passes.size());
  assertEquals("prismora:__screen",passes.get(0).getAsJsonObject().get("output").getAsString());
  assertEquals("minecraft:main",passes.get(1).getAsJsonObject().get("output").getAsString());
  assertArrayEquals(pack.files().get("shaders/post.fsh"),result.get("assets/prismora/shaders/pack/post.fsh"));
 }
}
