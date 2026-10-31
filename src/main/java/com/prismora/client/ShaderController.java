package com.prismora.client;

import com.prismora.Prismora;
import com.prismora.client.config.PrismoraConfig;
import com.prismora.pack.*;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.util.*;

public final class ShaderController {
    public static final ShaderController INSTANCE = new ShaderController();
    private final ShaderPackManager manager;
    private final PrismoraConfig config;
    private List<ShaderPackManager.Entry> entries = List.of();
    private String error = "";
    private String inspected="";private int legacyGeneration=-1;private String legacyId="";private com.prismora.compat.LegacyPack legacyCache;
    public String inspected(){return inspected.isEmpty()?selected():inspected;}
    public com.prismora.compat.LegacyPack legacyInspected(){
        String id=inspected();if(legacyGeneration==generation && legacyId.equals(id))return legacyCache;
        legacyGeneration=generation;legacyId=id;legacyCache=null;
        var entry=entries.stream().filter(e->e.id().equals(id)&&e.valid()).findFirst().orElse(null);
        if(entry!=null && com.prismora.compat.LegacyPack.isLegacy(entry.pack()))try{legacyCache=com.prismora.compat.LegacyPack.inspect(entry.id(),entry.pack().files());}catch(IOException e){fail(e);}
        return legacyCache;
    }
    public Map<String,String> legacyValues(String id){return config.legacyOptions(id);}
    public void applyLegacyOptions(String id,Map<String,String> values){if(busy||closed)return;try{var pack=legacyInspected();if(pack==null||!pack.id().equals(id))throw new IOException("Shaderpack changed; reopen options");config.legacyOptions(id,pack.validated(values));var entry=entries.stream().filter(e->e.id().equals(id)).findFirst().orElseThrow();select(entry);}catch(Exception e){fail(e);}}

