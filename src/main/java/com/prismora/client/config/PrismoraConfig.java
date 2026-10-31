package com.prismora.client.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class PrismoraConfig {
    private final Path file;
    private String selected = "";
    public PrismoraConfig(Path file) { this.file = file; }
    public String selected() { return selected; }
    public void load() throws IOException {
        if (!Files.exists(file)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            selected = json.get("selected").getAsString();
            if (selected.contains("/") || selected.contains("\\") || selected.equals("..")) throw new IOException("Invalid selection");
        } catch (RuntimeException e) { throw new IOException("Invalid Prismora config", e); }
    }
    public void select(String id) throws IOException {
        if (id.contains("/") || id.contains("\\") || id.equals("..")) throw new IOException("Invalid selection");
        Files.createDirectories(file.toAbsolutePath().getParent());
        JsonObject json = new JsonObject(); json.addProperty("selected", id);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        selected = id;
    }
}
