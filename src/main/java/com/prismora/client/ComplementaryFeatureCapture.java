package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.prismora.render.runtime.TerrainVertexData;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Geometry emitted by Minecraft's world model and item feature renderers for a diagnostic frame. */
public final class ComplementaryFeatureCapture {
    public enum Kind { MODEL, ITEM, PARTICLE, HAND }
    private record Key(Kind kind, GpuTextureView texture) {}
    record Draw(Kind kind, GpuTextureView texture, GpuBuffer vertices, int indices) implements AutoCloseable {
        @Override public void close() {
            vertices.close();
        }
    }

    private static final LinkedHashMap<Key, List<TerrainVertexData>> geometry = new LinkedHashMap<>();
    private static final List<TerrainVertexData.Corner> cubeCorners = new ArrayList<>(4);
    private static Key model;
    private static Key particle;
    private static boolean world;
    private static boolean hand;

    private ComplementaryFeatureCapture() {}

    public static void beginWorld() {
        geometry.clear();
        hand = false;
        world = TerrainSmokeProbe.capturingFeatures();
    }

    public static void beginHand() {
        hand = world;
        world = false;
        endModel();
    }

    public static void endHand() {
        hand = false;
        endModel();
    }

    public static void beginModel(RenderType type) {
        if (hand || (world && !type.hasBlending())) {
            model = key(hand ? Kind.HAND : Kind.MODEL, type);
        }
    }

    public static void endModel() {
        model = null;
        cubeCorners.clear();
    }

    public static void modelVertex(float x, float y, float z, int color, float u, float v, int light) {
        if (model == null) {
            return;
        }
        cubeCorners.add(new TerrainVertexData.Corner(x, y, z, u, v, color, light));
        if (cubeCorners.size() == 4) {
            geometry.computeIfAbsent(model, unused -> new ArrayList<>()).add(TerrainVertexData.fromCorners(0, cubeCorners, false));
            cubeCorners.clear();
        }
    }

    public static void beginParticles(net.minecraft.resources.Identifier atlas) {
        if (world) {
            var textures = net.minecraft.client.Minecraft.getInstance().getTextureManager();
            particle = new Key(Kind.PARTICLE, textures.getTexture(atlas).getTextureView());
        }
    }

    public static void endParticles() {
        particle = null;
        cubeCorners.clear();
    }

    public static void particleVertex(org.joml.Quaternionf rotation, float x, float y, float z, float nx, float ny,
                                      float scale, float u, float v, int color, int light) {
        if (particle == null) {
            return;
        }
        var position = new org.joml.Vector3f(nx, ny, 0).rotate(rotation).mul(scale).add(x, y, z);
        cubeCorners.add(new TerrainVertexData.Corner(position.x(), position.y(), position.z(), u, v, color, light));
        if (cubeCorners.size() == 4) {
            geometry.computeIfAbsent(particle, unused -> new ArrayList<>()).add(TerrainVertexData.fromCorners(0, cubeCorners, false));
            cubeCorners.clear();
        }
    }

    public static void item(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        if ((!world && !hand) || quad.materialInfo().itemRenderType().hasBlending()) {
            return;
        }
        var key = key(hand ? Kind.HAND : Kind.ITEM, quad.materialInfo().itemRenderType());
        if (key == null) {
            return;
        }
        var corners = new ArrayList<TerrainVertexData.Corner>(4);
        for (int index = 0; index < 4; index++) {
            var point = pose.pose().transformPosition(quad.position(index), new org.joml.Vector3f());
            long uv = quad.packedUV(index);
            corners.add(new TerrainVertexData.Corner(point.x, point.y, point.z, UVPair.unpackU(uv), UVPair.unpackV(uv),
                    instance.getColor(index), instance.getLightCoordsWithEmission(index, quad.materialInfo().lightEmission())));
        }
        geometry.computeIfAbsent(key, unused -> new ArrayList<>()).add(TerrainVertexData.fromCorners(0, corners, false));
    }

    private static Key key(Kind kind, RenderType type) {
        return type.prepare().textures().stream().filter(binding -> binding.name().equals("Sampler0"))
                .map(binding -> new Key(kind, binding.textureView())).findFirst().orElse(null);
    }

    static List<Draw> upload(Vec3 cameraPosition, SectionPos section) {
        RenderSystem.assertOnRenderThread();
        var origin = section.origin();
        float x = (float) (cameraPosition.x - origin.getX());
        float y = (float) (cameraPosition.y - origin.getY());
        float z = (float) (cameraPosition.z - origin.getZ());
        var draws = new ArrayList<Draw>();
        try {
            for (var entry : geometry.entrySet()) {
                var key = entry.getKey();
                var faces = entry.getValue().stream().map(face -> face.offset(x, y, z)).toList();
                draws.add(new Draw(key.kind(), key.texture(), TerrainVertexSmokeProbe.upload(faces), faces.size() * 6));
            }
            return draws;
        } catch (RuntimeException failure) {
            draws.forEach(Draw::close);
            throw failure;
        }
    }
}
