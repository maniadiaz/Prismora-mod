package com.prismora.performance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import com.prismora.client.config.PrismoraConfig;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceSettingsTest {
 @TempDir Path root;
 @Test void entryHasQuarterPixelBudgetAndNoBloom(){var p=PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY);assertEquals(518400,p.estimatedPixels(1920,1080));assertFalse(p.bloom());assertEquals(30,p.targetFps());}
 @Test void rejectsNonfiniteAndInvalidBudgets(){assertThrows(IllegalArgumentException.class,()->new PerformanceSettings(PerformanceSettings.Profile.CUSTOM,Double.NaN,true,false,30,true));assertThrows(IllegalArgumentException.class,()->new PerformanceSettings(PerformanceSettings.Profile.LOW,0.5,true,false,0,true));}
 @Test void settingsPersistAndOldConfigMigrates()throws Exception{
  var file=root.resolve("config.json");Files.writeString(file,"{\"selected\":\"\"}");var config=new PrismoraConfig(file);config.load();assertEquals(PerformanceSettings.Profile.MEDIUM,config.performance().profile());
  config.performance(PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY));var restarted=new PrismoraConfig(file);restarted.load();assertEquals(config.performance(),restarted.performance());
 }
 @Test void reductionsAreBounded(){var p=PerformanceSettings.preset(PerformanceSettings.Profile.HIGH);for(int i=0;i<20;i++)p=p.reduced();assertTrue(p.minimum());assertEquals(0.5,p.scale());}
}
