package com.prismora.render.runtime;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Native camera projection for a section-local vertex stream. */
public final class TerrainCameraFrame {
    private final Matrix4f viewProjection;
    private final Vec3 position;

    public TerrainCameraFrame(Matrix4fc projection, Matrix4fc rotation, Vec3 position) {
        this.viewProjection = new Matrix4f(projection).mul(rotation);
        this.position = position;
    }

    public Matrix4f clipFromSection(SectionPos section) {
        var origin = section.origin();
        return new Matrix4f(viewProjection).translate(
                (float) (origin.getX() - position.x),
                (float) (origin.getY() - position.y),
                (float) (origin.getZ() - position.z));
    }
}
