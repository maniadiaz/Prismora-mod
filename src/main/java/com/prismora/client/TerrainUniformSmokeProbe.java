package com.prismora.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.prismora.Prismora;
import com.prismora.compat.TerrainShaderTranslator;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.TerrainCameraFrame;
import com.prismora.render.runtime.TerrainUniformLayout;
import com.prismora.render.runtime.TerrainSceneUniforms;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.core.SectionPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

final class TerrainUniformSmokeProbe {
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final String QUAD = HEADER + """
            void main(){
                vec2 uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
            }
            """;

    private TerrainUniformSmokeProbe() {}

    static void submit(TerrainShaderTranslator.Program shader, TerrainCameraFrame camera, SectionPos section, Runnable done) {
        var device = RenderSystem.getDevice();
        boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
        var layout = new TerrainUniformLayout(shader.uniformTypes());
        var payload = layout.allocate();
        var client = Minecraft.getInstance();
        var atlas = (TextureAtlas) client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        var atlasView = atlas.getTextureView();
        var lightmap = client.gameRenderer.lightmap();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var stone = atlas.getSprite(Identifier.withDefaultNamespace("block/stone"));
        float stoneU = (stone.getU0() + stone.getU1()) * 0.5f;
        float stoneV = (stone.getV0() + stone.getV1()) * 0.5f;
        var scene = TerrainSceneUniforms.capture(client, atlasView.getWidth(0), atlasView.getHeight(0));
        scene.write(layout, payload);

        var legacy = camera.legacyProjection(zeroToOne);
        var modelView = camera.modelViewFromSection(section);
        var view = camera.viewRotation();
        var normal = new Matrix3f(modelView).invert().transpose();
        layout.put(payload, "prismoraProjection", legacy);
        layout.put(payload, "prismoraModelView", modelView);
        layout.put(payload, "prismoraNormalMatrix", normal);
        layout.put(payload, "gbufferProjection", legacy);
        layout.put(payload, "gbufferProjectionInverse", new Matrix4f(legacy).invert());
        layout.put(payload, "gbufferModelView", view);
        layout.put(payload, "gbufferModelViewInverse", new Matrix4f(view).invert());
        var texture = new Matrix4f();
        var light = new Matrix4f().scaling(1f / 256f, 1f / 256f, 1f / 256f).m30(1f / 32f).m31(1f / 32f);
        layout.putMatrixPair(payload, "prismoraTextureMatrix", texture, light);
        var eye = camera.position();
        layout.putVec3(payload, "cameraPosition", (float) eye.x, (float) eye.y, (float) eye.z);

        var point = new org.joml.Vector4f(1.25f, 4.5f, 7.75f, 1f);
        var nativePoint = camera.clipFromSection(section).transform(point);
        String fragment = HEADER + layout.declaration(TerrainShaderTranslator.UNIFORM_BLOCK) + """
                uniform sampler2D tex;
                uniform sampler2D lightmap;
                layout(location=0) out vec4 result;
                bool near4(vec4 a,vec4 b){return all(lessThan(abs(a-b),vec4(0.002)));}
                void main(){
                    vec4 p=vec4(1.25,4.5,7.75,1.0);
                    vec4 legacyClip=prismoraProjection*prismoraModelView*p;
                    vec4 nativeClip=legacyClip;
                    nativeClip.z=%DEPTH%;
                    vec4 actualProjection=gbufferProjection*vec4(0.25,0.75,-8.0,1.0);
                    bool ok=near4(nativeClip,%CLIP%)
                        && near4(actualProjection,prismoraProjection*vec4(0.25,0.75,-8.0,1.0))
                        && near4(gbufferProjectionInverse*actualProjection,vec4(0.25,0.75,-8.0,1.0))
                        && near4(gbufferModelViewInverse*gbufferModelView*p,p)
                        && near4(vec4(prismoraNormalMatrix*vec3(0.25,0.5,1.0),1.0),vec4(mat3(gbufferModelView)*vec3(0.25,0.5,1.0),1.0))
                        && near4(prismoraTextureMatrix[0]*p,p)
                        && near4(prismoraTextureMatrix[1]*vec4(240.0,0.0,0.0,1.0),vec4(0.96875,0.03125,0.0,1.0))
                        && near4(vec4(cameraPosition,1.0),%CAMERA%);
                    vec4 stone = texture(tex,%STONE%);
                    vec2 fullLightUv = (prismoraTextureMatrix[1]*vec4(240.0,240.0,0.0,1.0)).xy;
                    vec3 fullLight = texture(lightmap,fullLightUv).rgb;
                    vec3 minLight = texelFetch(lightmap,ivec2(0),0).rgb;
                    bool resources = all(equal(textureSize(tex,0),atlasSize))
                        && stone.a > 0.99 && length(stone.rgb) > 0.04
                        && all(equal(textureSize(lightmap,0),ivec2(16)))
                        && length(fullLight) > length(minLight) + 0.01;
                    bool sceneMatches = near4(vec4(viewWidth,viewHeight,aspectRatio,rainStrength),%VIEW%)
                        && near4(vec4(near,far,0.0,0.0),%RANGE%)
                        && worldTime == %TIME% && worldDay == %DAY% && moonPhase == %MOON%
                        && near4(vec4(fogColor,1.0),%FOG%)
                        && near4(vec4(skyColor,1.0),%SKY%);
                    ok = ok && resources && sceneMatches;
                    result=ok?vec4(0.0,1.0,0.0,1.0):vec4(1.0,0.0,0.0,1.0);
                }
                """
                .replace("%DEPTH%", zeroToOne ? "(legacyClip.w-legacyClip.z)*0.5" : "-legacyClip.z")
                .replace("%CLIP%", vec4(nativePoint.x, nativePoint.y, nativePoint.z, nativePoint.w))
                .replace("%CAMERA%", vec4((float) eye.x, (float) eye.y, (float) eye.z, 1f))
                .replace("%STONE%", "vec2(" + stoneU + "," + stoneV + ")")
                .replace("%VIEW%", vec4(scene.width(), scene.height(), scene.aspectRatio(), scene.rainStrength()))
                .replace("%RANGE%", vec4(scene.near(), scene.far(), 0f, 0f))
                .replace("%TIME%", Integer.toString(scene.worldTime()))
                .replace("%DAY%", Integer.toString(scene.worldDay()))
                .replace("%MOON%", Integer.toString(scene.moonPhase()))
                .replace("%FOG%", vec4(scene.fogColor().x, scene.fogColor().y, scene.fogColor().z, 1f))
                .replace("%SKY%", vec4(scene.skyColor().x, scene.skyColor().y, scene.skyColor().z, 1f));

        var readback = device.createBuffer(() -> "Prismora terrain uniform witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 4);
        try (var uniforms = device.createBuffer(() -> "Prismora terrain uniforms std140", GpuBuffer.USAGE_UNIFORM, payload);
             var program = GpuProgram.withVertices(device, "terrain_uniform_smoke", QUAD, fragment,
                     List.of(GpuFormat.RGBA8_UNORM), null, PrimitiveTopology.TRIANGLES, List.of("tex", "lightmap"), null,
                     List.of(TerrainShaderTranslator.UNIFORM_BLOCK));
             var output = device.createTexture("Prismora terrain uniform result", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
             var viewOut = device.createTextureView(output)) {
            var encoder = device.createCommandEncoder();
            var descriptor = RenderPassDescriptor.builder(() -> "Prismora terrain uniform round trip").withColorAttachment(viewOut).build();
            try (var pass = encoder.createRenderPass(descriptor)) {
                pass.setPipeline(program.compiled());
                pass.setUniform(TerrainShaderTranslator.UNIFORM_BLOCK, uniforms);
                pass.setUniform("tex", atlasView, sampler);
                pass.setUniform("lightmap", lightmap, sampler);
                pass.draw(3, 1, 0, 0);
            }
            encoder.copyTextureToBuffer(output, readback, 0, () -> {
                try (readback; var mapping = readback.map(true, false)) {
                    if ((mapping.data().get(1) & 255) != 255) {
                        throw new IllegalStateException("Terrain shader scene uniform or Minecraft texture witness failed");
                    }
                }
                Prismora.LOGGER.info("PRISMORA_WORLD_TERRAIN_UNIFORMS_PASS: camera, scene values, block atlas and lightmap verified on GPU");
                done.run();
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static String vec4(float x, float y, float z, float w) {
        return "vec4(" + x + "," + y + "," + z + "," + w + ")";
    }
}
