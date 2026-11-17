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
    private static final String RESTORE_PHASE = System.getProperty("prismora.complementaryRestorePhase", "");
    private static final String OPTION = "T_SATURATION";
    private static final String OPTION_VALUE = "0.50";
    private static int ticks;
    private static int step;
    private static boolean depthRequested;
    private static volatile boolean depthVerified;
    enum Feature { ENTITIES, SHADOW, HAND, PARTICLES }
    private static final java.util.EnumSet<Feature> requested = java.util.EnumSet.noneOf(Feature.class);
    private static volatile int featuresVerified;

    static boolean requestFeature(Feature feature) {
        return Boolean.getBoolean("prismora.complementaryMenuSmoke") && step == 3 && requested.add(feature);
    }

    static void featureVerified() {
        featuresVerified++;
    }

    static boolean includesModelsAndItems(java.util.List<ComplementaryFeatureCapture.Draw> draws) {
        return draws.stream().anyMatch(draw -> draw.kind() == ComplementaryFeatureCapture.Kind.MODEL)
                && draws.stream().anyMatch(draw -> draw.kind() == ComplementaryFeatureCapture.Kind.ITEM);
    }

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
        if (client.player.getAbilities().mayfly && !client.player.getAbilities().flying) {
            client.player.getAbilities().flying = true;
            client.player.onUpdateAbilities();
        }
        if (step == 3 && ticks % 10 == 0) {
            client.player.connection.sendCommand("particle minecraft:flame -526.5 75 71 0.4 0.2 0.3 0 32 force");
        }
        var controller = ShaderController.INSTANCE;
        if (!controller.error().isEmpty()) throw new IllegalStateException(controller.error());
        var entry = controller.entries().stream().filter(e -> ComplementaryActivation.supports(e.pack())).findFirst().orElseThrow();
        if (!RESTORE_PHASE.isEmpty()) {
            verifyRestore(client, controller, entry);
            return;
        }
        switch (step) {
            case 1 -> {
                client.player.connection.sendCommand("tp @s -526.5 76 70.5 0 75");
                client.player.connection.sendCommand("setblock -527 74 71 minecraft:water");
                client.player.connection.sendCommand("setblock -526 74 71 minecraft:cyan_stained_glass");
                client.player.connection.sendCommand("time set 6000");
                client.player.connection.sendCommand("summon minecraft:cow -526.5 73 72 {NoAI:1b,NoGravity:1b,Silent:1b}");
                client.player.connection.sendCommand("summon minecraft:item -527 74 72 {Item:{id:\"minecraft:diamond\",count:1},NoGravity:1b,PickupDelay:32767s}");
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
                if (TerrainSmokeProbe.runtimeFrames() < 5 || !depthVerified || featuresVerified != Feature.values().length) return;
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

    private static void verifyRestore(Minecraft client, ShaderController controller,
                                      com.prismora.pack.ShaderPackManager.Entry entry) {
        if (step == 1) {
            client.player.connection.sendCommand("gamemode spectator");
            client.player.connection.sendCommand("tp @s -526.5 76 70.5 0 75");
            if (RESTORE_PHASE.equals("save")) {
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, entry.name());
            }
            step++;
            return;
        }
        if (!entry.id().equals(controller.selected()) || !TerrainSmokeProbe.runtimeActive()) return;
        if (RESTORE_PHASE.equals("save") && step == 2) {
            client.gui.setScreen(null);
            controller.applyLegacyOptions(entry.id(), java.util.Map.of(OPTION, OPTION_VALUE));
            step++;
            return;
        }
        if (TerrainSmokeProbe.runtimeFrames() < 3) return;
        if (!OPTION_VALUE.equals(controller.legacyValues(entry.id()).get(OPTION))
                || !TerrainSmokeProbe.compiledPostOption(OPTION, OPTION_VALUE)) {
            throw new IllegalStateException("Complementary did not compile with the saved saturation");
        }
        Prismora.LOGGER.info("PRISMORA_COMPLEMENTARY_RESTART_{}_PASS: {} {}={}",
                RESTORE_PHASE.toUpperCase(java.util.Locale.ROOT), entry.id(), OPTION, OPTION_VALUE);
        client.stop();
    }

    private static void click(Minecraft client, String expected) {
        var screen = (ShaderPackScreen) client.gui.screen();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(expected))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing shader menu button " + expected))
                .onPress(null);
    }
}
