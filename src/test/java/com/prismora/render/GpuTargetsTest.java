package com.prismora.render;
import com.prismora.render.runtime.GpuTargets;
import com.mojang.renderpearl.api.GpuFormat;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GpuTargetsTest {
 @Test void memoryBudgetCountsBothBuffersAtTheirRealFormatPrecision(){var specs=Map.of(0,new GpuTargets.Spec(GpuFormat.RGBA16_FLOAT,0.5,0.5,true,0,0,0,0),12,new GpuTargets.Spec(GpuFormat.RGBA8_SNORM,1,1,false,0,0,0,0));assertEquals(96000,GpuTargets.estimate(specs,100,80));}
 @Test void rejectsInvalidDimensionsFormatsAndLogicalIndices(){assertThrows(IllegalArgumentException.class,()->new GpuTargets.Spec(GpuFormat.D32_FLOAT,1,1,false,0,0,0,0));assertThrows(IllegalArgumentException.class,()->new GpuTargets.Spec(GpuFormat.RGBA8_UNORM,Double.NaN,1,false,0,0,0,0));var spec=new GpuTargets.Spec(GpuFormat.RGBA16_FLOAT,1,1,false,0,0,0,0);assertThrows(IllegalArgumentException.class,()->GpuTargets.estimate(Map.of(16,spec),8,8));assertThrows(IllegalArgumentException.class,()->GpuTargets.estimate(Map.of(0,spec),0,8));}
}
