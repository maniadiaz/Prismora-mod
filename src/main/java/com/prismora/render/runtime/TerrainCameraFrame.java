package com.prismora.render.runtime;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Native camera projection for a section-local vertex stream. */
public final class TerrainCameraFrame {
    private final Matrix4f projection;
    private final Matrix4f rotation;
    private final Vec3 position;

    public TerrainCameraFrame(Matrix4fc projection, Matrix4fc rotation, Vec3 position) {
        this.projection = new Matrix4f(projection);
        this.rotation = new Matrix4f(rotation);
        this.position = position;
    }

    public Matrix4f clipFromSection(SectionPos section) {
        return new Matrix4f(projection).mul(modelViewFromSection(section));
    }

    public Matrix4f legacyProjection(boolean zeroToOne) {
        return new Matrix4f().m22(zeroToOne ? -2f : -1f).m32(zeroToOne ? 1f : 0f).mul(projection);
    }

    public Matrix4f viewRotation() {
        return new Matrix4f(rotation);
    }

    public Vec3 position() {
        return position;
    }

    public Matrix4f modelViewFromSection(SectionPos section) {
        var origin = section.origin();
        return new Matrix4f(rotation).translate(
                (float) (origin.getX() - position.x),
                (float) (origin.getY() - position.y),
                (float) (origin.getZ() - position.z));
    }
}
