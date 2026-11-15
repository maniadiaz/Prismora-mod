package com.prismora.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrawBuffersTest {
    @Test
    void retainsOutputLocationsForSparseAndReorderedTargets() throws IOException {
        var expected = List.of(OptionalInt.of(12), OptionalInt.empty(), OptionalInt.of(1), OptionalInt.of(15));
        assertEquals(expected, DrawBuffers.parse(fragment("/* DRAWBUFFERS:CN1F */")));
        assertEquals(expected, DrawBuffers.parse(fragment("/* RENDERTARGETS: 12, N,\n1, 15 */")));
    }

    @Test
    void defaultsToSceneColorAndRejectsInvalidMappings() throws IOException {
        assertEquals(List.of(OptionalInt.of(0)), DrawBuffers.parse(fragment("void main() {}")));
        for (String mapping : List.of("0, 0", "16", "-1", "0,", "")) {
            assertThrows(IOException.class, () -> DrawBuffers.parse(fragment("/* RENDERTARGETS: " + mapping + " */")));
        }
    }

    private static ShaderPreprocessor.Result fragment(String source) {
        return new ShaderPreprocessor.Result(
                List.of(new ShaderPreprocessor.Line("shaders/test.fsh", 1, source)), Map.of(1, "shaders/test.fsh"));
    }
}
