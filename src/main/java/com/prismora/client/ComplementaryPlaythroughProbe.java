package com.prismora.client;

import com.prismora.Prismora;
import com.prismora.client.gui.LegacyOptionsScreen;
import com.prismora.client.gui.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

/** Timed traversal of the packaged Complementary renderer, including world re-entry. */
final class ComplementaryPlaythroughProbe {
    private record Stop(String dimension, String first, String second) {}
    private static final List<Stop> ROUTE = List.of(
            new Stop("minecraft:overworld", "-526.5 76 70.5", "-548.5 84 91.5"),
            new Stop("minecraft:the_nether", "0.5 75 0.5", "24.5 76 18.5"),
            new Stop("minecraft:the_end", "0.5 84 0.5", "24.5 88 18.5"));
    private static final long LEG = Duration.ofSeconds(Long.getLong("prismora.playthroughSeconds", 1800L)).toNanos() / ROUTE.size();
    private static final long TRANSITION_LIMIT = Duration.ofMinutes(4).toNanos();
    private static final long CHECK_INTERVAL = Math.min(Duration.ofSeconds(60).toNanos(), Math.max(1, LEG / 2));
    private static final String SATURATION = "T_SATURATION";
    private static final String VALUE = "0.50";
    private enum Phase { START, SELECT, OPTIONS, WAIT_DIMENSION, TRAVERSE, DISCONNECT, REOPEN, DONE }
    private static Phase phase = Phase.START;
    private static int leg;
    private static int firstFrame;
    private static int lastFrame;
    private static boolean alternate;
    private static long phaseAt = System.nanoTime();
    private static long checkpoint;
    private static String packId;
    private static String worldId;

    private ComplementaryPlaythroughProbe() {}

    static void tick() {
        if (phase == Phase.DONE) return;
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (phase != Phase.TRAVERSE && now - phaseAt > TRANSITION_LIMIT) {
            throw new IllegalStateException("Complementary playthrough timed out: " + phase + ", leg " + leg);
        }
        if (!client.isGameLoadFinished() || client.gui.overlay() != null) return;
        var controller = ShaderController.INSTANCE;
        if (!controller.error().isEmpty()) throw new IllegalStateException(controller.error());
        if (controller.busy()) return;
        switch (phase) {
            case START -> {
                client.options.pauseOnLostFocus = false;
                client.options.renderDistance().set(3);
                client.options.framerateLimit().set(60);
                client.getWindow().setWindowed(960, 540);
                worldId = SmokeWorlds.id();
                client.createWorldOpenFlows().createFreshLevel(worldId,
                        new LevelSettings(worldId, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
                advance(Phase.SELECT);
            }
            case SELECT -> {
                if (client.player == null) return;
                var entry = controller.entries().stream().filter(candidate -> ComplementaryActivation.supports(candidate.pack()))
                        .findFirst().orElseThrow();
                packId = entry.id();
                client.player.connection.sendCommand("gamemode spectator");
                move(client, ROUTE.getFirst(), false);
                client.gui.setScreen(new ShaderPackScreen(null));
                click(client, entry.name());
                advance(Phase.OPTIONS);
            }
            case OPTIONS -> {
                if (!packId.equals(controller.selected()) || !TerrainSmokeProbe.runtimeActive()) return;
                var values = new HashMap<>(controller.legacyValues(packId));
                values.put(SATURATION, VALUE);
                client.gui.setScreen(new LegacyOptionsScreen(client.gui.screen(), controller.legacyInspected(), values));
                click(client, Component.translatable("prismora.apply").getString());
                client.gui.setScreen(null);
                advance(Phase.WAIT_DIMENSION);
            }
            case WAIT_DIMENSION -> {
                if (!inDimension(client) || !TerrainSmokeProbe.runtimeActive() || TerrainSmokeProbe.runtimeFrames() < 3) return;
                if (!TerrainSmokeProbe.compiledPostOption(SATURATION, VALUE)) {
                    throw new IllegalStateException("Complementary lost the option applied from the menu");
                }
                firstFrame = lastFrame = TerrainSmokeProbe.runtimeFrames();
                alternate = false;
                advance(Phase.TRAVERSE);
                checkpoint = phaseAt;
                Prismora.LOGGER.info("PRISMORA_PLAYTHROUGH_BEGIN: {}", ROUTE.get(leg).dimension());
            }
            case TRAVERSE -> {
                if (!inDimension(client) || !TerrainSmokeProbe.runtimeActive()) {
                    throw new IllegalStateException("Complementary stopped drawing during " + ROUTE.get(leg).dimension());
                }
                if (now - checkpoint >= CHECK_INTERVAL || now - phaseAt >= LEG) {
                    int frames = TerrainSmokeProbe.runtimeFrames();
                    if (frames <= lastFrame) throw new IllegalStateException("No new Complementary frames in " + ROUTE.get(leg).dimension());
                    lastFrame = frames;
                    checkpoint = now;
                    if (now - phaseAt < LEG) {
                        alternate = !alternate;
                        move(client, ROUTE.get(leg), alternate);
                        Prismora.LOGGER.info("PRISMORA_PLAYTHROUGH_PROGRESS: {} seconds={} frames={}", ROUTE.get(leg).dimension(),
                                Duration.ofNanos(now - phaseAt).toSeconds(), frames - firstFrame);
                    } else {
                        Prismora.LOGGER.info("PRISMORA_PLAYTHROUGH_DIMENSION_PASS: {} seconds={} frames={}", ROUTE.get(leg).dimension(),
                                Duration.ofNanos(now - phaseAt).toSeconds(), frames - firstFrame);
                        if (++leg < ROUTE.size()) {
                            move(client, ROUTE.get(leg), false);
                            advance(Phase.WAIT_DIMENSION);
                        } else {
                            advance(Phase.DISCONNECT);
                            client.disconnect(new TitleScreen(), false);
                        }
                    }
                }
            }
            case DISCONNECT -> {
                if (client.level != null || ComplementaryFrameResources.active()) return;
                client.createWorldOpenFlows().openWorld(worldId, client::stop);
                advance(Phase.REOPEN);
            }
            case REOPEN -> {
                if (client.level == null || client.player == null || client.gui.screen() != null
                        || !TerrainSmokeProbe.runtimeActive() || !ComplementaryFrameResources.active()) return;
                if (!packId.equals(controller.selected()) || !TerrainSmokeProbe.compiledPostOption(SATURATION, VALUE)) {
                    throw new IllegalStateException("Reopened world lost Complementary or its setting");
                }
                Prismora.LOGGER.info("PRISMORA_PLAYTHROUGH_REOPEN_PASS: {} {}={}",
                        client.level.dimension().identifier(), SATURATION, VALUE);
                advance(Phase.DONE);
                client.stop();
            }
            case DONE -> {}
        }
    }

    private static boolean inDimension(Minecraft client) {
        return client.player != null && client.level != null && client.gui.screen() == null
                && client.level.dimension().identifier().toString().equals(ROUTE.get(leg).dimension());
    }

    private static void move(Minecraft client, Stop stop, boolean other) {
        client.player.connection.sendCommand("execute in " + stop.dimension() + " run tp @s "
                + (other ? stop.second() + " 35 55" : stop.first() + " 0 75"));
    }

    private static void advance(Phase next) {
        phase = next;
        phaseAt = System.nanoTime();
    }

    private static void click(Minecraft client, String label) {
        client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow()
                .onPress(null);
    }
}
