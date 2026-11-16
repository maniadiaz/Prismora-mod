package com.prismora.client;

import com.prismora.render.runtime.GpuProgram;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Owns the real pack pipelines across all poses of the isolated preview. */
final class ComplementaryPreviewPipelines {
    private static final Map<String, GpuProgram> programs = new LinkedHashMap<>();

    private ComplementaryPreviewPipelines() {}

    static GpuProgram get(String name, Supplier<GpuProgram> compile) {
        return programs.computeIfAbsent(name, unused -> compile.get());
    }

    static int size() {
        return programs.size();
    }

    static void close() {
        programs.values().forEach(GpuProgram::close);
        programs.clear();
    }
}
