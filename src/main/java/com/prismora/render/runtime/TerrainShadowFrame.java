package com.prismora.render.runtime;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Directional light matrices for a local terrain shadow volume. */
public final class TerrainShadowFrame {
    public enum Body { SUN, MOON }
    private final Body body;
    private final Vector3f towardLight;
    private final Vector3f center;
    private final float radius;

    public TerrainShadowFrame(float sunAngle, float moonAngle) {
        this(sunAngle, moonAngle, new Vector3f(8), 16f);
    }

    public TerrainShadowFrame(float sunAngle, float moonAngle, Vector3f center, float radius) {
        if (!center.isFinite() || !Float.isFinite(radius) || radius < 16f) {
            throw new IllegalArgumentException("Invalid shadow volume");
        }
        this.center = new Vector3f(center);
        this.radius = radius;
        var sun = celestialDirection(sunAngle);
        var moon = celestialDirection(moonAngle);
        body = sun.y >= moon.y ? Body.SUN : Body.MOON;
        towardLight = body == Body.SUN ? sun : moon;
    }

    public Body body() {
        return body;
    }

    public Vector3f direction() {
        return new Vector3f(towardLight);
    }

    public Matrix4f clipFromSection(boolean zeroToOne) {
        return new Matrix4f().setOrtho(-radius, radius, -radius, radius, radius * 3.375f, radius * 0.625f, zeroToOne).mul(viewFromSection());
    }

    public Matrix4f legacyProjection() {
        return new Matrix4f().setOrtho(-radius, radius, -radius, radius, radius * 0.625f, radius * 3.375f, false);
    }

    public Matrix4f viewFromSection() {

        var eye = new Vector3f(towardLight).mul(radius * 2f).add(center);
        var up = Math.abs(towardLight.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        return new Matrix4f().setLookAt(eye, center, up);
    }

    private static Vector3f celestialDirection(float angle) {
        // Minecraft's sky applies a -90 degree Y rotation before the body's X rotation.
        return new Vector3f(-(float) Math.sin(angle), (float) Math.cos(angle), 0);
    }
}
