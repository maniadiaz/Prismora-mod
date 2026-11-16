package com.prismora.render.runtime;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Extended vertex data for a block model face. */
public record TerrainVertexData(int materialId, TerrainQuadAttributes attributes, List<Corner> corners) {
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("mc_Entity", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("at_tangent", GpuFormat.RGBA32_FLOAT)
            .addAttribute("mc_midTexCoord", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    public record Corner(float x, float y, float z, float u, float v, int argb, int light) {}

    public TerrainVertexData {
        corners = List.copyOf(corners);
        if (corners.size() != 4) {
            throw new IllegalArgumentException("Terrain quad requires four corners");
        }
    }

    public static TerrainVertexData from(int materialId, float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        var corners = new ArrayList<Corner>(4);
        for (int index = 0; index < 4; index++) {
            var position = quad.position(index);
            long uv = quad.packedUV(index);
            corners.add(new Corner(x + position.x(), y + position.y(), z + position.z(), UVPair.unpackU(uv), UVPair.unpackV(uv), instance.getColor(index),
                    instance.getLightCoordsWithEmission(index, quad.materialInfo().lightEmission())));
        }
        return new TerrainVertexData(materialId, TerrainQuadAttributes.from(quad), corners);
    }

    public TerrainVertexData offset(float x, float y, float z) {
        return new TerrainVertexData(materialId, attributes, corners.stream()
                .map(corner -> new Corner(corner.x() + x, corner.y() + y, corner.z() + z,
                        corner.u(), corner.v(), corner.argb(), corner.light()))
                .toList());
    }

    public void writeVertex(ByteBuffer buffer, int index) {
        var point = corners.get(index);
        buffer.putFloat(point.x()).putFloat(point.y()).putFloat(point.z());
        buffer.putFloat(point.u()).putFloat(point.v());
        buffer.putFloat(materialId).putFloat(0f).putFloat(0f).putFloat(1f);
        buffer.putFloat(attributes.nx()).putFloat(attributes.ny()).putFloat(attributes.nz());
        buffer.putFloat(attributes.tx()).putFloat(attributes.ty()).putFloat(attributes.tz()).putFloat(attributes.handedness());
        buffer.putFloat(attributes.midU()).putFloat(attributes.midV()).putFloat(0f).putFloat(1f);
        int argb = point.argb();
        buffer.put((byte) (argb >>> 16)).put((byte) (argb >>> 8)).put((byte) argb).put((byte) (argb >>> 24));
        int light = point.light();
        buffer.putShort((short) light).putShort((short) (light >>> 16));
    }
}
