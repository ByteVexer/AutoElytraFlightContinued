package me.ByteVexer.autoelytraflight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every option, saved as {@code config/autoelytraflightcontinued.json}. Plain public fields so Gson can
 * round-trip it; anything missing from an older file just keeps its default here.
 *
 * <p>Out of the box it flies the original mod's fast profile with no limits. The speed limit
 * and the altitude ceiling are opt-in, for servers that punish fast or high flight;
 * {@link #applyArdaPreset()} switches both on with Arda Anarchy's numbers.
 */
public final class FlightConfig {

    private static final Logger LOGGER = LogManager.getLogger("AutoElytraFlight-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FlightConfig instance = new FlightConfig();

    // --- HUD ---
    public boolean showHud = true;
    public boolean showGraph = true;
    public int guiX = 5;
    public int guiY = 5;
    public int guiWidth = 150;
    public int guiHeight = 50;
    /** Horizontal distance, in blocks, that the graph spans. */
    public int guiGraphRealWidth = 2000;

    /**
     * One dive/climb cycle. Angles in degrees, positive = nose down; velocities in blocks/tick;
     * turn speeds in degrees/tick. The six numbers are tuned as a set — change one and the
     * cycle may start losing height.
     */
    public static final class Profile {
        public double pullUpAngle;
        public double pullDownAngle;
        /** Climbing ends, and the next dive starts, once speed falls to this. */
        public double pullUpMinVelocity;
        /** Diving ends, and the pull-up starts, once speed reaches this. */
        public double pullDownMaxVelocity;
        /** How fast the nose comes up. */
        public double pullUpSpeed;
        /** How fast the nose goes down. */
        public double pullDownSpeed;

        // For Gson, which fills the fields in afterwards.
        Profile() {
        }

        Profile(double pullUpAngle, double pullDownAngle, double pullUpMinVelocity, double pullDownMaxVelocity,
                double pullUpSpeed, double pullDownSpeed) {
            this.pullUpAngle = pullUpAngle;
            this.pullDownAngle = pullDownAngle;
            this.pullUpMinVelocity = pullUpMinVelocity;
            this.pullDownMaxVelocity = pullDownMaxVelocity;
            this.pullUpSpeed = pullUpSpeed;
            this.pullDownSpeed = pullDownSpeed;
        }
    }

    /**
     * Above this limit the {@link #fastProfile} already fits under it (it peaks near 47.5 m/s),
     * so there is no reason to fly the slower one.
     */
    private static final double FAST_PROFILE_NEEDS = 50;

    // --- Flight profiles ---
    /**
     * The original mod's profile (simonlourson, 2020): dives to 46.5 m/s, climbs about
     * 0.9 blocks/s, about 23 blocks/s forward.
     */
    public Profile fastProfile = new Profile(-46.633514, 37.19872, 1.9102669, 2.3250866, 2.1605124 * 3, 0.20545267 * 3);
    /**
     * Flown when {@link #speedLimitEnabled}. Found by searching the vanilla glide formula for a
     * cycle that never passes about 36 m/s and still climbs: about 0.5 blocks/s up, about
     * 18 blocks/s forward.
     */
    public Profile limitedProfile = new Profile(-52, 34.7, 1.53, 1.74, 6.5, 0.86);

    // --- Server speed limit (off by default; Arda Anarchy resets you to 35 m/s above 39.5 m/s) ---
    public boolean speedLimitEnabled = false;
    /** The server's limit in m/s (blocks per second). */
    public double speedLimit = 39.5;
    /**
     * A dive always ends at least this many m/s below {@link #speedLimit}, whatever the profile
     * says: speed keeps rising for a moment after the pull-up starts (about 1.4 m/s).
     */
    public double speedLimitMargin = 2.5;
    /**
     * A dive that hasn't reached speed after this many ticks is being held back by something
     * (a server speed limit, usually); give up on it rather than fly into the ground. Normal
     * dives take up to about 175. 0 = never.
     */
    public int maxDiveTicks = 300;
    /** How long to glide at {@link #ceilingDescentAngle} after giving up on a dive, before trying again. */
    public int recoveryTicks = 100;

    // --- Altitude ceiling (off by default) ---
    public boolean ceilingEnabled = false;
    /** Never fly above this while {@link #ceilingEnabled}. */
    public double maxAltitude = 1000;
    /** The gentle descent starts this far below {@link #maxAltitude}, leaving room for the climb's momentum. */
    public double ceilingMargin = 40;
    /** How far below the point it started the gentle descent goes before normal cycling resumes. */
    public double ceilingDescent = 60;
    /**
     * Pitch held while descending from the ceiling. Shallow on purpose: about 3 blocks/s down at
     * about 31 blocks/s forward, slower than the normal cycle, so it can't trip a server speed limit.
     */
    public double ceilingDescentAngle = 5;
    /** Degrees per tick the nose moves towards {@link #ceilingDescentAngle}. */
    public double ceilingTurnSpeed = 1;

    // --- Elytra durability ---
    /**
     * Warn once the elytra has this little durability left (one point is about a second of
     * flight, more with Unbreaking). It only warns: there is no auto-land, and switching auto
     * flight off in mid-air would help nobody. 0 = never warn.
     */
    public int durabilityWarning = 30;

    /** The profile to fly right now. */
    public Profile profile() {
        return speedLimitEnabled && speedLimit < FAST_PROFILE_NEEDS ? limitedProfile : fastProfile;
    }

    /**
     * Back to a fresh install, in place: the flight cycle and the HUD hold on to this object,
     * so it can't simply be swapped for a new one.
     */
    public void resetToDefaults() {
        FlightConfig fresh = new FlightConfig();
        try {
            for (java.lang.reflect.Field field : FlightConfig.class.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.set(this, field.get(fresh));
                }
            }
        } catch (IllegalAccessException e) {
            // Public fields of our own class; can't happen.
            throw new IllegalStateException(e);
        }
    }

    /** Arda Anarchy: reset to 35 m/s above 39.5 m/s, and nothing above Y=1000. */
    public void applyArdaPreset() {
        speedLimitEnabled = true;
        speedLimit = 39.5;
        ceilingEnabled = true;
        maxAltitude = 1000;
    }

    public static FlightConfig get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("autoelytraflightcontinued.json");
    }

    public static void load() {
        Path file = file();
        if (Files.exists(file)) {
            try {
                FlightConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FlightConfig.class);
                if (loaded != null) {
                    loaded.repair();
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                // A hand-edited file with a typo shouldn't stop the game; fly with defaults
                // and leave the broken file alone so the edit isn't lost.
                LOGGER.warn("Could not read {}, using defaults", file, e);
                return;
            }
        }
        // Written back even when it loaded, so options added by an update show up in the file.
        save();
    }

    /**
     * A profile deleted or set to null by hand would otherwise crash the game on the first
     * tick of a glide; put the built-in one back.
     */
    private void repair() {
        FlightConfig fresh = new FlightConfig();
        if (fastProfile == null) {
            fastProfile = fresh.fastProfile;
        }
        if (limitedProfile == null) {
            limitedProfile = fresh.limitedProfile;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}", file(), e);
        }
    }
}
