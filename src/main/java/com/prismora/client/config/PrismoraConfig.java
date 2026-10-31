package com.prismora.client.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PrismoraConfig {
    private final Path file;
    private String selected = "";
    private com.prismora.performance.PerformanceSettings performance=com.prismora.performance.PerformanceSettings.preset(com.prismora.performance.PerformanceSettings.Profile.MEDIUM);
    public com.prismora.performance.PerformanceSettings performance(){return performance;}
    public void performance(com.prismora.performance.PerformanceSettings value)throws IOException{
        var previous=performance;performance=Objects.requireNonNull(value);
        try{save();}catch(IOException e){performance=previous;throw e;}
    }
    private final Map<String,Map<String,String>> legacyOptions=new HashMap<>();
    private final Map<String,Map<String,Double>> options = new HashMap<>();
    public PrismoraConfig(Path file) { this.file = file; }
    public String selected() { return selected; }
    public Map<String,String> legacyOptions(String pack){return Map.copyOf(legacyOptions.getOrDefault(pack,Map.of()));}
    private static void validateLegacy(Map<String,String> values)throws IOException{
        if(values.size()>2048)throw new IOException("Too many legacy options");
        for(var entry:values.entrySet())if(!entry.getKey().matches("[A-Za-z_]\\w{0,127}|program\\.(?:world-?\\d+/)?[a-z0-9_]+")||!entry.getValue().matches("[A-Za-z0-9_+.\\-]{1,128}"))throw new IOException("Invalid legacy option");
    }
    public void legacyOptions(String pack,Map<String,String> values)throws IOException{
        validateId(pack);validateLegacy(values);if(!legacyOptions.containsKey(pack)&&legacyOptions.size()>=64)throw new IOException("Too many legacy presets");
        var previous=legacyOptions.put(pack,new HashMap<>(values));try{save();}catch(IOException e){if(previous==null)legacyOptions.remove(pack);else legacyOptions.put(pack,previous);throw e;}
    }
    public Map<String,Double> options(String pack) { return Map.copyOf(options.getOrDefault(pack,Map.of())); }
    private static void validateId(String id) throws IOException {
        if(id.length()>240||id.contains("/")||id.contains("\\")||id.contains(":")||id.equals("..")||id.equals("."))throw new IOException("Invalid pack id");
    }
    public void load() throws IOException {
        if(!Files.exists(file))return;
        if(Files.size(file)>1024*1024)throw new IOException("Prismora config is too large");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String selection=json.get("selected").getAsString();validateId(selection);
            Map<String,Map<String,Double>> loaded=new HashMap<>();
            if(json.has("options")) {
                var packs=json.getAsJsonObject("options");if(packs.size()>256)throw new IOException("Too many option presets");
                for(var pack:packs.entrySet()){
                    validateId(pack.getKey());Map<String,Double> values=new HashMap<>();
                    var settings=pack.getValue().getAsJsonObject();if(settings.size()>16)throw new IOException("Too many saved options");
                    for(var entry:settings.entrySet()){
                        double value=entry.getValue().getAsDouble();
                        if(!entry.getKey().matches("[a-z][a-z0-9_]{0,39}")||!Double.isFinite(value))throw new IOException("Invalid saved option");
                        values.put(entry.getKey(),value);
                    }
                    loaded.put(pack.getKey(),values);
                }
            }
            var settings=com.prismora.performance.PerformanceSettings.preset(com.prismora.performance.PerformanceSettings.Profile.MEDIUM);
            if(json.has("performance")){
                var value=json.getAsJsonObject("performance");
                settings=new com.prismora.performance.PerformanceSettings(com.prismora.performance.PerformanceSettings.Profile.valueOf(value.get("profile").getAsString()),value.get("scale").getAsDouble(),value.get("bloom").getAsBoolean(),value.get("adaptive").getAsBoolean(),value.get("targetFps").getAsInt(),value.get("fallback").getAsBoolean());
            }
            var loadedLegacy=new HashMap<String,Map<String,String>>();
            if(json.has("legacyOptions")){
                var packs=json.getAsJsonObject("legacyOptions");if(packs.size()>64)throw new IOException("Too many legacy presets");
                for(var pack:packs.entrySet()){validateId(pack.getKey());var values=new HashMap<String,String>();for(var entry:pack.getValue().getAsJsonObject().entrySet())values.put(entry.getKey(),entry.getValue().getAsString());validateLegacy(values);loadedLegacy.put(pack.getKey(),values);}
            }
            legacyOptions.clear();legacyOptions.putAll(loadedLegacy);
            performance=settings;selected=selection;options.clear();options.putAll(loaded);
        } catch(RuntimeException e){throw new IOException("Invalid Prismora config",e);}
    }
    public void select(String id)throws IOException{
        validateId(id);String previous=selected;selected=id;
        try{save();}catch(IOException e){selected=previous;throw e;}
    }
    public void setOptions(String pack,Map<String,Double> values)throws IOException{
        validateId(pack);if(values.size()>16)throw new IOException("Too many options");
        for(var entry:values.entrySet())if(!entry.getKey().matches("[a-z][a-z0-9_]{0,39}")||!Double.isFinite(entry.getValue()))throw new IOException("Invalid option");
        if(!options.containsKey(pack)&&options.size()>=256)throw new IOException("Too many option presets");
        var previous=options.put(pack,new HashMap<>(values));
        try{save();}catch(IOException e){if(previous==null)options.remove(pack);else options.put(pack,previous);throw e;}
    }
    public void setOption(String pack,String id,double value)throws IOException{
        validateId(pack);
        if(!id.matches("[a-z][a-z0-9_]{0,39}")||!Double.isFinite(value))throw new IOException("Invalid option");
        if(!options.containsKey(pack)&&options.size()>=256)throw new IOException("Too many option presets");
        var values=options.computeIfAbsent(pack,key->new HashMap<>());
        if(!values.containsKey(id)&&values.size()>=16)throw new IOException("Too many saved options");
        Double previous=values.put(id,value);
        try{save();}catch(IOException e){if(previous==null)values.remove(id);else values.put(id,previous);throw e;}
    }
    private void save()throws IOException{
        Files.createDirectories(file.toAbsolutePath().getParent());
        JsonObject json=new JsonObject();json.addProperty("selected",selected);json.add("options",new Gson().toJsonTree(options));json.add("performance",new Gson().toJsonTree(performance));json.add("legacyOptions",new Gson().toJsonTree(legacyOptions));
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        String serialized=json.toString();if(serialized.getBytes(StandardCharsets.UTF_8).length>1024*1024)throw new IOException("Config exceeds 1 MiB");Files.writeString(temporary,serialized,StandardCharsets.UTF_8);
        try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
