package com.prismora.pack;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ShaderPackManager {
    public record Entry(String id, String name, PackLoader.ShaderPack pack, String error) { public boolean valid() { return pack != null; } }
    private final Path directory;
    public ShaderPackManager(Path directory) { this.directory = directory; }
    public Path directory() { return directory; }
    public List<Entry> scan() throws IOException {
        Files.createDirectories(directory);
        List<Entry> result = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().limit(257).toList()) {
                if (result.size() == 256) throw new IOException("Too many shaderpacks (limit 256)");
                if (!Files.isDirectory(path) && !path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
                try {
                    var pack = new PackLoader().load(path);
                    result.add(new Entry(pack.id(), pack.name(), pack, ""));
                } catch (IOException e) { result.add(new Entry(path.getFileName().toString(), path.getFileName().toString(), null, e.getMessage())); }
            }
        }
        return List.copyOf(result);
    }
}
