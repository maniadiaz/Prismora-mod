package com.prismora.compat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Maps a fragment output location to its logical colortex index. */
final class DrawBuffers {
    private static final Pattern DIRECTIVE = Pattern.compile("/\\*\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:\\s*([^*]*?)\\s*\\*/");

    private DrawBuffers() {}

    static List<OptionalInt> parse(ShaderPreprocessor.Result fragment) throws IOException {
        var source = new StringBuilder();
        for (var line : fragment.lines()) {
            source.append(line.text()).append('\n');
        }
        var matcher = DIRECTIVE.matcher(source);
        List<OptionalInt> result = List.of(OptionalInt.of(0));
        while (matcher.find()) {
            result = parseSlots(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    private static List<OptionalInt> parseSlots(String directive, String payload) throws IOException {
        String[] slots = directive.equals("DRAWBUFFERS") ? payload.split("") : payload.split(",", -1);
        if (payload.isBlank() || slots.length > 16) {
            throw new IOException("Invalid " + directive + " output count: " + payload);
        }
        var targets = new ArrayList<OptionalInt>(slots.length);
        var used = new HashSet<Integer>();
        for (String slot : slots) {
            String value = slot.strip();
            if (value.equals("N")) {
                targets.add(OptionalInt.empty());
                continue;
            }
            int target;
            try {
                target = Integer.parseInt(value, directive.equals("DRAWBUFFERS") ? 16 : 10);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid " + directive + " output: " + value, invalid);
            }
            if (target < 0 || target > 15 || !used.add(target)) {
                throw new IOException("Invalid or repeated colortex target: " + target);
            }
            targets.add(OptionalInt.of(target));
        }
        return List.copyOf(targets);
    }
}
