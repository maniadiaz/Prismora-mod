package com.prismora.render.runtime;

/** Comparison filtering for native reverse-Z depth on RenderPearl regular samplers. */
public final class ShadowDepthSampling {
    public static final String GLSL = """
            float prismoraShadowTap(sampler2D depthMap, ivec2 p, ivec2 size, float reverseReference) {
                float depth = texelFetch(depthMap, clamp(p, ivec2(0), size - 1), 0).r;
                return step(depth, reverseReference);
            }
            vec4 prismoraShadow2D(sampler2D depthMap, vec3 coordinates) {
                ivec2 size = textureSize(depthMap, 0);
                vec2 texel = coordinates.xy * vec2(size) - vec2(0.5);
                ivec2 p = ivec2(floor(texel));
                vec2 t = fract(texel);
                float reference = 1.0 - coordinates.z;
                float a = prismoraShadowTap(depthMap, p, size, reference);
                float b = prismoraShadowTap(depthMap, p + ivec2(1, 0), size, reference);
                float c = prismoraShadowTap(depthMap, p + ivec2(0, 1), size, reference);
                float d = prismoraShadowTap(depthMap, p + ivec2(1, 1), size, reference);
                return vec4(vec3(mix(mix(a, b, t.x), mix(c, d, t.x), t.y)), 1.0);
            }
            """;

    private ShadowDepthSampling() {}
}
