package com.prismora.render.runtime;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainCameraFrameTest {
    @Test
    void subtractsTheCameraPositionBeforeRoundingToFloat() {
        var section = SectionPos.of(1_000_000, 4, -1_000_000);
        var origin = section.origin();
        var camera = new Vec3(origin.getX() + 0.25, origin.getY() + 1.5, origin.getZ() - 0.5);
        var rotation = new Matrix4f().rotateY(0.7f);
        var projection = new Matrix4f().scale(0.5f, 0.75f, 0.25f);
        var corner = new Vector4f(1f, 2f, 3f, 1f);
        var expected = projection.transform(rotation.transform(new Vector4f(0.75f, 0.5f, 3.5f, 1f)));
        var actual = new TerrainCameraFrame(projection, rotation, camera).clipFromSection(section).transform(corner);
        assertEquals(expected.x, actual.x, 0.00001f);
        assertEquals(expected.y, actual.y, 0.00001f);
        assertEquals(expected.z, actual.z, 0.00001f);
        assertEquals(expected.w, actual.w, 0.00001f);
    }
}
