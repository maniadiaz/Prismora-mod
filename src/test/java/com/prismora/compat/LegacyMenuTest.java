package com.prismora.compat;
import com.prismora.client.config.PrismoraConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyMenuTest {
 @TempDir Path root;
 @Test void sessionSharesDraftAndResetDoesNotChangeOtherPack()throws Exception{var p=LegacyPack.inspect("demo",LegacyPackTest.fixture());var session=new LegacyMenuSession(p,Map.of());session.profile("LOW");assertEquals("false",session.value("BLOOM"));assertEquals("LOW",session.matchedProfile());session.reset();assertEquals("true",session.value("BLOOM"));assertEquals("1.0",session.value("EXPOSURE"));}
 @Test void presetsRoundtripAndRejectOtherPack()throws Exception{var p=LegacyPack.inspect("demo",LegacyPackTest.fixture());var session=new LegacyMenuSession(p,Map.of());session.profile("HIGH");var file=session.exportPreset(root);session.reset();session.importPreset(root);assertEquals("2.0",session.value("EXPOSURE"));String text=Files.readString(file);Files.writeString(file,text.replace("\"demo\"","\"another\""));assertThrows(java.io.IOException.class,()->session.importPreset(root));}
 @Test void legacyValuesSurviveRestartByPackAndKeepCase()throws Exception{Path file=root.resolve("config.json");var c=new PrismoraConfig(file);c.legacyOptions("a.zip",Map.of("BLOOM","true","EXPOSURE","2.0"));c.legacyOptions("b.zip",Map.of("BLOOM","false"));var next=new PrismoraConfig(file);next.load();assertEquals("true",next.legacyOptions("a.zip").get("BLOOM"));assertEquals("false",next.legacyOptions("b.zip").get("BLOOM"));}
}
