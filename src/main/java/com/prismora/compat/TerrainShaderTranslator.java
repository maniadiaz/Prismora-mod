package com.prismora.compat;

import com.prismora.render.runtime.TerrainUniformLayout;
import com.prismora.render.runtime.ShadowDepthSampling;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lowers a terrain program to the graphics interface used by ShaderC. */
public final class TerrainShaderTranslator {
    public static final String UNIFORM_BLOCK = "PrismoraTerrainUniforms";
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final Pattern INTERFACE = Pattern.compile("(?m)^[ \\t]*(?:(flat|smooth|noperspective)\\s+)?(in|out|varying)\\s+(\\w+)\\s+([\\w, \\t]+)\\s*;");
    private static final Pattern UNIFORM = Pattern.compile("(?m)^[ \\t]*uniform\\s+(\\w+)\\s+([^;{}]+);");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?m)^[ \\t]*attribute\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Map<String, String> BUILTINS = Map.ofEntries(
            Map.entry("gl_Vertex", "vec4(Position,1.0)"),
            Map.entry("gl_VertexID", "gl_VertexIndex"),
            Map.entry("ftransform", "prismoraTransform"),
            Map.entry("gl_Color", "Color"),
            Map.entry("gl_Normal", "Normal"),
            Map.entry("gl_MultiTexCoord0", "vec4(UV0,0.0,1.0)"),
            Map.entry("gl_MultiTexCoord1", "vec4(vec2(UV2),0.0,1.0)"),
            Map.entry("gl_ProjectionMatrix", "prismoraProjection"),
            Map.entry("gl_ModelViewMatrix", "prismoraModelView"),
            Map.entry("gl_NormalMatrix", "prismoraNormalMatrix"),
            Map.entry("gl_TextureMatrix", "prismoraTextureMatrix"),
            Map.entry("gl_FragData", "prismoraFragData"),
            Map.entry("texture2D", "texture"),
            Map.entry("texture2DLod", "textureLod"),
            Map.entry("texture2DGradARB", "textureGrad"),
            Map.entry("shadow2D", "prismoraShadow2D"));

    public record Program(String vertex, String fragment, Set<String> samplers, Map<String, String> uniformTypes, Map<String, String> uniformDefaults,
                          List<OptionalInt> outputSlots, Map<Integer, String> sourceFiles) {}

    private record Varying(int location, String type, String qualifier) {}
    private record Edit(int start, int end, String replacement) {}

    public Program translate(ShaderPreprocessor.Result vertex, ShaderPreprocessor.Result fragment, boolean zeroToOne)
            throws IOException {
        var uniforms = new LinkedHashMap<String, String>();
        uniforms.put("prismoraTextureMatrix", "mat4[2]");
        uniforms.put("prismoraProjection", "mat4");
        uniforms.put("prismoraModelView", "mat4");
        uniforms.put("prismoraNormalMatrix", "mat3");
        var defaults = new LinkedHashMap<String, String>();
        var samplers = new LinkedHashSet<String>();
        var varyings = new LinkedHashMap<String, Varying>();
        var slots = DrawBuffers.parse(fragment);
        var vs = stage(vertex.source(), true, uniforms, defaults, samplers, varyings);
        var fs = stage(fragment.source(), false, uniforms, defaults, samplers, varyings);
        String block = new TerrainUniformLayout(uniforms).declaration(UNIFORM_BLOCK);

        String vertexInputs = """
                layout(location=0) in vec3 Position;
                layout(location=1) in vec2 UV0;
                layout(location=2) in vec4 mc_Entity;
                layout(location=3) in vec3 Normal;
                layout(location=4) in vec4 at_tangent;
                layout(location=5) in vec4 mc_midTexCoord;
                layout(location=6) in vec4 Color;
                layout(location=7) in ivec2 UV2;
                vec4 prismoraTransform(){return prismoraProjection*prismoraModelView*vec4(Position,1.0);}
                #define main prismoraLegacyTerrainMain
                """;
        String vertexEnd = "\n#undef main\nvoid main(){prismoraLegacyTerrainMain(); gl_Position.z = "
                + (zeroToOne ? "(gl_Position.w-gl_Position.z)*0.5" : "-gl_Position.z") + ";}\n";
        String fragmentInputs = "layout(location=0) out vec4 prismoraFragData[" + slots.size() + "];\n"
                + ShadowDepthSampling.GLSL;
        var files = new LinkedHashMap<>(vertex.sourceFiles());
        files.putAll(fragment.sourceFiles());
        return new Program(HEADER + block + vertexInputs + vs + vertexEnd, HEADER + block + fragmentInputs + fs,
                Collections.unmodifiableSet(samplers), Collections.unmodifiableMap(uniforms), Map.copyOf(defaults), slots, Map.copyOf(files));
    }

    private static String stage(String source, boolean vertex, Map<String, String> uniforms, Map<String, String> defaults, Set<String> samplers,
                                Map<String, Varying> varyings) throws IOException {
        String masked = LegacyTranslator.mask(source);
        var edits = new ArrayList<Edit>();
        var uniform = UNIFORM.matcher(masked);
        while (uniform.find()) {
            String type = uniform.group(1);
            for (String declaration : uniform.group(2).split(",")) {
                String[] parts = declaration.split("=", 2);
                String name = parts[0].strip();
                if (!name.matches("[A-Za-z_]\\w*")) {
                    throw new IOException("Unsupported terrain uniform declaration: " + declaration);
                }
                if (type.startsWith("sampler")) {
                    samplers.add(name);
                } else {
                    if (parts.length == 2) defaults.put(name, parts[1].strip());
                    String previous = uniforms.putIfAbsent(name, type);
                    if (previous != null && !previous.equals(type)) {
                        throw new IOException("Terrain uniform has inconsistent type: " + name);
                    }
                }
            }
            if (type.equals("sampler2DShadow")) {
                edits.add(new Edit(uniform.start(1), uniform.end(1), "sampler2D"));
            } else if (!type.startsWith("sampler")) {
                edits.add(blank(source, uniform));
            }
        }
        var attribute = ATTRIBUTE.matcher(masked);
        while (attribute.find()) {
            if (!vertex || !attribute.group(1).equals("vec4")
                    || !Set.of("mc_Entity", "mc_midTexCoord", "at_tangent").contains(attribute.group(2))) {
                throw new IOException("Unsupported terrain attribute: " + attribute.group(2));
            }
            edits.add(blank(source, attribute));
        }
        var varying = INTERFACE.matcher(masked);
        while (varying.find()) {
            String direction = varying.group(2);
            if (!direction.equals("varying") && !direction.equals(vertex ? "out" : "in")) {
                continue;
            }
            String qualifier = varying.group(1) == null ? "" : varying.group(1) + " ";
            String type = varying.group(3);
            var declarations = new ArrayList<String>();
            for (String declaration : varying.group(4).split(",")) {
                String[] parts = declaration.split("=", 2);
                String name = parts[0].strip();
                if (vertex && !varyings.containsKey(name)) {
                    int location = varyings.values().stream().mapToInt(value -> width(value.type())).sum();
                    varyings.put(name, new Varying(location, type, qualifier));
                }
                var binding = varyings.get(name);
                if (binding == null || !binding.type().equals(type) || !binding.qualifier().equals(qualifier)) {
                    throw new IOException("Terrain varying does not match between stages: " + name);
                }
                declarations.add("layout(location=" + binding.location() + ") " + qualifier + (vertex ? "out " : "in ") + type + " " + name + ";");
            }
            edits.add(new Edit(varying.start(), varying.end(), String.join(" ", declarations)));
        }

        var word = Pattern.compile("\\b(?:gl_\\w+|texture2D\\w*|shadow2D|ftransform)\\b").matcher(masked);
        while (word.find()) {
            String replacement = BUILTINS.get(word.group());
            if (replacement != null) {
                edits.add(new Edit(word.start(), word.end(), replacement));
            }
        }
        var result = new StringBuilder(source);
        edits.sort(java.util.Comparator.comparingInt(Edit::start).reversed());
        for (var edit : edits) {
            result.replace(edit.start(), edit.end(), edit.replacement());
        }
        return result.toString().replaceAll("(?m)^[ \\t]*#extension[^\\r\\n]*", "");
    }

    private static int width(String type) {
        return type.matches("mat[234]") ? type.charAt(3) - '0' : 1;
    }

    private static Edit blank(String source, Matcher match) {
        String original = source.substring(match.start(), match.end());
        return new Edit(match.start(), match.end(), original.replaceAll("[^\\r\\n]", " "));
    }
}
