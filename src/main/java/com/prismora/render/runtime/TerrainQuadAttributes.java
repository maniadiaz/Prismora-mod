package com.prismora.render.runtime;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Geometric vertex attributes shared by the four vertices of a baked block quad. */
public record TerrainQuadAttributes(float nx, float ny, float nz, float tx, float ty, float tz, float handedness,
                                    float midU, float midV) {
    private static final float MIN_LENGTH_SQUARED = 1e-14f;

    public static TerrainQuadAttributes from(BakedQuad quad) {
        return calculate(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3());
    }

    static TerrainQuadAttributes calculate(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3,
                                           long uv0, long uv1, long uv2, long uv3) {
        var edge1 = new Vector3f(p1).sub(p0);
        var edge2 = new Vector3f(p2).sub(p0);
        var normal = edge1.cross(edge2, new Vector3f());
        long uvEdge1 = uv1;
        long uvEdge2 = uv2;
        if (normal.lengthSquared() <= MIN_LENGTH_SQUARED) {
            edge1.set(p2).sub(p0);
            edge2.set(p3).sub(p0);
            edge1.cross(edge2, normal);
            uvEdge1 = uv2;
            uvEdge2 = uv3;
        }
        if (normal.lengthSquared() <= MIN_LENGTH_SQUARED || !normal.isFinite()) {
            normal.set(0, 1, 0);
        } else {
            normal.normalize();
        }

        float du1 = UVPair.unpackU(uvEdge1) - UVPair.unpackU(uv0);
        float dv1 = UVPair.unpackV(uvEdge1) - UVPair.unpackV(uv0);
        float du2 = UVPair.unpackU(uvEdge2) - UVPair.unpackU(uv0);
        float dv2 = UVPair.unpackV(uvEdge2) - UVPair.unpackV(uv0);
        float determinant = du1 * dv2 - du2 * dv1;

        // Multiplying by the determinant sign avoids an unstable division on tiny atlas UVs.
        float sign = Math.copySign(1f, determinant);
        var tangent = new Vector3f(edge1).mul(dv2 * sign).fma(-dv1 * sign, edge2);
        tangent.fma(-tangent.dot(normal), normal);
        if (determinant == 0 || tangent.lengthSquared() <= MIN_LENGTH_SQUARED || !tangent.isFinite()) {
            var axis = Math.abs(normal.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            tangent.set(axis).fma(-axis.dot(normal), normal).normalize();
            sign = 1f;
        } else {
            tangent.normalize();
        }
        float midU = (UVPair.unpackU(uv0) + UVPair.unpackU(uv1) + UVPair.unpackU(uv2) + UVPair.unpackU(uv3)) * 0.25f;
        float midV = (UVPair.unpackV(uv0) + UVPair.unpackV(uv1) + UVPair.unpackV(uv2) + UVPair.unpackV(uv3)) * 0.25f;
        return new TerrainQuadAttributes(normal.x, normal.y, normal.z, tangent.x, tangent.y, tangent.z, sign, midU, midV);
    }
}
