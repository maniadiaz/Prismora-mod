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
    public boolean busy() { return busy; }
    private ShaderController() {
        var root = Minecraft.getInstance().gameDirectory.toPath();
        manager = new ShaderPackManager(root.resolve("shaderpacks"));
        config = new PrismoraConfig(root.resolve("config/prismora.json"));
        try { config.load(); } catch(IOException e) { fail(e); }
        refresh();
    }
    public ShaderPackManager manager() { return manager; }
    public List<ShaderPackManager.Entry> entries() { return entries; }
    public String selected() { return config.selected(); }
    public String error() { return error; }
    public void refresh() {
        try {
            entries = manager.scan();
            if (!selected().isEmpty() && entries.stream().noneMatch(e -> e.id().equals(selected()) && e.valid())) {
                config.select(""); error = "Selected shaderpack is missing or invalid";
            }
        } catch(IOException e) { fail(e); }
    }
    public void select(ShaderPackManager.Entry entry) {
        if(busy)return;
        if(entry != null && !entry.valid()) { error=entry.error();return; }
        busy=true;error="";
        com.prismora.render.PrismoraRenderer.activate(entry==null?null:entry.pack()).whenCompleteAsync((unused,failure)->{
            if(failure==null) {
                try { config.select(entry==null?"":entry.id()); } catch(IOException e) { fail(e); }
                busy=false;
            } else {
                fail(new IOException(failure.getCause()==null?failure.getMessage():failure.getCause().getMessage(),failure));
                com.prismora.render.PrismoraRenderer.activate(null).whenCompleteAsync((ignored,recovery)->{
                    try { config.select(""); } catch(IOException e) { fail(e); }
                    busy=false;
                },Minecraft.getInstance());
            }
        },Minecraft.getInstance());
    }
    public void reload() {
        if(busy)return;refresh();
        if(selected().isEmpty())select(null);
        else entries.stream().filter(e->e.id().equals(selected())).findFirst().ifPresent(this::select);
    }
    public void fail(Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); Prismora.LOGGER.error("Prismora: {}", error, e); }
}
