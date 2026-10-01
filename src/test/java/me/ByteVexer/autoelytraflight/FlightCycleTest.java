package me.ByteVexer.autoelytraflight;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flies {@link FlightCycle} through a copy of the vanilla glide formula
 * ({@code LivingEntity.updateFallFlyingMovement}, transcribed from the 26.2 bytecode) in a
 * straight line. If Mojang ever changes the formula this won't notice — it guards the cycle
 * logic and the default profile, not the game.
 */
class FlightCycleTest {

    private static final double GRAVITY = 0.08;

    private double vx;
    private double vy;

    /** One tick of vanilla glide physics, flying along +X. */
    private void glide(float pitchDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double lookHorizontal = Math.cos(pitch);
        double cosSq = lookHorizontal * lookHorizontal;
        double horizontalSpeed = Math.abs(vx);
        vy += GRAVITY * (-1.0 + cosSq * 0.75);
        if (vy < 0 && lookHorizontal > 0) {
            double lift = vy * -0.1 * cosSq;
            vx += lift;
            vy += lift;
        }
        if (pitch < 0 && lookHorizontal > 0) {
            double convert = horizontalSpeed * -Math.sin(pitch) * 0.04;
            vx -= convert;
            vy += convert * 3.2;
        }
        if (lookHorizontal > 0) {
            vx += (horizontalSpeed - vx) * 0.1;
        }
        vx *= 0.99;
        vy *= 0.98;
    }

    /** What a flight did, for the assertions. */
    private record Flight(double endY, double lowestY, double highestY, double topSpeed, int ceilingTicks, int recoveringTicks,
                          int diveAngleTicks) {
    }

    /**
     * @param noise     how wrong the speed the cycle sees may be, as a fraction (lag makes the
     *                  client's position-based reading jitter); the true speed is what's recorded
     * @param serverCap blocks/tick the "server" clamps true speed to every tick, or 0 for none
     */
    private Flight fly(FlightConfig config, double startY, double startVx, double startVy, int seconds, double noise, double serverCap) {
        Random random = new Random(7);
        vx = startVx;
        vy = startVy;
        FlightCycle cycle = new FlightCycle(config);
        cycle.start();
        double y = startY;
        double lowest = y;
        double highest = y;
        double topSpeed = 0;
        double seen = Math.hypot(vx, vy);
        float pitch = 0;
        int ceiling = 0;
        int recovering = 0;
        int atDiveAngle = 0;
        for (int tick = 0; tick < 20 * seconds; tick++) {
            pitch = cycle.nextPitch(pitch, seen, y);
            glide(pitch);
            double speed = Math.hypot(vx, vy);
            if (serverCap > 0 && speed > serverCap) {
                vx *= serverCap / speed;
                vy *= serverCap / speed;
                speed = serverCap;
            }
            y += vy;
            seen = speed * (1 + noise * (random.nextDouble() * 2 - 1));
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
            // The first second is whatever speed the player arrived with, not the cycle's doing.
            if (tick >= 20) {
                topSpeed = Math.max(topSpeed, speed);
            }
            if (cycle.mode() == FlightCycle.Mode.CEILING) {
                ceiling++;
            } else if (cycle.mode() == FlightCycle.Mode.RECOVERING) {
                recovering++;
            }
            if (pitch == (float) config.profile().pullDownAngle) {
                atDiveAngle++;
            }
        }
        return new Flight(y, lowest, highest, topSpeed, ceiling, recovering, atDiveAngle);
    }

    /**
     * Arda's rule and the whole point of the speed-limited profile: never pass 39.5 m/s, and at
     * least break even on altitude. From a near-stall, a normal glide, and already-fast starts,
     * with and without a jittery speed reading.
     */
    @Test
    void defaultProfileHoldsAltitudeUnderTheSpeedLimit() {
        double[][] starts = {{0.3, 0}, {0.5, 0}, {1.0, -0.2}, {1.75, 0}, {0.1, -1.5}, {1.5, -0.6}};
        for (double[] start : starts) {
            for (double noise : new double[]{0, 0.03}) {
                FlightConfig config = new FlightConfig();
                config.applyArdaPreset();
                Flight flight = fly(config, 300, start[0], start[1], 900, noise, 0);
                String what = "start " + start[0] + "," + start[1] + " noise " + noise + ": " + flight;
                assertTrue(flight.topSpeed * 20 < 38.0, "too close to the 39.5 m/s limit, " + what);
                assertTrue(flight.endY > 300, "should at least break even over 15 minutes, " + what);
                assertTrue(flight.lowestY > 240, "first dive dropped further than expected, " + what);
                assertEquals(0, flight.recoveringTicks, "no dive should time out in free flight, " + what);
            }
        }
    }

    /** What a fresh install does: the original fast cycle, no limits, climbing well. */
    @Test
    void outOfTheBoxItFliesTheFastProfileAndClimbs() {
        FlightConfig config = new FlightConfig();
        assertTrue(config.profile() == config.fastProfile);
        Flight flight = fly(config, 300, 0.5, 0, 600, 0, 0);
        assertTrue(flight.endY > 600, "ten minutes of cycling should climb well above the start: " + flight);
        assertTrue(flight.topSpeed * 20 > 45, "the fast profile dives to about 47 m/s: " + flight);
        // The price of starting slow: one long first dive. Documented in the README.
        assertTrue(flight.lowestY > 200, "first dive dropped further than expected: " + flight);
        assertEquals(0, flight.recoveringTicks, "no dive should time out in free flight: " + flight);
        assertEquals(0, flight.ceilingTicks, "no ceiling unless switched on: " + flight);
    }

