package com.prismora.render.runtime;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

/** Scene values in the conventions used by the terrain shaderpack contract. */
public record TerrainSceneUniforms(int width, int height, float far, int worldTime, int worldDay,
                                   int moonPhase, float rainStrength, int atlasWidth, int atlasHeight,
                                   Vector3f fogColor, Vector3f skyColor) {
    public static TerrainSceneUniforms capture(Minecraft client, int atlasWidth, int atlasHeight) {
        var state = client.gameRenderer.gameRenderState().levelRenderState;
        var target = client.gameRenderer.mainRenderTarget();
        long clock = client.level.getOverworldClockTime();
        var fog = state.cameraRenderState.fogData.color;
        return new TerrainSceneUniforms(target.width, target.height, state.cameraRenderState.depthFar,
                Math.floorMod(clock, 24000), (int) Math.floorDiv(clock, 24000), state.skyRenderState.moonPhase.index(),
                client.level.getRainLevel(state.worldPartialTicks), atlasWidth, atlasHeight,
                new Vector3f(fog.x(), fog.y(), fog.z()), new Vector3f(state.skyRenderState.skyColor));
    }

    public float aspectRatio() {
        return (float) width / height;
    }

    public float near() {
        return Camera.PROJECTION_Z_NEAR;
    }

    public void write(TerrainUniformLayout layout, ByteBuffer target) {
        layout.putFloat(target, "viewWidth", width);
        layout.putFloat(target, "viewHeight", height);
        layout.putFloat(target, "aspectRatio", aspectRatio());
        layout.putFloat(target, "near", near());
        layout.putFloat(target, "far", far);
        layout.putInt(target, "worldTime", worldTime);
        layout.putInt(target, "worldDay", worldDay);
        layout.putInt(target, "moonPhase", moonPhase);
        layout.putFloat(target, "rainStrength", rainStrength);
        layout.putIVec2(target, "atlasSize", atlasWidth, atlasHeight);
        layout.putVec3(target, "fogColor", fogColor.x, fogColor.y, fogColor.z);
        layout.putVec3(target, "skyColor", skyColor.x, skyColor.y, skyColor.z);
    }
}