    private boolean busy;
    private PackWatcher watcher;
    private int generation;
    private int lastWidth,lastHeight;private long resizeAt;
    private boolean suspended,closed;
    private com.prismora.performance.PerformanceSettings runtimePerformance;
    private String performanceReason="";
    public boolean suspended(){return suspended;}
    public String performanceReason(){return performanceReason;}
    public com.prismora.performance.PerformanceSettings effectivePerformance(){return runtimePerformance;}
    public void adapt(com.prismora.performance.AdaptiveController.Action action){
        if(busy||closed||suspended||selected().isEmpty())return;
        if(action==com.prismora.performance.AdaptiveController.Action.REDUCE){runtimePerformance=runtimePerformance.reduced();performanceReason="prismora.adapt.reduced";reload();}
        if(action==com.prismora.performance.AdaptiveController.Action.SUSPEND){
            busy=true;performanceReason="prismora.adapt.suspended";
            com.prismora.render.PrismoraRenderer.activate(null,runtimePerformance).whenCompleteAsync((unused,failure)->{busy=false;if(failure!=null)fail(new IOException(failure));else suspended=true;},Minecraft.getInstance());
        }
    }
    public com.prismora.performance.PerformanceSettings performance(){return config.performance();}
    public void performance(com.prismora.performance.PerformanceSettings settings){if(busy||closed)return;try{config.performance(settings);runtimePerformance=settings;suspended=false;performanceReason="";AdaptivePerformance.reset();reload();}catch(IOException e){fail(e);}}
    public void applyOptions(Map<String,Double> values){if(busy)return;try{config.setOptions(selected(),values);reload();}catch(IOException e){fail(e);}}
    public int generation(){return generation;}
    public boolean busy() { return busy; }
    private ShaderController() {
        var root = Minecraft.getInstance().gameDirectory.toPath();
        manager = new ShaderPackManager(root.resolve("shaderpacks"));
        config = new PrismoraConfig(root.resolve("config/prismora.json"));
        try { config.load(); } catch(IOException e) { fail(e); }
        runtimePerformance=config.performance();
        refresh();
        try{watcher=new PackWatcher(600);watcher.watch(manager.directory(),selected());}catch(IOException e){fail(e);}
    }
    public ShaderPackManager manager() { return manager; }
    public List<ShaderPackManager.Entry> entries() { return entries; }
    public String selected() { return config.selected(); }
    public String error() { return error; }
    public void refresh() {
        try {
            entries = manager.scan();generation++;
            if (!selected().isEmpty() && entries.stream().noneMatch(e -> e.id().equals(selected()) && e.valid())) {
                config.select(""); error = "Selected shaderpack is missing or invalid";
            }
        } catch(IOException e) { fail(e); }
    }
    public void select(ShaderPackManager.Entry entry){if(busy||closed)return;if(entry!=null)inspected=entry.id();runtimePerformance=config.performance();suspended=false;performanceReason="";AdaptivePerformance.reset();activateEntry(entry);}
    private void activateEntry(ShaderPackManager.Entry entry) {
        if(busy||closed)return;
        if(entry != null && !entry.valid()) { error=entry.error();return; }
        busy=true;error="";
        java.util.concurrent.CompletableFuture<Void> activation;
        try{
            var pack=entry==null?null:entry.pack();
            if(com.prismora.compat.LegacyPack.isLegacy(pack))pack=com.prismora.compat.LegacyAdapter.prepare(pack,config.legacyOptions(entry.id()),com.prismora.render.PrismoraRenderer.dimensionName());
            else if(pack!=null)pack=PackOptions.apply(pack,config.options(entry.id()));
            activation=com.prismora.render.PrismoraRenderer.activate(pack,effectivePerformance());
        }catch(Exception e){activation=java.util.concurrent.CompletableFuture.failedFuture(e);}
        activation.whenCompleteAsync((unused,failure)->{
            if(closed){busy=false;return;}
            if(failure==null) {
                try { config.select(entry==null?"":entry.id()); } catch(IOException e) { fail(e); }
                busy=false;resetWatcher();
            } else {
                fail(new IOException(failure.getCause()==null?failure.getMessage():failure.getCause().getMessage(),failure));
                com.prismora.render.PrismoraRenderer.activate(null).whenCompleteAsync((ignored,recovery)->{
                    try { config.select(""); } catch(IOException e) { fail(e); }
                    busy=false;resetWatcher();
                },Minecraft.getInstance());
            }
        },Minecraft.getInstance());
    }
    public void reload() {
        if(busy||closed)return;if(suspended){suspended=false;runtimePerformance=config.performance();performanceReason="";}refresh();
        if(selected().isEmpty())activateEntry(null);
        else entries.stream().filter(e->e.id().equals(selected())).findFirst().ifPresent(this::activateEntry);
    }
    private void resetWatcher(){try{if(watcher!=null)watcher.watch(manager.directory(),selected());}catch(IOException e){fail(e);}}
    public void tick(){
        if(closed)return;
        if(!busy && watcher!=null && watcher.changed()){if(selected().isEmpty()||suspended){refresh();resetWatcher();}else reload();}
        var window=Minecraft.getInstance().getWindow();long now=System.nanoTime();
        if(window.getWidth()!=lastWidth||window.getHeight()!=lastHeight){lastWidth=window.getWidth();lastHeight=window.getHeight();resizeAt=now;}
        if(!busy && !suspended && !selected().isEmpty() && now-resizeAt>600_000_000L && (com.prismora.render.PrismoraRenderer.compiledWidth()!=lastWidth||com.prismora.render.PrismoraRenderer.compiledHeight()!=lastHeight))reload();
    }
    public void close(){closed=true;AdaptivePerformance.close();try{var current=watcher;watcher=null;if(current!=null)current.close();}catch(IOException e){Prismora.LOGGER.warn("Cannot close pack watcher",e);}}
    public List<PackOptions.Option> options(){
        try{return entries.stream().filter(e->e.id().equals(selected())&&e.valid()).findFirst().map(e->{try{return PackOptions.read(e.pack().manifest());}catch(IOException failure){throw new IllegalArgumentException(failure);}}).orElse(List.of());}
        catch(RuntimeException e){fail(e);return List.of();}
    }
    public double optionValue(PackOptions.Option option){return config.options(selected()).getOrDefault(option.id(),option.defaultValue());}
    public void setOption(PackOptions.Option option,double value){
        if(busy)return;
        try{config.setOption(selected(),option.id(),Math.clamp(value,option.min(),option.max()));reload();}catch(IOException e){fail(e);}
    }
    public void recover(Exception e){select(null);fail(e);}
    public void fail(Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); Prismora.LOGGER.error("Prismora: {}", error, e); }
}
