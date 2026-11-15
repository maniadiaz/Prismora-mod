package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.prismora.compat.BlockMaterials;
import com.prismora.compat.LegacyPack;
import com.prismora.compat.ShaderPreprocessor;
import com.prismora.pack.PackLoader;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuDepthTargets;
import com.prismora.render.runtime.GpuTargets;
import com.prismora.render.runtime.TerrainVertexData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.SectionPos;
import com.prismora.render.runtime.TerrainCameraFrame;
import org.joml.Matrix4fc;
import java.util.Map;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;

/** Replays Minecraft's real opaque chunk draws into two independent color targets. */
public final class TerrainSmokeProbe {
    private static final int SIZE = 256;
    private static final GpuFormat COLOR = GpuFormat.RGBA8_UNORM;
    private static final String FRAGMENT = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            uniform sampler2D Sampler0;
            layout(location=2) in vec4 vertexColor;
            layout(location=3) in vec2 texCoord0;
            layout(location=0) out vec4 surface;
            layout(location=1) out vec4 coverage;
            void main() {
                vec4 texel = texture(Sampler0, texCoord0);
                #ifdef ALPHA_CUTOUT
                if (texel.a < ALPHA_CUTOUT) discard;
                #endif
                surface = vec4(texel.rgb * vertexColor.rgb, 1.0);
                coverage = vec4(0.0, 1.0, 0.0, 1.0);
            }
            """;
    private static final Map<RenderPipeline, CompiledRenderPipeline> pipelines = new IdentityHashMap<>();
    private static GpuTargets colors;
    private static GpuDepthTargets depth;
    private static GpuBuffer readback;
    private static boolean openingWorld, replaying, pending, finished;
    private static int ticks, worldTicks, readbacks;
    private static volatile int verifiedPixels;
    private static volatile BlockMaterials materials;
    private static boolean vertexSubmitted;
    private static volatile TerrainCameraFrame cameraFrame;
    private static volatile boolean captureStarted;
    private static volatile boolean vertexVerified;
    private record SectionMesh(SectionPos section, List<TerrainVertexData> faces) {}
    private static final AtomicReference<SectionMesh> captured = new AtomicReference<>();
    private static final AtomicBoolean captureInProgress = new AtomicBoolean();
    private static final ThreadLocal<SectionMesh> building = new ThreadLocal<>();
    private static final String PACK_FILE = System.getProperty("prismora.terrainPack", "");

    private TerrainSmokeProbe() {}

    public static void tick() {
        if (finished) {
            return;
        }
        var client = Minecraft.getInstance();
        if (++ticks > 1000) {
            throw new IllegalStateException("Terrain MRT probe timed out after " + readbacks + " GPU reads; pending=" + pending + ", visible section captured=" + (captured.get() != null) + ", MRT pixels=" + verifiedPixels);
        }
        if (!openingWorld && ticks > 60 && client.isGameLoadFinished() && client.gui.overlay() == null) {
            openingWorld = true;
            String id = SmokeWorlds.id();
            if (Files.exists(client.gameDirectory.toPath().resolve("saves/" + id + "/level.dat"))) {
                client.createWorldOpenFlows().openWorld(id, client::stop);
            } else {
                client.createWorldOpenFlows().createFreshLevel(id,
                        new LevelSettings(id, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
        }
        if (client.player != null && ++worldTicks == 1) {
            client.player.setXRot(75);
            if (!PACK_FILE.isEmpty()) {
                materials = loadMaterials();
                var leaves = Blocks.OAK_LEAVES.defaultBlockState();
                int dry = materials.id(leaves.setValue(BlockStateProperties.WATERLOGGED, false));
                int wet = materials.id(leaves.setValue(BlockStateProperties.WATERLOGGED, true));
                if (dry != 10009 || wet != 10011) {
                    throw new IllegalStateException("Complementary oak leaf IDs differ: dry=" + dry + ", wet=" + wet);
                }
                captureStarted = true;
                client.levelRenderer.invalidateCompiledGeometry(client.level, client.options, client.gameRenderer.mainCamera(), client.getBlockColors());
            }
        }
        var sample = captured.get();
        if (worldTicks > 40 && sample != null && cameraFrame != null && !vertexSubmitted) {
            vertexSubmitted = true;
            TerrainVertexSmokeProbe.submit(sample.faces(), cameraFrame.clipFromSection(sample.section()), () -> vertexVerified = true);
        }
        if (verifiedPixels > 0 && (PACK_FILE.isEmpty() || vertexVerified)) {
            if (materials != null) {
                Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_MATERIAL_PASS: {} states mapped; section {} contains {} block-model faces; oak leaves 10009/10011",
                        materials.size(), sample.section(), sample.faces().size());
                materials = null;
            }
            finished = true;
            readback.close();
            depth.close();
            colors.close();
            pipelines.values().forEach(CompiledRenderPipeline::close);
            pipelines.clear();
            Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_MRT_PASS: {} matching pixels from real chunk draws", verifiedPixels);
            client.stop();
        }
    }

    public static void captureCamera(Matrix4fc projection) {
        if (materials != null) {
            var state = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            cameraFrame = new TerrainCameraFrame(projection, state.viewRotationMatrix, state.pos);
        }
    }

    public static void beginSection(SectionPos section) {
        var frame = cameraFrame;
        if (captureStarted && materials != null && captured.get() == null && frame != null
                && new org.joml.FrustumIntersection(frame.clipFromSection(section)).testAab(0, 0, 0, 16, 16, 16)
                && captureInProgress.compareAndSet(false, true)) {
            if (captured.get() == null) {
                building.set(new SectionMesh(section, new ArrayList<>()));
            } else {
                captureInProgress.set(false);
            }
        }
    }

    public static boolean capturingSection() {
        return materials != null && building.get() != null;
    }

    public static void endSection() {
        if (materials == null) {
            return;
        }
        var result = building.get();
        if (result != null) {
            building.remove();
            if (containsVisibleFace(result)) {
                captured.set(new SectionMesh(result.section(), List.copyOf(result.faces())));
                Prismora.LOGGER.info("Prismora camera terrain section {} captured with {} faces", result.section(), result.faces().size());
            }
            captureInProgress.set(false);
        }
    }

    private static boolean containsVisibleFace(SectionMesh mesh) {
        var clip = cameraFrame.clipFromSection(mesh.section());
        var position = new org.joml.Vector4f();
        for (var face : mesh.faces()) {
            float x = 0, y = 0, z = 0;
            for (var corner : face.corners()) {
                x += corner.x();
                y += corner.y();
                z += corner.z();
            }
            clip.transform(position.set(x * 0.25f, y * 0.25f, z * 0.25f, 1f));
            float margin = position.w * 0.85f;
            if (position.w > 0f && Math.abs(position.x) < margin && Math.abs(position.y) < margin
                    && position.z > -position.w && position.z < position.w) {
                return true;
            }
        }
        return false;
    }

    public static int materialId(BlockState state) {
        return materials.id(state);
    }

    public static void recordMaterial(int id, float x, float y, float z, BakedQuad quad) {
        var faces = building.get().faces();
        if (faces.size() >= 65_536) {
            throw new IllegalStateException("Section exceeds the terrain probe's face budget");
        }
        var face = TerrainVertexData.from(id, x, y, z, quad);
        var attrs = face.attributes();
        float dot = attrs.nx() * attrs.tx() + attrs.ny() * attrs.ty() + attrs.nz() * attrs.tz();
        if (!(Math.abs(dot) < 0.0001f && Float.isFinite(attrs.midU()) && Float.isFinite(attrs.midV()))) {
            throw new IllegalStateException("Invalid terrain quad attributes: " + attrs);
        }
        faces.add(face);
    }

    private static BlockMaterials loadMaterials() {
        try {
            var pack = new PackLoader().load(Path.of(PACK_FILE));
            return BlockMaterials.load(LegacyPack.inspect(pack.id(), pack.files()), Map.of(), ShaderPreprocessor.environment());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load terrain materials from " + PACK_FILE, failure);
        }
    }

    public static boolean shouldDraw() {
        return worldTicks > 40 && !pending && verifiedPixels == 0 && Minecraft.getInstance().player != null;
    }

    public static CompiledRenderPipeline pipeline(RenderPipeline original) {
        return replaying ? pipelines.get(original) : null;
    }

    public static void draw(ChunkSectionsToRender chunks, GpuSampler sampler, GpuTextureView atlas) {
        if (!shouldDraw()) {
            return;
        }
        var device = RenderSystem.getDevice();
        if (colors == null) {
            initialize();
            Prismora.LOGGER.info("Prismora replaying terrain via {}", chunks.getClass().getSimpleName());
        }
        colors.clear(false);
        var encoder = device.createCommandEncoder();
        var scene = depth.view(GpuDepthTargets.Stage.SCENE);
        encoder.clearDepthTexture(scene.texture(), 0);
        var surface = colors.write(0);
        var coverage = colors.write(1);
        var descriptor = RenderPassDescriptor.builder(() -> "Prismora real opaque chunk draws")
                .withColorAttachment(surface)
                .withColorAttachment(coverage)
                .withDepthAttachment(scene)
                .build();
        try (var pass = encoder.createRenderPass(descriptor)) {
            RenderSystem.bindDefaultUniforms(pass);
            replaying = true;
            try {
                chunks.renderGroup(ChunkSectionLayerGroup.OPAQUE, pass, sampler, atlas, false);
            } finally {
                replaying = false;
            }
        }
        pending = true;
        encoder.copyTextureToBuffer(surface.texture(), readback, 0, () -> {}, 0);
        encoder.copyTextureToBuffer(coverage.texture(), readback, SIZE * SIZE * 4L, TerrainSmokeProbe::verify, 0);
        encoder.submit();
    }

    private static void verify() {
        readbacks++;
        int matches = 0;
        try (var mapping = readback.map(true, false)) {
            var data = mapping.data();
            int bytes = SIZE * SIZE * 4;
            for (int i = 0; i < bytes; i += 4) {
                boolean surface = (data.get(i + 3) & 255) == 255;
                boolean coverage = (data.get(bytes + i + 1) & 255) == 255 && (data.get(bytes + i + 3) & 255) == 255;
                if (surface != coverage) {
                    throw new IllegalStateException("Terrain MRT attachments disagree at pixel " + i / 4);
                }
                if (surface) {
                    matches++;
                }
            }
        }
        if (matches > 16) {
            verifiedPixels = matches;
        }
        pending = false;
    }

    private static void initialize() {
        var client = Minecraft.getInstance();
        var device = RenderSystem.getDevice();
        var spec = new GpuTargets.Spec(COLOR, 1, 1, false, 0, 0, 0, 0);
        colors = new GpuTargets(device, Map.of(0, spec, 1, spec), SIZE, SIZE, 4L * 1024 * 1024);
        depth = new GpuDepthTargets(device, SIZE, SIZE, 2L * 1024 * 1024);
        readback = device.createBuffer(() -> "Prismora terrain MRT pixels",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, SIZE * SIZE * 8L);
        var resources = client.getResourceManager();
        try {
            var vertex = resources.getResourceOrThrow(Identifier.withDefaultNamespace("shaders/core/terrain.vsh"));
            var source = vertex.readAllAsString();
            var includes = ShaderManager.listAllIncludes(resources);
            try (var shaders = new ShaderSource() {
                @Override public String getShader(Identifier id, ShaderType type) {
                    return type == ShaderType.VERTEX ? source : FRAGMENT;
                }
                @Override public CachedIncludeSource getInclude(Identifier id) {
                    return includes.get(id);
                }
                @Override public void close() {
                    includes.values().forEach(CachedIncludeSource::close);
                }
            }) {
                compile(shaders, RenderPipelines.SOLID_TERRAIN, false, false);
                compile(shaders, RenderPipelines.CUTOUT_TERRAIN, false, true);
                compile(shaders, RenderPipelines.SOLID_TERRAIN_MULTIDRAW, true, false);
                compile(shaders, RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW, true, true);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Terrain vertex shader could not be read", failure);
        }
    }

    private static void compile(ShaderSource shaders, RenderPipeline original, boolean multiDraw, boolean cutout) {
        var id = Identifier.fromNamespaceAndPath("prismora", "terrain_probe/" + original.getLocation().getPath());
        var snippet = multiDraw ? RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET : RenderPipelines.TERRAIN_SNIPPET;
        var builder = RenderPipeline.builder(snippet).withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withColorTargetState(0, new ColorTargetState(java.util.Optional.empty(), COLOR, ColorTargetState.WRITE_ALL))
                .withColorTargetState(1, new ColorTargetState(java.util.Optional.empty(), COLOR, ColorTargetState.WRITE_ALL));
        if (cutout) {
            builder.withShaderDefine("ALPHA_CUTOUT", 0.5f);
        }
        var compiled = RenderSystem.getDevice().compilePipeline(builder.build(), shaders, Runnable::run).join().finishCompile();
        if (compiled == null) {
            throw new IllegalStateException("Terrain probe pipeline failed: " + id);
        }
        pipelines.put(original, compiled);
    }
}
