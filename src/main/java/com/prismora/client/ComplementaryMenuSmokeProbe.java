package com.prismora.client;

import com.prismora.Prismora;
import com.prismora.client.gui.ShaderPackScreen;
import com.prismora.render.PrismoraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Exercises real menu buttons with the production render bridge enabled. */
final class ComplementaryMenuSmokeProbe {
    private static int ticks;
    private static int step;
    private static boolean depthRequested;
    private static volatile boolean depthVerified;

    static boolean requestTranslucentDepth() {
        if (step != 3 || depthRequested || !TerrainSmokeProbe.hasTranslucentMaterials(32000, 31018)) return false;
        depthRequested = true;
        return true;
    }

    static void translucentDepthVerified() {
        depthVerified = true;
    }

    private ComplementaryMenuSmokeProbe() {}

    static void tick() {
        var client = Minecraft.getInstance();
        if (++ticks > 2400) {
            throw new IllegalStateException("Complementary menu timed out at step " + step + ": " + ShaderController.INSTANCE.error()
                    + ", live frames=" + TerrainSmokeProbe.runtimeFrames() + ", visible sections=" + client.levelRenderer.visibleSections().size());
        }
        if (!client.isGameLoadFinished() || client.gui.overlay() != null) return;
        if (step == 0) {
            step++;
            client.options.renderDistance().set(3);
            client.getWindow().setWindowed(960, 540);
            String id = SmokeWorlds.id();
            client.createWorldOpenFlows().createFreshLevel(id,
                    new LevelSettings(id, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1234, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
            return;
        }
        if (client.player == null || ShaderController.INSTANCE.busy()) return;
        var controller = ShaderController.INSTANCE;
        if (!controller.error().isEmpty()) throw new IllegalStateException(controller.error());
        var entry = controller.entries().stream().filter(e -> ComplementaryActivation.supports(e.pack())).findFirst().orElseThrow();
        switch (step) {
            case 1 -> {
                client.player.connection.sendCommand("gamemode spectator");
                client.player.connection.sendCommand("tp @s -526.5 76 70.5 0 75");
                client.player.connection.sendCommand("setblock -527 74 71 minecraft:water");
                client.player.connection.sendCommand("setblock -526 74 71 minecraft:cyan_stained_glass");
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, entry.name());
                step++;
            }
            case 2 -> {
                if (!entry.id().equals(controller.selected()) || !TerrainSmokeProbe.runtimeActive()) return;
                client.gui.setScreen(null);
                step++;
            }
            case 3 -> {
                if (TerrainSmokeProbe.runtimeFrames() < 5 || !depthVerified) return;
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_MENU_ENABLE_PASS: {} live frames", TerrainSmokeProbe.runtimeFrames());
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, Component.translatable("prismora.disable").getString());
                step++;
            }
            case 4 -> {
                if (!controller.selected().isEmpty() || TerrainSmokeProbe.runtimeActive()) return;
                if (ComplementaryFrameResources.active() || PrismoraRenderer.hasResourceChain()) {
                    throw new IllegalStateException("Shader resources remain active after disabling");
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_MENU_DISABLE_PASS: renderer released");
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, entry.name());
                step++;
            }
            case 5 -> {
                if (!entry.id().equals(controller.selected()) || !TerrainSmokeProbe.runtimeActive()) return;
                client.gui.setScreen(null);
                step++;
            }
            case 6 -> {
                if (TerrainSmokeProbe.runtimeFrames() < 3) return;
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, controller.entries().stream().filter(e -> e.id().equals("Prismora-Bloom")).findFirst().orElseThrow().name());
                step++;
            }
            case 7 -> {
                if (!controller.selected().equals("Prismora-Bloom") || TerrainSmokeProbe.runtimeActive()) return;
                if (!PrismoraRenderer.hasResourceChain() || ComplementaryFrameResources.active()) {
                    throw new IllegalStateException("Shaderpack switch did not replace the active renderer");
                }
                Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_MENU_SWITCH_PASS: Prismora-Bloom active");
                step++;
                client.stop();
            }
            default -> {}
        }
    }

    private static void click(Minecraft client, String expected) {
        var screen = (ShaderPackScreen) client.gui.screen();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(expected))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing shader menu button " + expected))
                .onPress(null);
    }
}
