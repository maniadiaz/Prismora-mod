package com.prismora.client.config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PrismoraConfigTest {
 @TempDir Path temp;
 @Test void selectionSurvivesRestartAndCanBeDisabled() throws Exception {
  Path file=temp.resolve("config/prismora.json"); var first=new PrismoraConfig(file); first.select("demo.zip");
  var next=new PrismoraConfig(file);next.load();assertEquals("demo.zip",next.selected());next.select("");
  var disabled=new PrismoraConfig(file);disabled.load();assertEquals("",disabled.selected());
 }
 @Test void optionsSurviveRestartIndependentlyOfSelection()throws Exception{
  Path file=temp.resolve("options.json");var config=new PrismoraConfig(file);config.select("bloom");config.setOption("bloom","bloom_strength",0.15);config.select("");
  var restarted=new PrismoraConfig(file);restarted.load();assertEquals(0.15,restarted.options("bloom").get("bloom_strength"));assertTrue(restarted.options("other").isEmpty());
 }
 @Test void rejectsDriveRelativeSelection(){assertThrows(IOException.class,()->new PrismoraConfig(temp.resolve("config")).select("C:"));}
 @Test void rejectsExternalSelection() { assertThrows(IOException.class,()->new PrismoraConfig(temp.resolve("config")).select("../pack")); }
}
