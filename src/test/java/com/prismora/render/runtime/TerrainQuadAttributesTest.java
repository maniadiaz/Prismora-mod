package com.prismora.render.runtime;

import net.minecraft.client.model.geom.builders.UVPair;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainQuadAttributesTest {
    private static final Vector3f A = new Vector3f(0, 0, 0);
    private static final Vector3f B = new Vector3f(1, 0, 0);
    private static final Vector3f C = new Vector3f(1, 1, 0);
    private static final Vector3f D = new Vector3f(0, 1, 0);

    @Test
    void computesQuadNormalTangentHandednessAndAtlasCenter() {
        var regular = TerrainQuadAttributes.calculate(A, B, C, D,
                UVPair.pack(0.25f, 0.5f), UVPair.pack(0.5f, 0.5f), UVPair.pack(0.5f, 1), UVPair.pack(0.25f, 1));
        assertEquals(1, regular.nz(), 0.0001);
        assertEquals(1, regular.tx(), 0.0001);
        assertEquals(1, regular.handedness(), 0.0001);
        assertEquals(0.375, regular.midU(), 0.0001);
        assertEquals(0.75, regular.midV(), 0.0001);

        var mirrored = TerrainQuadAttributes.calculate(A, B, C, D,
                UVPair.pack(1, 0), UVPair.pack(0, 0), UVPair.pack(0, 1), UVPair.pack(1, 1));
        assertEquals(-1, mirrored.tx(), 0.0001);
        assertEquals(-1, mirrored.handedness(), 0.0001);
    }

    @Test
    void degenerateUvsHaveAnOrthonormalFallback() {
        long uv = UVPair.pack(0.25f, 0.5f);
        var result = TerrainQuadAttributes.calculate(A, B, C, D, uv, uv, uv, uv);
        var normal = new Vector3f(result.nx(), result.ny(), result.nz());
        var tangent = new Vector3f(result.tx(), result.ty(), result.tz());
        assertEquals(1, normal.length(), 0.0001);
        assertEquals(1, tangent.length(), 0.0001);
        assertEquals(0, normal.dot(tangent), 0.0001);
    }
}
