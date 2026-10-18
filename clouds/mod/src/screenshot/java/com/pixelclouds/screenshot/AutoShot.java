package com.pixelclouds.screenshot;

import com.pixelclouds.PixelClouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dev-only (src/screenshot, not in the mod jar). With -Dpixelclouds.autoshot=true the client creates or loads a
 * world, visits a few scenes as a spectator, saves a screenshot of each to run/screenshots and quits.
 * Run with: ./gradlew runClient -Pautoshot=true (under xvfb-run when there is no display).
 */
@Mod.EventBusSubscriber(modid = PixelClouds.MOD_ID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ENABLED = Boolean.getBoolean("pixelclouds.autoshot");
    private static final String WORLD = "pixel_clouds_screens";
    private static final long SEED = 20261018L;

    /** name, commands to run, ticks to wait before the screenshot. */
    private record Scene(String name, List<String> commands, int ticks) {}

    private static final List<Scene> SCENES = List.of(
            new Scene("day", List.of("weather clear", "time set 5000", "tp @s 0 150 0 -30 -6"), 120),
            new Scene("above", List.of("time set 5000", "tp @s 40 260 40 -30 40"), 120),
            new Scene("inside", List.of("time set 5000", "tp @s 0 200 0 -30 -4"), 80),
            new Scene("sunset", List.of("time set 12600", "tp @s 0 150 0 80 -10"), 120),
            new Scene("sunrise", List.of("time set 23300", "tp @s 0 150 0 -80 -10"), 120),
            new Scene("night", List.of("time set 18000", "tp @s 0 150 0 -30 -15"), 100),
            new Scene("storm", List.of("time set 6000", "weather thunder", "tp @s 0 150 0 -30 -12"), 220));

    private static int stage = 0;       // 0 title, 1 waiting for the world, 2 scenes, 3 done
    private static int timer = 0;
    private static int scene = 0;
    private static boolean commandsSent = false;

    private AutoShot() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen) {
                    openWorld(mc);
                    stage = 1;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && mc.screen == null) {
                    mc.options.hideGui = true;
                    mc.player.connection.sendCommand("gamemode spectator");
                    mc.player.connection.sendCommand("gamerule doDaylightCycle false");
                    mc.player.connection.sendCommand("gamerule doWeatherCycle false");
                    stage = 2;
                    timer = 0;
                }
            }
            case 2 -> {
                Scene s = SCENES.get(scene);
                if (!commandsSent) {
                    s.commands().forEach(mc.player.connection::sendCommand);
                    commandsSent = true;
                    timer = 0;
                    return;
                }
                if (++timer < s.ticks()) {
                    return;
                }
                Screenshot.grab(mc.gameDirectory, "pixelclouds_" + s.name() + ".png", mc.getMainRenderTarget(),
                        message -> PixelCloudsLog.info(message.getString()));
                scene++;
                commandsSent = false;
                if (scene >= SCENES.size()) {
                    stage = 3;
                    timer = 0;
                }
            }
            case 3 -> {
                if (++timer > 20) {
                    mc.stop();
                }
            }
            default -> {}
        }
    }

    private static void openWorld(Minecraft mc) {
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
                WorldPresets::createNormalWorldDimensions);
    }

    private static final class PixelCloudsLog {
        private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

        static void info(String s) {
            LOG.info("[autoshot] {}", s);
        }
    }
}
