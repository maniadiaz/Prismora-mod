package com.prismora.pack;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PackOptionsTest {
 @Test void appliesClampedOverrideWithoutChangingPack()throws Exception{
  var pack=new PackLoader().load(Path.of("examples/Prismora-Bloom"));
  var configured=PackOptions.apply(pack,Map.of("bloom_strength",2.0));
  var original=pack.manifest().getAsJsonArray("passes").get(3).getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray("BloomOptions").get(0).getAsJsonObject();
  var changed=configured.manifest().getAsJsonArray("passes").get(3).getAsJsonObject().getAsJsonObject("uniforms").getAsJsonArray("BloomOptions").get(0).getAsJsonObject();
  assertEquals(0.35,original.get("value").getAsDouble());assertEquals(1.0,changed.get("value").getAsDouble());
 }
 @Test void rejectsMissingUniformAndInvalidRange()throws Exception{
  var pack=new PackLoader().load(Path.of("examples/Prismora-Bloom"));var manifest=pack.manifest().deepCopy();var option=manifest.getAsJsonObject("options").getAsJsonObject("bloom_strength");
  option.addProperty("index",99);assertThrows(IOException.class,()->PackOptions.read(manifest));
  option.addProperty("index",0);option.addProperty("step",0);assertThrows(IOException.class,()->PackOptions.read(manifest));
 }
}
