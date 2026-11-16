package com.prismora.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TerrainShaderTranslatorTest {
    @Test
    void assignsEachVaryingAnExplicitLocationAndPreservesTerrainUniformDefaults() throws IOException {
        var vertex = source("test.vsh", """
                attribute vec4 mc_Entity;
                flat out vec3 binormal, tangent;
                uniform float cloudHeight = 192.0;
                void main() { gl_Position = gl_ProjectionMatrix * gl_Vertex; }
                """);
        var fragment = source("test.fsh", """
                /* RENDERTARGETS: 0,6,4 */
                flat in vec3 tangent, binormal;
                uniform sampler2D atlas;
                uniform sampler2D unusedAtlas;
                uniform float cloudHeight = 192.0;
                void main() { gl_FragData[2] = vec4(tangent + binormal, 1.0) * texture(atlas, vec2(0)); }
                """);
        var translated = new TerrainShaderTranslator().translate(vertex, fragment, true);
        assertTrue(translated.vertex().contains("layout(location=0) flat out vec3 binormal;"));
        assertTrue(translated.fragment().contains("layout(location=1) flat in vec3 tangent;"));
        assertTrue(translated.vertex().contains("prismoraProjection * vec4(Position,1.0)"));
        assertTrue(translated.vertex().contains("(gl_Position.w-gl_Position.z)*0.5"));
        assertTrue(translated.fragment().contains("prismoraFragData[2]"));
        assertEquals("192.0", translated.uniformDefaults().get("cloudHeight"));
        assertEquals("float", translated.uniformTypes().get("cloudHeight"));
        assertEquals(List.of(OptionalInt.of(0), OptionalInt.of(6), OptionalInt.of(4)), translated.outputSlots());
        assertEquals(java.util.Set.of("atlas"), translated.samplers());
    }

    @Test
    void rejectsMismatchedVertexToFragmentInterfaces() {
        var vertex = source("test.vsh", "out vec3 basis;");
        var fragment = source("test.fsh", "in vec2 basis;");
        assertThrows(IOException.class, () -> new TerrainShaderTranslator().translate(vertex, fragment, false));
    }

    private static ShaderPreprocessor.Result source(String path, String text) {
        return new ShaderPreprocessor.Result(List.of(new ShaderPreprocessor.Line(path, 1, text)), Map.of(1, path));
    }
}
