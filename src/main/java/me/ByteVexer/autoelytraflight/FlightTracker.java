package me.ByteVexer.autoelytraflight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-tick state: whether auto flight is on, the measured speed, and the altitude history the
 * HUD graph draws. Runs at the start of the client tick so the pitch it sets is the one this
 * tick's glide physics use.
 */
public final class FlightTracker {

    /** One tick of flight history. {@code horizontalDelta} is the ground distance from the previous sample. */
    public record Sample(double altitude, double horizontalDelta, double speed) {
    }

    private final FlightCycle cycle = new FlightCycle(FlightConfig.get());
    private final Deque<Sample> graph = new ArrayDeque<>();
    private double graphSpan;

    private boolean autoFlight;
    private boolean gliding;
    private Vec3 previousPosition;
    private double speed;
    private int durabilityLeft = -1;
    private boolean durabilityWarned;

    public boolean isAutoFlight() {
        return autoFlight;
    }

    /** What auto flight is doing right now; only meaningful while {@link #isAutoFlight()}. */
    public FlightCycle.Mode mode() {
        return cycle.mode();
    }

    public boolean isGliding() {
        return gliding;
    }

    /** Blocks per tick, measured from position change. */
    public double speed() {
        return speed;
    }

    /** Durability left on the worn elytra, or -1 when there is nothing to measure (unbreakable, or not gliding). */
    public int durabilityLeft() {
        return durabilityLeft;
    }

    public boolean isDurabilityLow() {
        int warnAt = FlightConfig.get().durabilityWarning;
        return warnAt > 0 && durabilityLeft >= 0 && durabilityLeft <= warnAt;
    }

    public Iterable<Sample> graph() {
        return graph;
    }

    /** Only does anything mid-glide: there is nothing to automate on the ground. */
    public void toggle() {
        if (gliding) {
            autoFlight = !autoFlight;
            if (autoFlight) {
                cycle.start();
            }
        }
    }

    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        gliding = player != null && player.isFallFlying();
        if (!gliding) {
            // Landing (or the elytra breaking) always switches it off, so it can't silently
            // resume on the next take-off.
            autoFlight = false;
            previousPosition = null;
            graph.clear();
            graphSpan = 0;
            durabilityLeft = -1;
            durabilityWarned = false;
            return;
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        durabilityLeft = chest.isDamageableItem() ? chest.getMaxDamage() - chest.getDamageValue() : -1;
        if (!isDurabilityLow()) {
            durabilityWarned = false;
        } else if (!durabilityWarned) {
            // Once per crossing, not every tick. Loud on purpose: with auto flight on, nobody
            // is watching the armour bar, and a spent elytra drops you out of the sky.
            durabilityWarned = true;
            player.sendOverlayMessage(Component.literal("Elytra almost worn out: " + durabilityLeft + " durability left"));
            player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f);
        }

        if (autoFlight) {
            player.setXRot(cycle.nextPitch(player.getXRot(), speed, player.getY()));
        }

        Vec3 position = player.position();
        double horizontal = 0;
        if (previousPosition != null) {
            double moved = position.distanceTo(previousPosition);
            // A zero reading is a tick where the client didn't move us (lag); keep the last
            // real speed rather than flipping the cycle on a false stall.
            if (moved > 0) {
                speed = moved;
            }
            horizontal = Math.hypot(position.x - previousPosition.x, position.z - previousPosition.z);
        }
        previousPosition = position;

        graph.addLast(new Sample(position.y, horizontal, speed));
        graphSpan += horizontal;
        int limit = FlightConfig.get().guiGraphRealWidth;
        while (graphSpan > limit && graph.size() > 1) {
            graph.removeFirst();
            // The segment that disappears is the one leading to the new first sample.
            graphSpan -= graph.getFirst().horizontalDelta();
        }
    }
}
