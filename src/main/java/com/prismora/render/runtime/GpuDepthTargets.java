package com.prismora.render.runtime;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native reverse-Z depth for the live scene and independent snapshots at world pass boundaries. */
public final class GpuDepthTargets implements AutoCloseable {
    public enum Stage {
        SCENE, BEFORE_TRANSLUCENCY, BEFORE_HAND
    }

    private final GpuDevice device;
    private final int width;
    private final int height;
    private final List<GpuTexture> textures = new ArrayList<>();
    private final List<GpuTextureView> views = new ArrayList<>();
    private boolean closed;

    public GpuDepthTargets(GpuDevice device, int width, int height, long budget) {
        long bytes = estimate(width, height);
        if (bytes > budget) {
            throw new IllegalArgumentException("Depth attachment budget exceeded: " + bytes + " > " + budget);
        }
        this.device = Objects.requireNonNull(device);
        this.width = width;
        this.height = height;
        int limit = device.getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.D32_FLOAT);
        if (width > limit || height > limit) {
            throw new IllegalArgumentException("Depth attachments exceed device size limit");
        }
        try {
            int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
                    | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
            for (Stage stage : Stage.values()) {
                var texture = device.createTexture("Prismora depth " + stage, usage, GpuFormat.D32_FLOAT, width, height, 1, 1);
                textures.add(texture);
                views.add(device.createTextureView(texture));
            }
            beginFrame();
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public static long estimate(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Invalid depth dimensions");
        }
        return Math.multiplyExact((long) width * height, (long) Stage.values().length * GpuFormat.D32_FLOAT.blockSize());
    }

    public GpuTextureView view(Stage stage) {
        if (closed) {
            throw new IllegalStateException("Depth targets are closed");
        }
        return views.get(stage.ordinal());
    }

    public void beginFrame() {
        clearScene();
        snapshot(Stage.BEFORE_TRANSLUCENCY);
        snapshot(Stage.BEFORE_HAND);
    }

    public void snapshot(Stage stage) {
        if (stage == Stage.SCENE) {
            throw new IllegalArgumentException("The live scene cannot be a snapshot destination");
        }
        var source = view(Stage.SCENE).texture();
        var destination = view(stage).texture();
        device.createCommandEncoder().copyTextureToTexture(source, destination, 0, 0, 0, 0, 0, width, height);
    }

    private void clearScene() {
        device.createCommandEncoder().clearDepthTexture(view(Stage.SCENE).texture(), 0.0);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (var view : views.reversed()) {
                view.close();
            }
        } finally {
            for (var texture : textures.reversed()) {
                texture.close();
            }
        }
    }
}
