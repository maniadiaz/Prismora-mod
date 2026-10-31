package com.prismora.render;
import com.prismora.pack.PackLoader;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackCompilerTest {
 @Test void emitsPrivateTargetThenSafeCopyToMain() throws Exception {
  var pack=new PackLoader().load(Path.of("examples/Prismora-Color"));
  var result=new PackCompiler().compile(pack);
  var chain=JsonParser.parseString(new String(result.get("assets/minecraft/post_effect/end_of_frame.json"),StandardCharsets.UTF_8)).getAsJsonObject();
  var passes=chain.getAsJsonArray("passes");assertEquals(2,passes.size());
  assertEquals("prismora:final",passes.get(0).getAsJsonObject().get("output").getAsString());
  assertEquals("minecraft:main",passes.get(1).getAsJsonObject().get("output").getAsString());
  assertArrayEquals(pack.files().get("shaders/post.fsh"),result.get("assets/prismora/shaders/pack/post.fsh"));
 }
}
