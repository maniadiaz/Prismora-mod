package com.prismora.render.runtime;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/** std140 offsets for the terrain compiler's scalar, vector and matrix declarations. */
public final class TerrainUniformLayout {
    private record Field(String type, int offset) {}
    private record Shape(int alignment, int size) {}
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final int bytes;

    public TerrainUniformLayout(Map<String, String> types) {
        int cursor = 0;
        for (var member : types.entrySet()) {
            var shape = shape(member.getValue());
            cursor = align(cursor, shape.alignment());
            fields.put(member.getKey(), new Field(member.getValue(), cursor));
            cursor = Math.addExact(cursor, shape.size());
        }
        bytes = align(cursor, 16);
    }

    public String declaration(String block) {
        var text = new StringBuilder("layout(std140) uniform ").append(block).append(" {\n");
        fields.forEach((name, field) -> {
            if (field.type().equals("mat4[2]")) {
                text.append("mat4 ").append(name).append("[2];\n");
            } else {
                text.append(field.type()).append(' ').append(name).append(";\n");
            }
        });
        return text.append("};\n").toString();
    }

    public ByteBuffer allocate() {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    public void put(ByteBuffer target, String name, Matrix4fc value) {
        value.get(field(name, "mat4").offset(), target);
    }

    public void put(ByteBuffer target, String name, Matrix3fc value) {
        int base = field(name, "mat3").offset();
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                target.putFloat(base + column * 16 + row * Float.BYTES, value.get(column, row));
            }
        }
    }

    public void putMatrixPair(ByteBuffer target, String name, Matrix4fc first, Matrix4fc second) {
        int base = field(name, "mat4[2]").offset();
        first.get(base, target);
        second.get(base + 64, target);
    }

    public void putFloat(ByteBuffer target, String name, float value) {
        target.putFloat(field(name, "float").offset(), value);
    }

    public void putInt(ByteBuffer target, String name, int value) {
        target.putInt(field(name, "int").offset(), value);
    }

    public void putIVec2(ByteBuffer target, String name, int x, int y) {
        int base = field(name, "ivec2").offset();
        target.putInt(base, x).putInt(base + 4, y);
    }

    public void putVec4(ByteBuffer target, String name, float x, float y, float z, float w) {
        int base = field(name, "vec4").offset();
        target.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, z).putFloat(base + 12, w);
    }

    public void putVec3(ByteBuffer target, String name, float x, float y, float z) {
        int base = field(name, "vec3").offset();
        target.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, z);
    }

    private Field field(String name, String type) {
        var field = fields.get(name);
        if (field == null || !field.type().equals(type)) {
            throw new IllegalArgumentException("Terrain uniform " + name + " must be " + type);
        }
        return field;
    }

    private static Shape shape(String type) {
        return switch (type) {
            case "bool", "int", "uint", "float" -> new Shape(4, 4);
            case "vec2", "ivec2", "uvec2", "bvec2" -> new Shape(8, 8);
            case "vec3", "ivec3", "uvec3", "bvec3" -> new Shape(16, 12);
            case "vec4", "ivec4", "uvec4", "bvec4" -> new Shape(16, 16);
            case "mat2" -> new Shape(16, 32);
            case "mat3" -> new Shape(16, 48);
            case "mat4" -> new Shape(16, 64);
            case "mat4[2]" -> new Shape(16, 128);
            default -> throw new IllegalArgumentException("Unsupported std140 terrain type: " + type);
        };
    }

    private static int align(int value, int alignment) {
        return Math.addExact(value, alignment - 1) & -alignment;
    }
}
