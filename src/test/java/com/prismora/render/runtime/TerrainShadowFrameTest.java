package com.prismora.render.runtime;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainShadowFrameTest {
    @Test
    void switchesTheSelectedCelestialBodyAtNight() {
        assertEquals(TerrainShadowFrame.Body.SUN, new TerrainShadowFrame(0f, (float) Math.PI).body());
        assertEquals(TerrainShadowFrame.Body.MOON, new TerrainShadowFrame((float) Math.PI, 0f).body());
    }

    @Test
    void closerCasterHasGreaterNativeDepthInBothClipConventions() {
        var light = new TerrainShadowFrame(0f, (float) Math.PI);
        for (boolean zeroToOne : new boolean[]{false, true}) {
            var projection = light.clipFromSection(zeroToOne);
            var near = projection.transform(new Vector4f(8, 16, 8, 1));
            var far = projection.transform(new Vector4f(8, 0, 8, 1));
            assertEquals(0, near.x, 0.0001);
            assertEquals(0, near.y, 0.0001);
            assertTrue(near.z > far.z);
            assertTrue(near.z <= 1f && far.z >= (zeroToOne ? 0f : -1f));
        }
    }
}
