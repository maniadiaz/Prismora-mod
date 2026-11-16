package com.prismora.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.prismora.Prismora;
import com.prismora.render.runtime.GpuProgram;
import com.prismora.render.runtime.MrtPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class ComplementaryPresentProbe {
    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n";
    private static final String VERTEX = HEADER + """
            layout(location=0) out vec2 uv;
            void main(){
                uv=vec2(float((gl_VertexIndex<<1)&2),float(gl_VertexIndex&2));
                gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
            }
            """;
    private static GpuTexture retained;
    private static GpuTextureView preview;
    private static GpuProgram blit;
    private static boolean checked;
    private static volatile boolean matched;
    private static volatile boolean captured;
    private static int generation;
    private static int[] previousPixels;

    private ComplementaryPresentProbe() {}

    static boolean verified() {
        return matched && captured;
    }

    static int generation() {
        return generation;
    }

    static void stage(GpuTexture output) {
        var device = RenderSystem.getDevice();
        int width = output.getWidth(0), height = output.getHeight(0);
        if (retained != null && !verified()) {
            throw new IllegalStateException("Previous Complementary preview has not completed");
        }
        if (retained != null && (retained.getWidth(0) != width || retained.getHeight(0) != height
                || retained.getFormat() != output.getFormat())) {
            throw new IllegalStateException("Complementary probe dimensions changed");
        }
        checked = false;
        matched = false;
        captured = false;
        generation++;
        try {
            if (retained == null) {
                retained = device.createTexture("Complementary presented frame",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, output.getFormat(), width, height, 1, 1);
                preview = device.createTextureView(retained);
            }
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(output, retained, 0, 0, 0, 0, 0, width, height);
            encoder.submit();
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public static void draw(RenderTarget target) {
        var client = Minecraft.getInstance();
        if (preview == null || client.gui.overlay() != null || client.gui.screen() != null) {
            return;
        }
        var device = RenderSystem.getDevice();
        var destination = target.getColorTextureView();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        if (blit == null) {
            blit = new GpuProgram(device, "complementary_present", VERTEX, HEADER + """
                    uniform sampler2D Preview;
                    layout(location=0) in vec2 uv;
                    layout(location=0) out vec4 color;
                    void main(){color=texture(Preview,uv);}
                    """, List.of(destination.texture().getFormat()), List.of("Preview"));
        }
        MrtPass.draw(device, blit, List.of(destination), Map.of("Preview", preview), sampler);
        if (!checked) {
            checked = true;
            verify(destination, sampler);
            capture(target);
        }
    }

    private static void verify(GpuTextureView destination, com.mojang.renderpearl.api.textures.GpuSampler sampler) {
        var device = RenderSystem.getDevice();
        var readback = device.createBuffer(() -> "Complementary presentation witness", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 4);
        try (var program = new GpuProgram(device, "complementary_present_witness", VERTEX, HEADER + """
                     uniform sampler2D Preview;
                     uniform sampler2D MainTarget;
                     layout(location=0) out vec4 color;
                     void main(){
                        bool ok = true;
                        vec2 dim = vec2(textureSize(Preview,0));
                        for(int y=0;y<7;y++){
                            for(int x=0;x<7;x++){
                               vec2 uv=(vec2(5+x*17,5+y*17)+0.5)/dim;
                               vec4 expected=texture(Preview,uv);
                               vec4 actual=texture(MainTarget,uv);
                               ok=ok&&all(lessThan(abs(actual-expected),vec4(0.012)));
                            }
                        }
                        color=ok?vec4(0,1,0,1):vec4(1,0,0,1);
                     }
                     """, List.of(GpuFormat.RGBA8_UNORM), List.of("Preview", "MainTarget"));
             var witness = device.createTexture("Complementary present result", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                     GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
             var view = device.createTextureView(witness)) {
            MrtPass.draw(device, program, List.of(view), Map.of("Preview", preview, "MainTarget", destination), sampler);
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(witness, readback, 0, () -> {
                try (readback; var map = readback.map(true, false)) {
                    if ((map.data().get(1) & 255) != 255) {
                        throw new IllegalStateException("The Minecraft render target differs from Complementary final");
                    }
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_PRESENT_PASS: main framebuffer matches final at 49 points");
                matched = true;
            }, 0);
            encoder.submit();
        } catch (RuntimeException failure) {
            readback.close();
            throw failure;
        }
    }

    private static void capture(RenderTarget target) {
        int frame = generation;
        Screenshot.takeScreenshot(target, image -> {
            try (image) {
                var pixels = image.getPixels();
                if (previousPixels != null) {
                    if (previousPixels.length != pixels.length) {
                        throw new IllegalStateException("Complementary capture dimensions changed");
                    }
                    int changed = 0;
                    for (int i = 0; i < pixels.length; i++) {
                        if (previousPixels[i] != pixels[i]) changed++;
                    }
                    if (changed < 128) {
                        throw new IllegalStateException("New camera pose did not change the Complementary frame");
                    }
                    Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_LIVE_PASS: frame {} changed {} presented pixels", frame, changed);
                }
                previousPixels = pixels;
                String name = frame == 1 ? "complementary-present.png" : "complementary-present-" + frame + ".png";
                var path = Minecraft.getInstance().gameDirectory.toPath().resolve(name);
                image.writeToFile(path);
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_PRESENT_CAPTURE: screenshot {}x{}", image.getWidth(), image.getHeight());
                captured = true;
            } catch (IOException failure) {
                throw new IllegalStateException("Could not write Complementary frame capture", failure);
            }
        });
    }

    static void close() {
        previousPixels = null;
        if (blit != null) {
            blit.close();
            blit = null;
        }
        if (preview != null) {
            preview.close();
            preview = null;
        }
        if (retained != null) {
            retained.close();
            retained = null;
        }
    }
}
