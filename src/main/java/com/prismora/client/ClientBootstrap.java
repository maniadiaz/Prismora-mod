package com.prismora.client;

import com.prismora.Prismora;
import com.prismora.client.gui.ShaderPackScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Prismora.MOD_ID, value = Dist.CLIENT)
public final class ClientBootstrap {
    @SubscribeEvent public static void beforeFrame(TickEvent.RenderTickEvent.Pre event) { com.prismora.render.PrismoraRenderer.beforeFrame(); AdaptivePerformance.beforeFrame(); if(Boolean.getBoolean("prismora.performanceSmoke"))PerformanceBenchmark.beforeFrame(); if(Boolean.getBoolean("prismora.renderSmoke"))RenderSmokeProbe.frameStart(); }
    @SubscribeEvent public static void shutdown(net.minecraftforge.event.GameShuttingDownEvent event) { ShaderController.INSTANCE.close(); }
    @SubscribeEvent public static void afterFrame(TickEvent.RenderTickEvent.Post event) { AdaptivePerformance.afterFrame(); if(Boolean.getBoolean("prismora.performanceSmoke"))PerformanceBenchmark.afterFrame(); if(Boolean.getBoolean("prismora.renderSmoke"))RenderSmokeProbe.frameEnd(); }
    @SubscribeEvent public static void packs(net.minecraftforge.event.AddPackFindersEvent event) {
        if(event.getPackType()==net.minecraft.server.packs.PackType.CLIENT_RESOURCES) event.addRepositorySource(com.prismora.render.PrismoraRenderer::provide);
    }
    private static final KeyMapping OPEN = new KeyMapping("key.prismora.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, KeyMapping.Category.MISC);
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof VideoSettingsScreen screen) {
            event.addListener(Button.builder(Component.translatable("prismora.title"), b -> Minecraft.getInstance().gui.setScreen(new ShaderPackScreen(screen)))
                .bounds(8,8,100,20).build());
        }
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.isGameLoadFinished())ShaderController.INSTANCE.tick();
        while (OPEN.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new ShaderPackScreen(null));
    }
}
