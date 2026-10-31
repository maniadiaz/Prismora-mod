package com.prismora.pack;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PackLoaderTest {
    @TempDir Path temp;
    private Path pack() throws IOException {
        Path root = temp.resolve("valid"); Files.createDirectories(root.resolve("shaders"));
        Files.writeString(root.resolve("shaders/post.vsh"), "vertex"); Files.writeString(root.resolve("shaders/post.fsh"), "fragment");
        Files.writeString(root.resolve("pack.json"), """
            {"formatVersion":1,"name":"Test","passes":[{"name":"post","vertex":"shaders/post.vsh","fragment":"shaders/post.fsh","inputs":{"SceneColor":"sceneColor"},"output":"screen"}]}
            """); return root;
    }
    @Test void loadsFolderAndZip() throws Exception {
        Path folder = pack(); assertEquals("Test", new PackLoader().load(folder).name());
        Path zip = temp.resolve("pack.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip)); var paths = Files.walk(folder)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) { out.putNextEntry(new ZipEntry(folder.relativize(p).toString().replace('\\','/'))); Files.copy(p,out); out.closeEntry(); }
        }
        assertEquals(3, new PackLoader().load(zip).files().size());
    }
    @Test void rejectsTraversalZip() throws Exception {
        Path zip = temp.resolve("evil.zip");
        try(var out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("../escape")); out.write(1); }
        assertThrows(IOException.class, () -> new PackLoader().load(zip));
        assertFalse(Files.exists(temp.resolve("escape")));
    }
    @Test void rejectsOversizedInflatedEntry() throws Exception {
        Path zip = temp.resolve("large.zip");
        try(var out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry("large")); out.write(new byte[PackLoader.MAX_FILE+1]); }
        assertThrows(IOException.class, () -> new PackLoader().load(zip));
    }
    @Test void rejectsMissingShader() throws Exception { Path p=pack(); Files.delete(p.resolve("shaders/post.fsh")); assertThrows(IOException.class,()->new PackLoader().load(p)); }
    @Test void rejectsUnsupportedVersion() throws Exception { Path p=pack(); Path m=p.resolve("pack.json"); Files.writeString(m, Files.readString(m).replace("formatVersion\":1", "formatVersion\":2")); assertThrows(IOException.class,()->new PackLoader().load(p)); }
    @Test void rejectsFeedbackAndForwardReferences() throws Exception {
        Path p=pack(); Path m=p.resolve("pack.json"); String original=Files.readString(m);
        Files.writeString(m, original.replace("sceneColor", "unknown")); assertThrows(IOException.class,()->new PackLoader().load(p));
        Files.writeString(m, original.replace("\"output\":\"screen\"", "\"output\":\"sceneColor\"")); assertThrows(IOException.class,()->new PackLoader().load(p));
    }
    @Test void scanKeepsInvalidPacksVisible() throws Exception {
        pack(); Files.createDirectory(temp.resolve("broken"));
        var entries=new ShaderPackManager(temp).scan(); assertEquals(2, entries.size()); assertEquals(1,entries.stream().filter(ShaderPackManager.Entry::valid).count());
    }
    @Test void loadsMultipassAndRejectsCyclesAndOversizedTargets() throws Exception {
        assertEquals(3,new PackLoader().load(Path.of("examples/Prismora-Multipass")).manifest().getAsJsonArray("passes").size());
        Path p=pack();Path m=p.resolve("pack.json");String original=Files.readString(m);
        Files.writeString(m,original.replace("\"passes\"", "\"targets\":{\"huge\":{\"width\":99999}},\"passes\""));assertThrows(IOException.class,()->new PackLoader().load(p));
        Files.writeString(m,original.replace("\"sceneColor\"","\"cycle\"").replace("\"screen\"","\"cycle\""));assertThrows(IOException.class,()->new PackLoader().load(p));
    }
    @Test void validatesPaths() { for(String s : new String[]{"/absolute", "a/../b", "C:/file", "a\\b", "a//b"}) assertThrows(IOException.class,()->PackLoader.safePath(s)); }
}