    /** A hand-edited profile that asks for more speed than the server allows still stays under it. */
    @Test
    void speedLimitOverridesAFastProfile() {
        FlightConfig config = new FlightConfig();
        config.speedLimitEnabled = true;
        config.limitedProfile = config.fastProfile;
        Flight flight = fly(config, 300, 0.5, 0, 300, 0, 0);
        assertTrue(flight.topSpeed * 20 < 39.5, "the original 46.5 m/s profile must be held under the limit: " + flight);
    }

    /** A limit the fast profile already fits under shouldn't cost any speed. */
    @Test
    void aGenerousSpeedLimitKeepsTheFastProfile() {
        FlightConfig config = new FlightConfig();
        config.speedLimitEnabled = true;
        config.speedLimit = 55;
        assertTrue(config.profile() == config.fastProfile);
        Flight flight = fly(config, 300, 0.5, 0, 300, 0, 0);
        assertTrue(flight.topSpeed * 20 < 55 && flight.topSpeed * 20 > 45, "fast, but under the limit: " + flight);
    }

    /**
     * What used to go wrong on Arda: the server held the speed below what the dive was waiting
     * for, so the nose stayed down all the way to the ground. Now the dive times out into a
     * shallow glide and retries.
     */
    @Test
    void aSpeedTheServerNeverAllowsDoesNotMeanDivingForever() {
        FlightConfig config = new FlightConfig();
        double serverCap = config.profile().pullDownMaxVelocity - 0.2;
        Flight flight = fly(config, 300, 0.5, 0, 120, 0, serverCap);
        assertTrue(flight.recoveringTicks > 0, "the dive should have timed out: " + flight);
        assertTrue(flight.diveAngleTicks < 20 * 120 * 0.8, "nose held at the dive angle most of the flight: " + flight);

        config.maxDiveTicks = 0;
        Flight stuck = fly(config, 300, 0.5, 0, 120, 0, serverCap);
        assertTrue(flight.endY > stuck.endY + 100, "timing out should lose far less height than diving on: " + flight + " vs " + stuck);
    }

    /**
     * Stay under the ceiling, and come down from it gently. Started from every altitude a player
     * might switch it on at, including where a climb crosses the trigger at full vertical speed.
     */
    @Test
    void staysUnderTheCeilingAndDescendsGently() {
        FlightConfig config = new FlightConfig();
        config.applyArdaPreset();
        double trigger = config.maxAltitude - config.ceilingMargin;
        for (double start = trigger - 80; start < trigger; start += 3) {
            Flight flight = fly(config, start, 0.5, 0, 1200, 0, 0);
            String what = "start " + start + ": " + flight;
            assertTrue(flight.highestY < config.maxAltitude - 5, "crossed the ceiling, " + what);
            assertTrue(flight.ceilingTicks > 0, "should have reached the ceiling within 20 minutes, " + what);
            assertTrue(flight.endY > trigger - config.ceilingDescent - 120, "sank far below the ceiling band, " + what);
            assertTrue(flight.topSpeed * 20 < 38.0, "the ceiling descent must respect the speed limit too, " + what);
        }
    }

    /** The settings screen's shortest "start below ceiling" with its longest climb: still under the ceiling. */
    @Test
    void theSmallestCeilingMarginTheScreenOffersIsStillSafe() {
        for (boolean limited : new boolean[]{true, false}) {
            FlightConfig config = new FlightConfig();
            config.applyArdaPreset();
            config.speedLimitEnabled = limited;
            config.ceilingMargin = 30;
            double trigger = config.maxAltitude - config.ceilingMargin;
            for (double start = trigger - 120; start < trigger; start += 2) {
                Flight flight = fly(config, start, 0.5, 0, 900, 0, 0);
                assertTrue(flight.highestY < config.maxAltitude, "crossed the ceiling (limited=" + limited + ", start " + start + "): " + flight);
            }
        }
    }

    @Test
    void resetToDefaultsUndoesThePresetInPlace() {
        FlightConfig config = new FlightConfig();
        config.applyArdaPreset();
        config.ceilingDescent = 200;
        config.showGraph = false;
        config.resetToDefaults();
        assertTrue(!config.speedLimitEnabled && !config.ceilingEnabled && config.showGraph);
        assertEquals(60, config.ceilingDescent);
        assertTrue(config.profile() == config.fastProfile);
    }

    @Test
    void pitchStaysWithinTheProfileAngles() {
        FlightConfig config = new FlightConfig();
        FlightConfig.Profile profile = config.profile();
        FlightCycle cycle = new FlightCycle(config);
        cycle.start();
        float pitch = 0;
        for (int tick = 0; tick < 200; tick++) {
            pitch = cycle.nextPitch(pitch, 0, 100);
        }
        assertEquals((float) profile.pullDownAngle, pitch, "a slow glider keeps diving at the dive angle");
        assertTrue(cycle.isDescending());

        pitch = cycle.nextPitch(pitch, profile.pullDownMaxVelocity, 100);
        assertTrue(!cycle.isDescending(), "reaching dive speed starts the pull-up");
        for (int tick = 0; tick < 200; tick++) {
            pitch = cycle.nextPitch(pitch, profile.pullDownMaxVelocity, 100);
        }
        assertEquals((float) profile.pullUpAngle, pitch);
    }
}
