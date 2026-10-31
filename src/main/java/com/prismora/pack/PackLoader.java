package com.prismora.pack;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Bounded snapshot: no open ZIP handles or unvalidated paths survive loading. */
public final class PackLoader {
    public static final int MAX_FILE = 1024 * 1024, MAX_TOTAL = PackFiles.MAX_TOTAL, MAX_ENTRIES = PackFiles.MAX_ENTRIES;
    public record ShaderPack(String id, String name, JsonObject manifest, Map<String, byte[]> files) {}

    public ShaderPack load(Path source) throws IOException {
        Map<String, byte[]> files = PackFiles.read(source);
        if(!files.containsKey("pack.json"))return com.prismora.compat.LegacyPack.inspect(source.getFileName().toString(),files).snapshot();
        try {
            byte[] metadata = files.get("pack.json");
            if (metadata == null || metadata.length > 65536) throw new IOException("Missing or oversized pack.json");
            JsonObject manifest = JsonParser.parseString(new String(metadata, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (manifest.get("formatVersion").getAsDouble() != 1) throw new IOException("Unsupported formatVersion");
            String name = manifest.get("name").getAsString();
            if (name.isBlank() || name.length() > 100) throw new IOException("Invalid pack name");
            JsonArray passes = manifest.getAsJsonArray("passes");
            if (passes.isEmpty() || passes.size() > 16) throw new IOException("Expected 1 to 16 passes");
            Set<String> targets = new HashSet<>(Set.of("sceneColor", "sceneDepth"));
            Set<String> names = new HashSet<>();
            if(manifest.has("targets")) {
                JsonObject descriptors=manifest.getAsJsonObject("targets");
                if(descriptors.size()>16)throw new IOException("Too many targets");
                for(var descriptor:descriptors.entrySet()) {
                    if(!descriptor.getKey().matches("[a-z][a-z0-9_]*") || descriptor.getKey().equals("screen"))throw new IOException("Invalid target name");
                    var options=descriptor.getValue().getAsJsonObject();
                    for(String dimension:List.of("width","height")) if(options.has(dimension)) {
                        double size=options.get(dimension).getAsDouble();
                        if(size!=Math.rint(size)||size<1||size>4096)throw new IOException("Target dimension must be 1..4096");
                    }
                    if(options.has("persistent") && options.get("persistent").getAsBoolean())throw new IOException("Persistent targets are not supported in format 1");
                }
            }
            for (var value : passes) {
                JsonObject pass = value.getAsJsonObject();
                String passName = pass.get("name").getAsString();
                if (!passName.matches("[a-z0-9_]+") || !names.add(passName)) throw new IOException("Invalid or repeated pass name");
                for (String stage : List.of("vertex", "fragment")) {
                    String shader = pass.get(stage).getAsString(); safePath(shader);
                    if (!(stage.equals("vertex")?shader.endsWith(".vsh"):shader.endsWith(".fsh")) || !shader.matches("shaders/[a-z0-9_/]+\\.(vsh|fsh)") || !files.containsKey(shader)) throw new IOException("Missing or invalid shader: " + shader);
                }
                if(pass.has("scale")) {double scale=pass.get("scale").getAsDouble();if(!Double.isFinite(scale)||scale<0.25||scale>1)throw new IOException("Pass scale must be 0.25..1");}
                if(pass.has("feature")) {
                    if(!pass.get("feature").getAsString().equals("bloom") || !pass.has("bypass") || !pass.getAsJsonObject("inputs").has(pass.get("bypass").getAsString()))throw new IOException("Optional pass requires a known feature and bypass sampler");
                    if(pass.getAsJsonObject("inputs").get(pass.get("bypass").getAsString()).getAsString().equals("sceneDepth"))throw new IOException("A depth buffer cannot be used as a color bypass");
                }
                String output = pass.get("output").getAsString();
                if(output.equals("screen") && value!=passes.get(passes.size()-1))throw new IOException("Only the last pass can output to screen");
                if (!output.matches("screen|[a-z][a-z0-9_]*")) throw new IOException("Invalid output");
                var inputs = pass.getAsJsonObject("inputs");
                if (inputs == null || inputs.size() == 0 || inputs.size() > 8) throw new IOException("Expected 1 to 8 inputs");
                for (var input : inputs.entrySet()) {
                    String target = input.getValue().getAsString();
                    if ((!input.getKey().matches("[A-Za-z][A-Za-z0-9_]*") || input.getKey().equals("PrismoraData")) || !targets.contains(target)) throw new IOException("Invalid sampler or forward target: " + target);
                    if (target.equals(output)) throw new IOException("Input/output feedback; use an intermediate target");
                }
                if (!output.equals("screen")) targets.add(output);
            }
            if (!passes.get(passes.size()-1).getAsJsonObject().get("output").getAsString().equals("screen")) throw new IOException("Last pass must output to screen");
            PackOptions.read(manifest);
            return new ShaderPack(source.getFileName().toString(), name, manifest, Collections.unmodifiableMap(files));
        } catch (RuntimeException e) { throw new IOException("Invalid pack.json: " + e.getMessage(), e); }
    }

    public static void safePath(String name) throws IOException {
        if (name.isEmpty() || name.length() > 240 || name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe path: " + name);
        for (String part : name.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe path: " + name);
    }
}
