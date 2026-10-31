package com.prismora.render;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** In-memory resource snapshot: reloading never reads mutable ZIP or filesystem data. */
public final class MemoryPack extends PathPackResources {
 private final Map<String,byte[]> files;
 public MemoryPack(PackLocationInfo info,Map<String,byte[]> files) { super(info,Path.of("."));this.files=files; }
 private IoSupplier<InputStream> resource(String path) {
  byte[] bytes=files.get(path);return bytes==null?null:()->new ByteArrayInputStream(bytes);
 }
 @Override public IoSupplier<InputStream> getRootResource(String... paths) { return resource(String.join("/",paths)); }
 @Override public IoSupplier<InputStream> getResource(PackType type,Identifier id) { return type==PackType.CLIENT_RESOURCES?resource("assets/"+id.getNamespace()+"/"+id.getPath()):null; }
 @Override public Set<String> getNamespaces(PackType type) { return type==PackType.CLIENT_RESOURCES?Set.of("prismora","minecraft"):Set.of(); }
 @Override public void listResources(PackType type,String namespace,String directory,PackResources.ResourceOutput output) {
  if(type!=PackType.CLIENT_RESOURCES) return;
  String prefix="assets/"+namespace+"/";
  files.forEach((path,bytes)->{ if(path.startsWith(prefix+directory+"/")) output.accept(Identifier.fromNamespaceAndPath(namespace,path.substring(prefix.length())),()->new ByteArrayInputStream(bytes)); });
 }
}
