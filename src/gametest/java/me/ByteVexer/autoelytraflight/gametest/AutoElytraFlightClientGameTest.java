package me.ByteVexer.autoelytraflight.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import me.ByteVexer.autoelytraflight.FlightConfig;
import me.ByteVexer.autoelytraflight.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The mod's promises, asked of the real game with a survival player, an elytra and no fireworks:
 * <ol>
 *   <li>the toggle key opens the settings when you aren't gliding;</li>
 *   <li>out of the box, auto flight climbs (the original fast cycle);</li>
 *   <li>with the Arda Anarchy preset, it never flies faster than the server's 39.5 m/s limit
 *       and still at least holds its altitude.</li>
 * </ol>
 * Position is measured every tick, the way a server sees it.
 *
 * <p>The altitude ceiling and the dive time-out aren't flown here (Y=1000 is too far to climb
 * in a test, and a singleplayer world has no speed limit); the unit tests cover those.
 */
public final class AutoElytraFlightClientGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LogManager.getLogger("AutoElytraFlight-GameTest");
    private static final int START_Y = 400;
    private static final double SPEED_LIMIT = 39.5;

    // Written on the client thread every tick, read by the test thread between waits.
    private static volatile boolean measuring;
    private static volatile double topSpeed;
    private static Vec3 previous;

    /** Altitude averaged over the first and the last 30 seconds of a stretch of flight. */
    private record Stretch(double earlyMean, double lateMean, double lowest, double topSpeed) {
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || !measuring) {
                previous = null;
                return;
            }
            Vec3 now = mc.player.position();
            if (previous != null) {
                topSpeed = Math.max(topSpeed, now.distanceTo(previous) * 20);
            }
            previous = now;
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            TestServerContext server = singleplayer.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            // The run directory keeps its config between runs; start from a fresh install's settings.
            context.runOnClient(mc -> {
                FlightConfig cfg = FlightConfig.get();
                cfg.speedLimitEnabled = false;
                cfg.ceilingEnabled = false;
            });
            context.waitTicks(20);

            // 1. On the ground the key opens the settings.
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitTicks(5);
            check(context.computeOnClient(mc -> mc.gui.screen() instanceof SettingsScreen), "the toggle key opens the settings when not gliding");
            context.takeScreenshot("autoelytraflightcontinued-settings");
            context.runOnClient(mc -> mc.gui.screen().onClose());
            context.waitTicks(5);

            // 2. Out of the box: the fast cycle climbs.
            server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
            server.runCommand("tp @a 0 " + START_Y + " 0 0 0");
            context.waitTicks(20);
            // Held across ticks: the glide starts on the tick the jump key goes down mid-air.
            context.getInput().holdKey(o -> o.keyJump);
            context.waitTicks(3);
            context.getInput().releaseKey(o -> o.keyJump);
            context.waitTicks(3);
            check(context.computeOnClient(mc -> mc.player.isFallFlying()), "jumping mid-air starts a glide");
            context.getInput().pressKey(InputConstants.KEY_R);
            Stretch fast = fly(context, "fast", 20, 120);
            context.takeScreenshot("autoelytraflightcontinued-fast");
            check(fast.lateMean > fast.earlyMean + 20, "the fast cycle climbs: mean y " + fast.earlyMean + " -> " + fast.lateMean);
            check(fast.topSpeed > 44, "the fast cycle really is the fast one, top speed " + fast.topSpeed);

            // 3. Arda preset: under the limit, and no worse than break-even.
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitTicks(5);
            float before = context.computeOnClient(mc -> mc.player.getXRot());
            context.waitTicks(20);
            float after = context.computeOnClient(mc -> mc.player.getXRot());
            check(before == after, "pitch is left alone once auto flight is off (" + before + " -> " + after + ")");
            context.runOnClient(mc -> FlightConfig.get().applyArdaPreset());
            context.getInput().pressKey(InputConstants.KEY_R);
            // Arriving from the fast cycle it may still be above the limit; give it time to
            // bleed that off before holding it to the limit.
            Stretch limited = fly(context, "limited", 30, 150);
            context.takeScreenshot("autoelytraflightcontinued-speed-limited");
            check(limited.topSpeed < SPEED_LIMIT - 1.5, "it stays clear of the " + SPEED_LIMIT + " m/s limit, top speed " + limited.topSpeed);
            check(limited.lateMean >= limited.earlyMean, "it at least breaks even on altitude: mean y " + limited.earlyMean + " -> " + limited.lateMean);

            // 4. A nearly spent elytra: the warning line shows (checked by eye in the screenshot).
            server.runCommand("item replace entity @a armor.chest with minecraft:elytra[damage=412]");
            context.waitTicks(20);
            context.takeScreenshot("autoelytraflightcontinued-low-durability");
        }
    }

    /** Lets auto flight settle for {@code settleSeconds}, then watches it for {@code seconds}. */
    private static Stretch fly(ClientGameTestContext context, String name, int settleSeconds, int seconds) {
        context.waitTicks(20 * settleSeconds);
        topSpeed = 0;
        measuring = true;
        double lowest = Double.POSITIVE_INFINITY;
        double earlySum = 0;
        double lateSum = 0;
        for (int second = 0; second < seconds; second++) {
            context.waitTicks(20);
            double y = context.computeOnClient(mc -> mc.player.getY());
            if (!context.computeOnClient(mc -> mc.player.isFallFlying())) {
                throw new AssertionError("FAILED: stopped gliding " + second + "s into the " + name + " stretch (y=" + y + ")");
            }
            lowest = Math.min(lowest, y);
            if (second < 30) {
                earlySum += y;
            } else if (second >= seconds - 30) {
                lateSum += y;
            }
            if (second % 15 == 0) {
                LOGGER.info("{} t={}s y={} top speed so far {} m/s", name, second, String.format("%.1f", y), String.format("%.2f", topSpeed));
            }
        }
        measuring = false;
        Stretch stretch = new Stretch(earlySum / 30, lateSum / 30, lowest, topSpeed);
        LOGGER.info("{} result: {}", name, stretch);
        return stretch;
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAILED: " + what);
        }
        LOGGER.info("ok: {}", what);
    }
}
