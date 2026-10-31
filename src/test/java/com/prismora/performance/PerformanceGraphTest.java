package com.prismora.performance;
import com.google.gson.*;
import com.prismora.pack.PackLoader;
import com.prismora.render.PackCompiler;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceGraphTest {
 private JsonObject chain(PerformanceSettings.Profile profile)throws Exception{
  var pack=new PackLoader().load(Path.of("examples/Prismora-Bloom"));var files=new PackCompiler().compile(pack,PerformanceSettings.preset(profile),1920,1080);
  return JsonParser.parseString(new String(files.get("assets/minecraft/post_effect/end_of_frame.json"),StandardCharsets.UTF_8)).getAsJsonObject();
 }
 @Test void entryRemovesBloomWorkInsteadOfOnlyChangingStrength()throws Exception{var c=chain(PerformanceSettings.Profile.ENTRY);assertEquals(0,c.getAsJsonArray("passes").size());assertEquals(0,c.getAsJsonObject("targets").size());}
 @Test void lowUsesScaledBuffersAndBilinearUpscaling()throws Exception{
  var c=chain(PerformanceSettings.Profile.LOW);var targets=c.getAsJsonObject("targets");assertEquals(480,targets.getAsJsonObject("prismora:bright").get("width").getAsInt());assertEquals(270,targets.getAsJsonObject("prismora:bright").get("height").getAsInt());assertEquals(960,targets.getAsJsonObject("prismora:__screen").get("width").getAsInt());
  assertTrue(c.getAsJsonArray("passes").get(4).getAsJsonObject().getAsJsonArray("inputs").get(0).getAsJsonObject().get("bilinear").getAsBoolean());
 }
 @Test void everyProfileMatchesMojangCodec()throws Exception{for(var p:PerformanceSettings.Profile.values())assertTrue(net.minecraft.client.renderer.PostChainConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,chain(p)).isSuccess());}
 @Test void excessiveTargetMemoryIsRejectedBeforeGpuAllocation()throws Exception{
  var pack=new PackLoader().load(Path.of("examples/Prismora-Color"));
  assertThrows(IllegalArgumentException.class,()->new PackCompiler().compile(pack,PerformanceSettings.preset(PerformanceSettings.Profile.HIGH),16384,16384));
 }
 @Test void tinyWindowNeverCreatesZeroSizedTargets()throws Exception{var files=new PackCompiler().compile(new PackLoader().load(Path.of("examples/Prismora-Bloom")),PerformanceSettings.preset(PerformanceSettings.Profile.LOW),1,1);var c=JsonParser.parseString(new String(files.get("assets/minecraft/post_effect/end_of_frame.json"),StandardCharsets.UTF_8)).getAsJsonObject();assertEquals(1,c.getAsJsonObject("targets").getAsJsonObject("prismora:bright").get("width").getAsInt());}
}
