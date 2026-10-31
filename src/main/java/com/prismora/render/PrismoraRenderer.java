package com.prismora.render;
import com.prismora.Prismora;
import com.prismora.pack.PackLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PrismoraRenderer {
 private static volatile Map<String,byte[]> active=Map.of();
 private static boolean initialized,ready,inFlight;
 private static int compiledWidth,compiledHeight;
 private static boolean dynamicData;
 public static int compiledWidth(){return compiledWidth;}
 public static int compiledHeight(){return compiledHeight;}
 public static com.google.gson.JsonObject activeChain(){return active.isEmpty()?new com.google.gson.JsonObject():com.google.gson.JsonParser.parseString(new String(active.get("assets/minecraft/post_effect/end_of_frame.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
 private static Map<String,byte[]> compile(PackLoader.ShaderPack pack,com.prismora.performance.PerformanceSettings settings){
  var window=Minecraft.getInstance().getWindow();compiledWidth=window.getWidth();compiledHeight=window.getHeight();
  var result=new PackCompiler().compile(pack,settings,compiledWidth,compiledHeight);
  dynamicData=new String(result.get("assets/minecraft/post_effect/end_of_frame.json"),java.nio.charset.StandardCharsets.UTF_8).contains("PrismoraData");return result;
 }
 private static void restoreSelection() {
  if(initialized)return;initialized=true;
  try {
   var root=Minecraft.getInstance().gameDirectory.toPath();
   var config=new com.prismora.client.config.PrismoraConfig(root.resolve("config/prismora.json"));config.load();
   if(!config.selected().isEmpty()) active=compile(com.prismora.pack.PackOptions.apply(new PackLoader().load(root.resolve("shaderpacks").resolve(config.selected())),config.options(config.selected())),config.performance());
  } catch(Exception e) { Prismora.LOGGER.error("Cannot restore selected shaderpack",e);active=Map.of(); }
 }
 public static void provide(Consumer<Pack> consumer) {
  restoreSelection();
  Map<String,byte[]> snapshot=active;if(snapshot.isEmpty())return;
  var location=new PackLocationInfo("prismora/active",Component.literal("Prismora"),PackSource.BUILT_IN,Optional.empty());
  var supplier=new Pack.ResourcesSupplier() {
   public PackMetadataResources openMetadata(PackLocationInfo info) { return new MemoryPack(info,snapshot); }
   public Stream<PackResources> openResources(PackLocationInfo info,Pack.Metadata metadata) { return Stream.of(new MemoryPack(info,snapshot)); }
  };
  // Hidden required pack is present only while a shader is enabled.
  var metadata=new Pack.Metadata(Component.literal("Prismora active shader"),PackCompatibility.COMPATIBLE,net.minecraft.world.flag.FeatureFlagSet.of(),List.of(),true);
  consumer.accept(new Pack(location,supplier,metadata,new PackSelectionConfig(true,Pack.Position.TOP,true)));
 }
 public static CompletableFuture<Void> activate(PackLoader.ShaderPack pack) { return activate(pack,com.prismora.performance.PerformanceSettings.preset(com.prismora.performance.PerformanceSettings.Profile.MEDIUM)); }
 public static CompletableFuture<Void> activate(PackLoader.ShaderPack pack,com.prismora.performance.PerformanceSettings settings) {
  Map<String,byte[]> compiled;
  try{compiled=pack==null?Map.of():compile(pack,settings);}catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
  initialized=true;ready=false;inFlight=true;active=compiled;
  Minecraft mc=Minecraft.getInstance();
  return mc.reloadResourcePacks().thenRunAsync(()->{
   if(pack==null){ready=true;inFlight=false;Uniforms.release();return;}
   validateCurrent();if(!dynamicData)Uniforms.release();ready=true;inFlight=false;
   Prismora.LOGGER.info("PRISMORA_SHADER_READY: {}",pack.id());
  },mc);
 }
 public static void beforeFrame() {
  var mc=Minecraft.getInstance();
  if(!active.isEmpty() && dynamicData)Uniforms.update();
  if(!ready && !inFlight && !active.isEmpty() && mc.isGameLoadFinished()) {
   try { validateCurrent();ready=true; }
   catch(Exception e) {
    com.prismora.client.ShaderController.INSTANCE.fail(e);
    inFlight=true;
    mc.execute(()->com.prismora.client.ShaderController.INSTANCE.recover(e));
   }
  }
  if(!ready && (inFlight || !active.isEmpty())) mc.gameRenderer.gameRenderState().requestedPostEffects.remove(Identifier.withDefaultNamespace("end_of_frame"));
 }
 public static void validateCurrent() {
  if(active.isEmpty())return;
  if(dynamicData)Uniforms.ensure();
  var json=com.google.gson.JsonParser.parseString(new String(active.get("assets/minecraft/post_effect/end_of_frame.json"),java.nio.charset.StandardCharsets.UTF_8));
  var config=net.minecraft.client.renderer.PostChainConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
  var projection=new net.minecraft.client.renderer.Projection();projection.setupOrtho(0.1F,1000F,1F,1F,false);
  try(var diagnostics=new CompileDiagnostics();var buffer=new net.minecraft.client.renderer.ProjectionMatrixBuffer("prismora-validation")) {
   try(var chain=net.minecraft.client.renderer.PostChain.load(config,Minecraft.getInstance().getTextureManager(),LevelTargetBundle.MAIN_TARGETS,Identifier.withDefaultNamespace("end_of_frame"),projection,buffer)) { }
   catch(Exception e) { throw new IllegalStateException(e.getMessage()+"\n"+diagnostics.details(),e); }
  } catch(Exception e) { throw new IllegalStateException("Shader compilation failed: "+e.getMessage(),e); }
 }
 public static void clearSnapshot() { active=Map.of(); }
}
