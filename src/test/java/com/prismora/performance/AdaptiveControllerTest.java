package com.prismora.performance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AdaptiveControllerTest {
 private AdaptiveController.Action feed(AdaptiveController c,PerformanceSettings s,double frame,double gpu,int count){var action=AdaptiveController.Action.NONE;for(int i=0;i<count;i++){var next=c.observe(frame,gpu,100000+i,s);if(next!=AdaptiveController.Action.NONE)action=next;}return action;}
 @Test void requiresSustainedGpuPressure(){var c=new AdaptiveController(10,0,2,0);var s=PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY).custom(0.75,true,true,30,true);assertEquals(AdaptiveController.Action.NONE,feed(c,s,40,38,10));assertEquals(AdaptiveController.Action.REDUCE,feed(c,s,40,38,10));}
 @Test void minimumMaySuspendButOnlyWithOptIn(){var c=new AdaptiveController(10,0,1,0);var s=PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY);assertEquals(AdaptiveController.Action.SUSPEND,feed(c,s,45,40,10));assertEquals(AdaptiveController.Action.NONE,feed(c,s.custom(0.5,false,true,30,false),45,40,10));}
 @Test void cpuAndVsyncLimitsDoNotReduceQuality(){var c=new AdaptiveController(10,0,1,0);var s=PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY);assertEquals(AdaptiveController.Action.NONE,feed(c,s,60,2,30));assertTrue(c.metrics().cpuBound());}
 @Test void warmupAndCooldownBlockChanges(){var c=new AdaptiveController(10,20,1,1000000);var s=PerformanceSettings.preset(PerformanceSettings.Profile.ENTRY);c.reset(100000);assertEquals(AdaptiveController.Action.NONE,feed(c,s,45,40,50));}
 @Test void rejectsInvalidSamplesAndDisabledAdaptation(){var c=new AdaptiveController(10,0,1,0);var s=PerformanceSettings.preset(PerformanceSettings.Profile.HIGH);assertEquals(AdaptiveController.Action.NONE,feed(c,s,Double.NaN,40,100));assertNull(c.metrics());assertEquals(AdaptiveController.Action.NONE,feed(c,s,45,40,20));}
}
