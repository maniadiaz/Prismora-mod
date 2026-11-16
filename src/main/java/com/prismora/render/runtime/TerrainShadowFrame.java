package com.prismora.render.runtime;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Directional light matrices for a local terrain shadow volume. */
public final class TerrainShadowFrame {
    public enum Body { SUN, MOON }
    private final Body body;
    private final Vector3f towardLight;

    public TerrainShadowFrame(float sunAngle, float moonAngle) {
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
        return new Matrix4f().setOrtho(-16, 16, -16, 16, 54, 10, zeroToOne).mul(viewFromSection());
    }

    public Matrix4f legacyProjection() {
        return new Matrix4f().setOrtho(-16, 16, -16, 16, 10, 54, false);
    }

    public Matrix4f viewFromSection() {
        var center = new Vector3f(8);
        var eye = new Vector3f(towardLight).mul(32f).add(center);
        var up = Math.abs(towardLight.y) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        return new Matrix4f().setLookAt(eye, center, up);
    }

    private static Vector3f celestialDirection(float angle) {
        // Minecraft's sky applies a -90 degree Y rotation before the body's X rotation.
        return new Vector3f(-(float) Math.sin(angle), (float) Math.cos(angle), 0);
    }
}
