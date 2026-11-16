package com.prismora.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.prismora.Prismora;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Owns the size-dependent resources of the Complementary diagnostic world. */
final class ComplementaryFrameResources implements AutoCloseable {
    private static ComplementaryFrameResources current;
    private final int windowWidth;
    private final int windowHeight;
    private final LinkedHashMap<String, AutoCloseable> resources = new LinkedHashMap<>();

    private ComplementaryFrameResources(RenderTarget target) {
        windowWidth = target.width;
        windowHeight = target.height;
    }

    static ComplementaryFrameResources forTarget(RenderTarget target) {
        if (current != null && current.windowWidth == target.width && current.windowHeight == target.height) {
            return current;
        }
        if (current != null) {
            release();
            ComplementaryPostDrawProbe.invalidateHistory();
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_FRAME_RESIZE: {}x{}", target.width, target.height);
        }
        current = new ComplementaryFrameResources(target);
        return current;
    }

    int width() {
        return Math.max(1, windowWidth / 5);
    }

    int height() {
        return Math.max(1, windowHeight / 5);
    }

    <T extends AutoCloseable> T get(String label, Class<T> type, Supplier<T> create) {
        return type.cast(resources.computeIfAbsent(label, unused -> create.get()));
    }

    int size() {
        return resources.size();
    }

    static boolean active() {
        return current != null;
    }

    static void release() {
        if (current != null) {
            var previous = current;
            current = null;
            previous.close();
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (var resource : resources.sequencedValues().reversed()) {
            try {
                resource.close();
            } catch (Exception error) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to release Complementary frame resources", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        resources.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
