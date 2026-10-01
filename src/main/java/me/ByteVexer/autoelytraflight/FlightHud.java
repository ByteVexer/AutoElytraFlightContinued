package me.ByteVexer.autoelytraflight;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Shown only while gliding: an altitude-over-distance graph coloured by speed (red slow, green
 * fast), and under it the auto flight state, altitude and speed.
 */
public final class FlightHud {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF6B6B;
    private static final int PANEL = 0x22FFFFFF;
    private static final int BORDER = 0xFF000000;
    /** Speed, in blocks/tick, drawn as fully green. */
    private static final float FULL_GREEN_SPEED = 2.5f;

    private FlightHud() {
    }

    public static void register(FlightTracker tracker) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(AutoElytraFlightClient.MOD_ID, "hud"),
                (graphics, deltaTracker) -> extract(graphics, tracker));
    }

    private static void extract(GuiGraphicsExtractor g, FlightTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        FlightConfig cfg = FlightConfig.get();
        if (!cfg.showHud || mc.player == null || !tracker.isGliding()) {
            return;
        }
        int x = cfg.guiX;
        int y = cfg.guiY;
        if (cfg.showGraph) {
            graph(g, tracker, cfg);
            y += cfg.guiHeight + 2;
        }
        String state = !tracker.isAutoFlight() ? "Disabled" : switch (tracker.mode()) {
            case CYCLING -> "Enabled";
            case CEILING -> "Descending (ceiling)";
            case RECOVERING -> "Gliding (speed limited)";
        };
        String[] lines = {
                "Auto flight : " + state,
                String.format(Locale.ROOT, "Altitude : %.2f", mc.player.getY()),
                String.format(Locale.ROOT, "Speed : %.2f m/s", tracker.speed() * 20)
        };
        for (String line : lines) {
            g.text(mc.font, line, x, y, WHITE, true);
            y += mc.font.lineHeight + 1;
        }
        if (tracker.isDurabilityLow()) {
            g.text(mc.font, "Elytra : " + tracker.durabilityLeft() + " durability left", x, y, RED, true);
        }
    }

    private static void graph(GuiGraphicsExtractor g, FlightTracker tracker, FlightConfig cfg) {
        int left = cfg.guiX;
        int top = cfg.guiY;
        int width = cfg.guiWidth;
        int height = cfg.guiHeight;
        g.fill(left, top, left + width, top + height, PANEL);
        g.fill(left, top, left + width, top + 1, BORDER);
        g.fill(left, top + height - 1, left + width, top + height, BORDER);
        g.fill(left, top, left + 1, top + height, BORDER);
        g.fill(left + width - 1, top, left + width, top + height, BORDER);

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (FlightTracker.Sample s : tracker.graph()) {
            min = Math.min(min, s.altitude());
            max = Math.max(max, s.altitude());
        }
        if (min > max) {
            return;
        }
        // Headroom so the line doesn't hug the frame; more below, since each cycle starts with a dive.
        max += 5;
        min -= 40;

        // Drawn as one vertical run per sample, joined to the previous sample's height, so the
        // line stays unbroken through steep dives without needing a line primitive.
        double distance = 0;
        int previousY = Integer.MIN_VALUE;
        for (FlightTracker.Sample s : tracker.graph()) {
            distance += s.horizontalDelta();
            int px = left + 1 + (int) (distance * (width - 3) / Math.max(1, cfg.guiGraphRealWidth));
            int py = top + height - 2 - (int) ((s.altitude() - min) * (height - 3) / (max - min));
            px = Math.min(px, left + width - 2);
            int from = previousY == Integer.MIN_VALUE ? py : Math.min(py, previousY);
            int to = previousY == Integer.MIN_VALUE ? py : Math.max(py, previousY);
            g.fill(px, from, px + 1, to + 1, speedColor(s.speed()));
            previousY = py;
        }
    }

    private static int speedColor(double speed) {
        float ratio = (float) speed / FULL_GREEN_SPEED;
        int red = Math.round(255 * Math.clamp(2 * (1 - ratio), 0f, 1f));
        int green = Math.round(255 * Math.clamp(2 * ratio, 0f, 1f));
        return 0xFF000000 | red << 16 | green << 8;
    }
}
