package me.ByteVexer.autoelytraflight;

/**
 * The dive/climb cycle itself, with no Minecraft types so it can be unit-tested against the
 * vanilla glide formula.
 *
 * <p>Why it holds height: vanilla elytra physics turn speed into lift far more efficiently in a
 * sharp pull-up than a shallow dive costs in altitude. So the cycle dives gently until fast,
 * pulls up hard until slow, and repeats, ending each cycle a little higher than it started.
 *
 * <p>Some servers punish fast flight (Arda Anarchy resets anyone above 39.5 m/s). With the
 * speed limit switched on, the dive ends early enough that the pull-up's overshoot stays below
 * it. And limit or not, if something holds the speed down the cycle backs off into a shallow
 * glide instead of keeping the nose down waiting for a speed it will never be allowed to reach.
 */
public final class FlightCycle {

    /** What the cycle is doing, for the HUD. */
    public enum Mode {
        /** Normal dive/climb cycling. */
        CYCLING,
        /** Gliding down gently from the altitude ceiling. */
        CEILING,
        /** A dive ran out of time without reaching speed (server speed limit); gliding before retrying. */
        RECOVERING
    }

    private final FlightConfig config;
    private boolean descending = true;
    private boolean underCeiling;
    private int diveTicks;
    private int recoveryTicks;

    public FlightCycle(FlightConfig config) {
        this.config = config;
    }

    /** Always begin with a dive: a climb from low speed would just stall. */
    public void start() {
        descending = true;
        underCeiling = false;
        diveTicks = 0;
        recoveryTicks = 0;
    }

    public Mode mode() {
        return underCeiling ? Mode.CEILING : recoveryTicks > 0 ? Mode.RECOVERING : Mode.CYCLING;
    }

    public boolean isDescending() {
        return descending;
    }

    /**
     * The speed, in blocks/tick, at which a dive ends. Normally the profile's own value, but
     * never closer to the server's limit than the configured margin — so a hand-edited profile
     * can't fly into the limit either.
     */
    public double diveEndSpeed() {
        double profileSpeed = config.profile().pullDownMaxVelocity;
        if (!config.speedLimitEnabled) {
            return profileSpeed;
        }
        return Math.min(profileSpeed, (config.speedLimit - config.speedLimitMargin) / 20.0);
    }

    /**
     * @param pitch current pitch in degrees (Minecraft's: positive looks down)
     * @param speed last tick's speed in blocks/tick
     * @param altitude current Y
     * @return the pitch to fly this tick
     */
    public float nextPitch(float pitch, double speed, double altitude) {
        FlightConfig.Profile profile = config.profile();
        if (config.ceilingEnabled) {
            double start = config.maxAltitude - config.ceilingMargin;
            if (altitude >= start) {
                underCeiling = true;
            } else if (underCeiling && altitude <= start - config.ceilingDescent) {
                underCeiling = false;
                restartDive();
            }
        } else {
            underCeiling = false;
        }
        if (underCeiling) {
            // Every cycle ends a little higher than the last, so near the ceiling the cycle has
            // to stop. A steady shallow glide sheds height slowly and at less than cruise speed;
            // a dive would do it faster but is exactly what a server speed limit punishes.
            return glide(pitch);
        }
        if (recoveryTicks > 0) {
            if (--recoveryTicks == 0) {
                restartDive();
            }
            return glide(pitch);
        }

        if (descending) {
            if (speed >= diveEndSpeed()) {
                descending = false;
            } else if (config.maxDiveTicks > 0 && ++diveTicks > config.maxDiveTicks) {
                // A dive this long means something is holding the speed down. Keeping the nose
                // down would fly it into the ground, so glide for a while and try again.
                recoveryTicks = Math.max(1, config.recoveryTicks);
                return glide(pitch);
            }
        } else if (speed <= profile.pullUpMinVelocity) {
            restartDive();
        }
        // Turn gradually rather than snapping: the slow nose-down is part of what keeps the
        // dive cheap, and the turn rates are tuned together with the angles.
        if (descending) {
            return (float) Math.min(pitch + profile.pullDownSpeed, profile.pullDownAngle);
        }
        return (float) Math.max(pitch - profile.pullUpSpeed, profile.pullUpAngle);
    }

    private void restartDive() {
        descending = true;
        diveTicks = 0;
    }

    /** Ease the nose towards the shallow glide angle shared by the ceiling descent and recovery. */
    private float glide(float pitch) {
        double target = config.ceilingDescentAngle;
        return (float) (pitch < target ? Math.min(pitch + config.ceilingTurnSpeed, target)
                : Math.max(pitch - config.ceilingTurnSpeed, target));
    }
}
