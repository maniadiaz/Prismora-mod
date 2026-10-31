package com.prismora.compat;
import com.google.gson.*;
import com.prismora.pack.PackLoader;
import java.nio.file.Path;
public final class PackInspector {
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Usage: inspectShaderPack -PshaderPack=<folder-or-zip>");
  var snapshot=new PackLoader().load(Path.of(args[0]));var output=new JsonObject();output.addProperty("id",snapshot.id());output.addProperty("format",LegacyPack.isLegacy(snapshot)?"OptiFine/Iris":"Prismora");
  if(LegacyPack.isLegacy(snapshot)){var pack=LegacyPack.inspect(snapshot.id(),snapshot.files());output.addProperty("files",snapshot.files().size());output.addProperty("options",pack.options().size());output.addProperty("screens",pack.screens().size());output.addProperty("profiles",pack.profiles().size());output.addProperty("requiresGeometry",pack.requiresGeometry());output.add("dimensions",new Gson().toJsonTree(pack.dimensions().keySet()));output.add("notes",new Gson().toJsonTree(pack.notes()));}
  System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(output));
 }
}
