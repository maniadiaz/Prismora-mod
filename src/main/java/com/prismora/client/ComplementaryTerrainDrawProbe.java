package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.prismora.Prismora;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class ComplementaryTerrainDrawProbe {
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";

    private ComplementaryTerrainDrawProbe() {}

    static void submit(ComplementaryFrameResources resources, TerrainShaderTranslator.Program shader, ByteBuffer payload, GpuBuffer vertices, int indicesCount,
                       GpuTextureView atlas, GpuTextureView noise, GpuSampler repeat, GpuTextureView shadowDepth,
                       GpuTextureView shadowOpaqueDepth, GpuTextureView shadowColor, GpuTextureView shadowShaftColor, List<ComplementaryPostCompileProbe.Pass> post, ComplementaryPostCompileProbe.Pass sky,
                       ComplementaryTranslucentPass.Program translucent, ComplementaryTranslucentPass.Geometry geometry, GpuTextureView cloud,
                       ComplementaryEntityPass.Program entities, ComplementaryEntityPass.Program particles, ComplementaryEntityPass.Program hand, List<ComplementaryFeatureCapture.Draw> features,
                       TerrainCameraFrame camera, TerrainSceneUniforms scene, TerrainShadowFrame light, net.minecraft.core.SectionPos section, Runnable completed) {
        var modelsAndItems = features.stream().filter(feature -> feature.kind() == ComplementaryFeatureCapture.Kind.MODEL
                || feature.kind() == ComplementaryFeatureCapture.Kind.ITEM).toList();
        var particleFaces = features.stream().filter(feature -> feature.kind() == ComplementaryFeatureCapture.Kind.PARTICLE).toList();
        var handFaces = features.stream().filter(feature -> feature.kind() == ComplementaryFeatureCapture.Kind.HAND).toList();
        int checks = 3 + (modelsAndItems.isEmpty() ? 0 : 1) + (handFaces.isEmpty() ? 0 : 1);
        var pending = new java.util.concurrent.atomic.AtomicInteger(checks);
        Runnable verified = () -> { if (pending.decrementAndGet() == 0) completed.run(); };
        var device = RenderSystem.getDevice();
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var color = new GpuTargets.Spec(TargetFormat.R11F_G11F_B10F, 1, 1, false, 0, 0, 0, 0);
        var material = new GpuTargets.Spec(TargetFormat.RGB8, 1, 1, false, 0, 0, 0, 1);
        var normal = new GpuTargets.Spec(TargetFormat.RGBA8_SNORM, 1, 1, false, 0, 0, 0, 0);
        int width = resources.width(), height = resources.height();
        long witnessBytes = (long) width * height * 4;
        var readback = device.createBuffer(() -> "Complementary terrain witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, witnessBytes * 2);
        try {
            var targets = resources.get("terrain colors", GpuTargets.class,
                    () -> {
                        var specs = new java.util.HashMap<>(Map.of(0, color, 6, material, 4, normal));
                        for (int slot : java.util.stream.Stream.concat(entities.slots().stream(), particles.slots().stream()).distinct().toList()) {
                            specs.putIfAbsent(slot, new GpuTargets.Spec(ComplementaryPostCompileProbe.format(slot), 1, 1, false, 0, 0, 0, 0));
                        }
                        return new GpuTargets(device, specs, width, height, 8L * 1024 * 1024);
                    });
            var depth = resources.get("terrain depth", GpuDepthTargets.class,
                    () -> new GpuDepthTargets(device, width, height, 8L * 1024 * 1024));
            var specular = resources.get("specular", GpuTexture.class, () -> {
                var texture = device.createTexture("Vanilla block materials without custom emission",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
                device.createCommandEncoder().clearColorTexture(texture, new org.joml.Vector4f(0));
                return texture;
            });
            var specularView = resources.get("specular view", GpuTextureView.class, () -> device.createTextureView(specular));
            targets.clear(true);
            depth.beginFrame();
            ComplementarySkyPass.draw(sky, targets.write(0), noise, repeat, camera, scene);
            try (var uniforms = device.createBuffer(() -> "Complementary terrain scene", GpuBuffer.USAGE_UNIFORM, payload);
                 var witness = witnessProgram();
                 var output = device.createTexture("Complementary terrain validation", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, width, height, 1, 1);
                 var outputView = device.createTextureView(output)) {
                var program = ComplementaryPreviewPipelines.get("terrain", () -> GpuProgram.withVertices(device, "complementary_gbuffers_terrain_draw", shader.vertex(), shader.fragment(),
                         List.of(color.format(), material.format(), normal.format()), TerrainVertexData.FORMAT, PrimitiveTopology.QUADS,
                         shader.samplers(), DepthStencilState.DEFAULT, List.of(TerrainShaderTranslator.UNIFORM_BLOCK)));
                var known = Map.of("tex", atlas, "noisetex", noise, "specular", specularView,
                        "shadowtex0", shadowDepth, "shadowtex1", shadowOpaqueDepth, "shadowcolor0", shadowColor);
                var required = new TreeSet<>(((FrontendRenderPipeline) program.compiled()).uniformIndices().keySet());
                required.remove(TerrainShaderTranslator.UNIFORM_BLOCK);
                if (!known.keySet().containsAll(required)) {
                    throw new IllegalStateException("Unbound Complementary terrain samplers: " + required);
                }

                var encoder = device.createCommandEncoder();
                var sceneDepth = depth.view(GpuDepthTargets.Stage.SCENE);
                var witnessInputs = Map.of("TerrainDepth", sceneDepth, "Color", targets.write(0),
                        "Material", targets.write(6), "NormalData", targets.write(4));
                MrtPass.draw(device, witness, List.of(outputView), witnessInputs, nearest);
                encoder.copyTextureToBuffer(output, readback, 0, () -> {}, 0);
                encoder.submit();

                var passDescription = RenderPassDescriptor.builder(() -> "Complementary gbuffers_terrain draw")
                        .withColorAttachment(targets.write(0)).withColorAttachment(targets.write(6)).withColorAttachment(targets.write(4))
                        .withDepthAttachment(sceneDepth).build();
                var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                var indexBuffer = indices.getBuffer(indicesCount);
                try (var pass = device.createCommandEncoder().createRenderPass(passDescription)) {
                    pass.setPipeline(program.compiled());
                    pass.setVertexBuffer(0, vertices.slice());
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                    for (String name : required) {
                        pass.setUniform(name, known.get(name), name.equals("noisetex") ? repeat : nearest);
                    }
                    pass.drawIndexed(indicesCount, 1, 0, 0, 0);
                }

                MrtPass.draw(device, witness, List.of(outputView), witnessInputs, nearest);
                var readEncoder = device.createCommandEncoder();
                readEncoder.copyTextureToBuffer(output, readback, witnessBytes, () -> {
                    int colorCount = 0, materialCount = 0, normalCount = 0, skyCount = 0;
                    try (readback; var mapping = readback.map(true, false)) {
                        var bytes = mapping.data();
                        for (int i = 0; i < width * height * 4; i += 4) {
                            int terrainPixel = Math.toIntExact(witnessBytes) + i;
                            if (bytes.get(terrainPixel) != 0) colorCount++;
                            if (bytes.get(terrainPixel + 1) != 0) materialCount++;
                            if (bytes.get(terrainPixel + 2) != 0) normalCount++;
                            if (bytes.get(i + 3) != 0) skyCount++;
                        }
                    }
                    if (colorCount < 16 || materialCount < 16 || normalCount < 16) {
                        throw new IllegalStateException("Complementary terrain outputs have insufficient coverage: "
                                + colorCount + "/" + materialCount + "/" + normalCount);
                    }
                    Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_TERRAIN_DRAW_PASS: colortex0={}, colortex6={}, colortex4={}, samplers={}",
                            colorCount, materialCount, normalCount, required);
                    if (skyCount < 16) {
                        throw new IllegalStateException("Complementary sky did not write enough background pixels: " + skyCount);
                    }
                    Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_SKY_DRAW_PASS: {} colored pixels without terrain depth", skyCount);
                    verified.run();
                }, 0);
                readEncoder.submit();
                var entityInputs = new java.util.HashMap<>(known);
                entityInputs.put("normals", specularView);
                depth.snapshot(GpuDepthTargets.Stage.BEFORE_HAND);
                ComplementaryEntityPass.draw(entities, modelsAndItems, targets::write, sceneDepth, entityInputs, repeat, camera, scene, light, section);
                if (!modelsAndItems.isEmpty()) {
                    ComplementaryTranslucentPass.verifyDepth("ENTITIES_DEPTH", sceneDepth, depth.view(GpuDepthTargets.Stage.BEFORE_HAND), verified);
                    long models = features.stream().filter(feature -> feature.kind() == ComplementaryFeatureCapture.Kind.MODEL).mapToLong(feature -> feature.indices() / 6).sum();
                    long items = features.stream().filter(feature -> feature.kind() == ComplementaryFeatureCapture.Kind.ITEM).mapToLong(feature -> feature.indices() / 6).sum();
                    Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_ENTITIES_DRAW_PASS: {} model and {} item faces submitted to scene and shadow", models, items);
                }
                depth.snapshot(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY);
                var opaqueDepth = depth.view(GpuDepthTargets.Stage.BEFORE_TRANSLUCENCY);
                var materialCopy = targets.read(6);
                var snapshots = device.createCommandEncoder();
                for (int slot : List.of(3, 6)) {
                    snapshots.copyTextureToTexture(targets.write(slot).texture(), targets.read(slot).texture(),
                            0, 0, 0, 0, 0, width, height);
                }
                snapshots.submit();
                var shadowTextures = Map.of("shadowtex0", shadowDepth, "shadowtex1", shadowOpaqueDepth,
                        "shadowcolor0", shadowColor, "shadowcolor1", shadowShaftColor);
                var translucencyInputs = new java.util.HashMap<>(shadowTextures);
                translucencyInputs.putAll(Map.of("tex", atlas, "noisetex", noise, "gaux4", cloud,
                        "specular", specularView, "normals", specularView, "colortex6", materialCopy));
                var translucencyMeasured = new java.util.concurrent.atomic.AtomicBoolean();
                var handMeasured = new java.util.concurrent.atomic.AtomicBoolean();
                ComplementaryPostDrawProbe.submit(resources, post, targets, sceneDepth, opaqueDepth, noise, repeat,
                        camera, scene, light, section, shadowTextures, attachments -> {
                            translucencyInputs.put("depthtex1", attachments.get("depthtex1"));
                            translucencyInputs.put("gaux2", attachments.get("colortex5"));
                            ComplementaryTranslucentPass.draw(translucent, geometry,
                                    slot -> attachments.get("colortex" + slot), sceneDepth, translucencyInputs,
                                    repeat, camera, scene, light, section);
                            if (translucencyMeasured.compareAndSet(false, true)) {
                                ComplementaryTranslucentPass.verifyDepth("TRANSLUCENT_DEPTH", sceneDepth, opaqueDepth, verified);
                            }
                            entityInputs.put("gaux2", attachments.get("colortex5"));
                            ComplementaryEntityPass.draw(particles, particleFaces, slot -> attachments.get("colortex" + slot),
                                    sceneDepth, entityInputs, repeat, camera, scene, light, section);
                            if (!particleFaces.isEmpty()) {
                                int count = particleFaces.stream().mapToInt(feature -> feature.indices() / 6).sum();
                                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_PARTICLES_DRAW_PASS: {} textured particle quads", count);
                            }
                            boolean checkHand = !handFaces.isEmpty() && handMeasured.compareAndSet(false, true);
                            if (checkHand) {
                                depth.snapshot(GpuDepthTargets.Stage.BEFORE_HAND);
                            }
                            ComplementaryEntityPass.draw(hand, handFaces, slot -> attachments.get("colortex" + slot),
                                    sceneDepth, entityInputs, repeat, camera, scene, light, section);
                            if (checkHand) {
                                ComplementaryTranslucentPass.verifyDepth("HAND_DEPTH", sceneDepth,
                                        depth.view(GpuDepthTargets.Stage.BEFORE_HAND), verified);
                            }
                            if (!handFaces.isEmpty()) {
                                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_HAND_DRAW_PASS: {} first-person faces",
                                        handFaces.stream().mapToInt(feature -> feature.indices() / 6).sum());
                            }
                        }, verified);
            }
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static GpuProgram witnessProgram() {
        return new GpuProgram(RenderSystem.getDevice(), "complementary_terrain_readback", HEADER + """
                void main(){vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));gl_Position=vec4(uv*2.0-1.0,0.0,1.0);}
                """, HEADER + """
                uniform sampler2D TerrainDepth;
                uniform sampler2D Color;
                uniform sampler2D Material;
                uniform sampler2D NormalData;
                layout(location=0) out vec4 result;
                void main(){
                    ivec2 p=ivec2(gl_FragCoord.xy);
                    float z=texelFetch(TerrainDepth,p,0).r;
                    vec3 a=texelFetch(Color,p,0).rgb;
                    vec3 b=texelFetch(Material,p,0).rgb;
                    vec4 c=texelFetch(NormalData,p,0);
                    bool covered=z>0.0&&z<=1.0;
                    result=vec4(float(covered&&all(lessThan(abs(a),vec3(65504)))&&length(a)>0.0001),
                        float(covered&&any(greaterThan(b,vec3(0.0)))),float(covered&&length(c.xyz)>0.1),float(!covered&&all(lessThan(abs(a),vec3(65504)))&&length(a)>0.0001));
                }
                """, List.of(GpuFormat.RGBA8_UNORM), List.of("TerrainDepth", "Color", "Material", "NormalData"));
    }
}
