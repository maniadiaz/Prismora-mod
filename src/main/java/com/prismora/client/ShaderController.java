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
    private boolean busy;
    private PackWatcher watcher;
    private int generation;
    public int generation(){return generation;}
    public boolean busy() { return busy; }
    private ShaderController() {
        var root = Minecraft.getInstance().gameDirectory.toPath();
        manager = new ShaderPackManager(root.resolve("shaderpacks"));
        config = new PrismoraConfig(root.resolve("config/prismora.json"));
        try { config.load(); } catch(IOException e) { fail(e); }
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
    public void select(ShaderPackManager.Entry entry) {
        if(busy)return;
        if(entry != null && !entry.valid()) { error=entry.error();return; }
        busy=true;error="";
        com.prismora.render.PrismoraRenderer.activate(entry==null?null:PackOptions.apply(entry.pack(),config.options(entry.id()))).whenCompleteAsync((unused,failure)->{
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
        if(busy)return;refresh();
        if(selected().isEmpty())select(null);
        else entries.stream().filter(e->e.id().equals(selected())).findFirst().ifPresent(this::select);
    }
    private void resetWatcher(){try{if(watcher!=null)watcher.watch(manager.directory(),selected());}catch(IOException e){fail(e);}}
    public void tick(){if(!busy && watcher!=null && watcher.changed()){if(selected().isEmpty()){refresh();resetWatcher();}else reload();}}
    public void close(){try{var current=watcher;watcher=null;if(current!=null)current.close();}catch(IOException e){Prismora.LOGGER.warn("Cannot close pack watcher",e);}}
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
