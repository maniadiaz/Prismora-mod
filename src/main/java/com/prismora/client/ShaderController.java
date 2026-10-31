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
        if (entry != null && !entry.valid()) { error = entry.error(); return; }
        try { config.select(entry == null ? "" : entry.id()); error = ""; }
        catch(IOException e) { fail(e); }
    }
    public void fail(Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); Prismora.LOGGER.error("Prismora: {}", error, e); }
}
