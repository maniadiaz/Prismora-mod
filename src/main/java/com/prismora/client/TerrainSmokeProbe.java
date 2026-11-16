package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.QuadInstance;
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
import com.prismora.render.runtime.TerrainSectionGeometry;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
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
    private static GpuBuffer previewMesh;
    private static int requestedPose;
    private static boolean poseRendering;
    private static volatile boolean poseVerified;
    private static volatile TerrainCameraFrame cameraFrame;
    private static volatile boolean captureStarted;
    private static volatile boolean vertexVerified;
    private static volatile boolean uniformsVerified;
    private static volatile boolean shadowsVerified;
    private static TerrainPackCompileProbe.Programs packPrograms;
    private record SectionMesh(SectionPos section, List<TerrainVertexData> faces) {}
    private static Map<SectionPos, List<TerrainVertexData>> previewSections = Map.of();
    private static int meshUpdates;
    private enum EditCheck { PLACE, BREAK, UNLOAD, LOAD, DONE }
    private static EditCheck editCheck;
    private static BlockPos editBlock;
    private static TerrainSectionGeometry editedMesh;
    private static List<TerrainVertexData> facesBeforeEdit;
    private static SectionMesh previewScene;

    private static final ThreadLocal<List<TerrainVertexData>> building = new ThreadLocal<>();
    private static final SectionPos PRESENT_SECTION = SectionPos.of(-33, 4, 4);
    private static final List<Integer> PREVIEW_YAW = List.of(0, 14, 28);
    private static final List<Double> PREVIEW_X = List.of(-526.5, -528.5, -530.5);
    private static final String PACK_FILE = System.getProperty("prismora.terrainPack", "");

    private TerrainSmokeProbe() {}

    public static void tick() {
        if (finished) {
            return;
        }
        var client = Minecraft.getInstance();
        if (++ticks > 1000) {
            throw new IllegalStateException("Terrain MRT probe timed out after " + readbacks + " GPU reads; pending=" + pending + ", visible sections submitted=" + previewSections.keySet() + ", section lifecycle=" + editCheck + ", MRT pixels=" + verifiedPixels);
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
                client.options.renderDistance().set(3);
                client.getWindow().setWindowed(960, 540);
                client.player.connection.sendCommand("gamemode spectator");
                client.player.connection.sendCommand("tp @s -526.5 76 70.5 0 75");
                client.player.connection.sendCommand("time set 6000");
                client.player.connection.sendCommand("gamerule minecraft:advance_time false");
                client.player.connection.sendCommand("weather clear");

            }
        }
        if (materials != null && !captureStarted && worldTicks >= 25 && cameraFrame != null) {
            var position = cameraFrame.position();
            if (Math.abs(position.x + 526.5) < 0.1 && Math.abs(position.z - 70.5) < 0.1) {
                captureStarted = true;
                client.levelRenderer.invalidateCompiledGeometry(client.level, client.options, client.gameRenderer.mainCamera(), client.getBlockColors());
            }
        }
        if (vertexVerified && ComplementaryPresentProbe.verified()) {
            advancePreview(client);
        }
        if (requestedPose == PREVIEW_YAW.size() - 1 && poseVerified && ComplementaryPresentProbe.verified()) {
            verifySectionLifecycle(client);
        }
        if (verifiedPixels > 0 && (PACK_FILE.isEmpty() || (vertexVerified && uniformsVerified && shadowsVerified && ComplementaryPresentProbe.verified() && ComplementaryPresentProbe.generation() == PREVIEW_YAW.size() && editCheck == EditCheck.DONE))) {
            if (materials != null) {
                Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_MATERIAL_PASS: {} states mapped; oak leaves 10009/10011", materials.size());
                captureStarted = false;
            }
            if (!PACK_FILE.isEmpty()) {
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_VISIBLE_TERRAIN_PASS: {} poses crossed a section boundary; {} visible mesh uploads",
                        PREVIEW_X.size(), meshUpdates);
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_LIVE_SEQUENCE_PASS: {} poses presented with {} reused real pipelines",
                        PREVIEW_YAW.size(), ComplementaryPreviewPipelines.size());
            }
            finished = true;
            ComplementaryPresentProbe.close();
            ComplementaryPreviewPipelines.close();
            ComplementaryPostDrawProbe.close();
            updatePreviewMesh(Map.of());
            editedMesh = null;
            facesBeforeEdit = null;
            readback.close();
            depth.close();
            colors.close();
            pipelines.values().forEach(CompiledRenderPipeline::close);
            pipelines.clear();
            Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_MRT_PASS: {} matching pixels from real chunk draws", verifiedPixels);
            client.stop();
        }
    }

    private static void advancePreview(Minecraft client) {
        int presented = ComplementaryPresentProbe.generation();
        if (presented >= PREVIEW_YAW.size() || requestedPose >= presented || !poseVerified) {
            return;
        }
        requestedPose = presented;
        poseRendering = false;
        poseVerified = false;
        client.player.connection.sendCommand("tp @s " + PREVIEW_X.get(requestedPose) + " 76 70.5 " + PREVIEW_YAW.get(requestedPose) + " 75");
    }

    public static void renderComplementaryFrame() {
        var client = Minecraft.getInstance();
        if (finished || !captureStarted || worldTicks <= 40 || cameraFrame == null
                || client.player == null || client.gui.overlay() != null || client.gui.screen() != null) {
            return;
        }
        var visible = visibleTerrain(client);
        if (vertexSubmitted || visible.size() >= 2) {
            updatePreviewMesh(visible);
        }
        if (poseRendering || visible.size() < 2) {
            return;
        }
        var state = client.gameRenderer.gameRenderState().levelRenderState;
        if (Math.abs(state.cameraRenderState.yRot - PREVIEW_YAW.get(requestedPose)) > 0.05f
                || Math.abs(cameraFrame.position().x - PREVIEW_X.get(requestedPose)) > 0.1) {
            return;
        }
        var sample = previewScene;
        poseRendering = true;
        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_MULTISECTION_PASS: {} sections, {} faces submitted together",
                previewSections.size(), sample.faces().size());
        var sky = state.skyRenderState;
        var light = new com.prismora.render.runtime.TerrainShadowFrame(sky.sunAngle, sky.moonAngle, new org.joml.Vector3f(8, 8, 8), 40f);
        if (!vertexSubmitted) {
            vertexSubmitted = true;
            TerrainVertexSmokeProbe.submit(previewMesh, sample.faces(), cameraFrame, sample.section(), light, () -> vertexVerified = true);
            TerrainUniformSmokeProbe.submit(packPrograms.terrain(), cameraFrame, sample.section(), () -> uniformsVerified = true);
            ShadowSamplingSmokeProbe.submit(() -> shadowsVerified = true);
        }
        ComplementaryShadowSmokeProbe.submit(packPrograms.shadow(), previewMesh, sample.faces().size(), light, cameraFrame,
                sample.section(), packPrograms.terrain(), packPrograms.post(), () -> poseVerified = true);
        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_WORLD_FRAME_PASS: pose {} submitted shadow, terrain and post from renderLevel", requestedPose + 1);
    }
    public static void captureCamera(Matrix4fc projection) {
        if (materials != null) {
            var state = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            cameraFrame = new TerrainCameraFrame(projection, state.viewRotationMatrix, state.pos);
        }
    }

    private static TerrainSectionGeometry visibleMesh(Minecraft client, SectionPos position) {
        for (var section : client.levelRenderer.visibleSections()) {
            if (section.getSectionNode() == position.asLong()
                    && section.getSectionMesh() instanceof TerrainSectionGeometry mesh) {
                return mesh;
            }
        }
        return null;
    }

    private static void verifySectionLifecycle(Minecraft client) {
        if (editCheck == EditCheck.DONE) {
            return;
        }
        if (editCheck == null) {
            var mesh = visibleMesh(client, PRESENT_SECTION);
            if (mesh == null || mesh.prismora$faces().isEmpty()) {
                return;
            }
            for (int y = 78; y >= 64; y--) {
                var position = new BlockPos(-526, y, 70);
                if (client.level.getBlockState(position).isAir()) {
                    editBlock = position;
                    editedMesh = mesh;
                    facesBeforeEdit = mesh.prismora$faces();
                    setTestBlock(client, "minecraft:gold_block");
                    editCheck = EditCheck.PLACE;
                    return;
                }
            }
            throw new IllegalStateException("No air block in the terrain edit fixture");
        }
        if (editCheck == EditCheck.LOAD) {
            int destinationSection = SectionPos.blockToSectionCoord(-720);
            if (previewSections.keySet().stream().anyMatch(section -> section.x() == destinationSection)) {
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SECTION_LOAD_PASS: newly loaded section joined the visible mesh");
                editCheck = EditCheck.DONE;
            }
            return;
        }
        if (editCheck == EditCheck.UNLOAD) {
            if (!client.level.getChunkSource().hasChunk(PRESENT_SECTION.x(), PRESENT_SECTION.z())
                    && editedMesh.prismora$faces().isEmpty() && !previewSections.containsKey(PRESENT_SECTION)) {
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SECTION_UNLOAD_PASS: unloaded geometry released and removed from the visible mesh");
                editCheck = EditCheck.LOAD;
            }
            return;
        }
        var mesh = visibleMesh(client, PRESENT_SECTION);
        var faces = previewSections.get(PRESENT_SECTION);
        if (mesh == null || mesh == editedMesh || !editedMesh.prismora$faces().isEmpty() || faces == null
                || faces != mesh.prismora$faces()) {
            return;
        }
        var block = client.level.getBlockState(editBlock);
        if (editCheck == EditCheck.PLACE && block.is(Blocks.GOLD_BLOCK) && !faces.equals(facesBeforeEdit)) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SECTION_PLACE_PASS: placed block replaced geometry in the visible mesh");
            editedMesh = mesh;
            facesBeforeEdit = faces;
            setTestBlock(client, "minecraft:air");
            editCheck = EditCheck.BREAK;
        } else if (editCheck == EditCheck.BREAK && block.isAir() && !faces.equals(facesBeforeEdit)) {
            Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SECTION_BREAK_PASS: removed block replaced geometry in the visible mesh");
            editedMesh = mesh;
            facesBeforeEdit = null;
            client.player.connection.sendCommand("tp @s -720 90 70.5 28 75");
            editCheck = EditCheck.UNLOAD;
        }
    }

    private static void setTestBlock(Minecraft client, String block) {
        client.player.connection.sendCommand("setblock " + editBlock.getX() + " " + editBlock.getY() + " " + editBlock.getZ() + " " + block);
    }

    private static Map<SectionPos, List<TerrainVertexData>> visibleTerrain(Minecraft client) {
        var visible = new LinkedHashMap<SectionPos, List<TerrainVertexData>>();
        for (var renderSection : client.levelRenderer.visibleSections()) {
            var section = SectionPos.of(renderSection.getSectionNode());
            if (renderSection.getSectionMesh() instanceof TerrainSectionGeometry geometry) {
                var faces = geometry.prismora$faces();
                if (!faces.isEmpty()) {
                    visible.put(section, faces);
                }
            }
        }
        return visible;
    }

    private static void updatePreviewMesh(Map<SectionPos, List<TerrainVertexData>> visible) {
        if (visible.equals(previewSections)) {
            previewSections = visible;
            return;
        }
        var anchor = PRESENT_SECTION.origin();
        var faces = new ArrayList<TerrainVertexData>();
        for (var entry : visible.entrySet()) {
            var origin = entry.getKey().origin();
            for (var face : entry.getValue()) {
                faces.add(face.offset(origin.getX() - anchor.getX(), origin.getY() - anchor.getY(), origin.getZ() - anchor.getZ()));
            }
        }
        var replacement = faces.isEmpty() ? null : TerrainVertexSmokeProbe.upload(faces);
        if (previewMesh != null) {
            previewMesh.close();
        }
        previewMesh = replacement;
        previewScene = new SectionMesh(PRESENT_SECTION, List.copyOf(faces));
        previewSections = visible;
        meshUpdates++;
    }

    public static void beginSection() {
        building.remove();
        if (captureStarted && materials != null) {
            building.set(new ArrayList<>());
        }
    }

    public static boolean capturingSection() {
        return building.get() != null;
    }

    public static void endSection(SectionCompiler.Results results) {
        var faces = building.get();
        building.remove();
        if (faces != null) {
            ((TerrainSectionGeometry) (Object) results).prismora$setFaces(List.copyOf(faces));
        }
    }
    public static int materialId(BlockState state) {
        return materials.id(state);
    }

    public static void recordMaterial(int id, float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        var faces = building.get();
        if (faces.size() >= 65_536) {
            throw new IllegalStateException("Section exceeds the terrain probe's face budget");
        }
        var face = TerrainVertexData.from(id, x, y, z, quad, instance);
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
            var legacy = LegacyPack.inspect(pack.id(), pack.files());
            packPrograms = TerrainPackCompileProbe.verify(legacy);
            return BlockMaterials.load(legacy, Map.of(), ShaderPreprocessor.environment());
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
