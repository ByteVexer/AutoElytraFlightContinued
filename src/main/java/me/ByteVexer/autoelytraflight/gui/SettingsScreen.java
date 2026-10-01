package me.ByteVexer.autoelytraflight.gui;

import me.ByteVexer.autoelytraflight.FlightConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The settings most players need, on one page: the two opt-in limits, how the glide down from
 * the ceiling behaves, the durability warning and the HUD. The flight profiles' raw numbers stay in the config file — they are tuned as a
 * set, and a slider per number would invite breaking them.
 */
public final class SettingsScreen extends Screen {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFB0B0B0;
    private static final int COL_W = 200;
    private static final int GAP = 10;
    private static final int ROW_H = 24;
    /**
     * Below this the speed-limited profile can't complete a cycle: the dive would have to end
     * before the climb's own switch-over speed.
     */
    private static final double LOWEST_SPEED_LIMIT = 38;
    /** Less room than this and a climb cut short at full speed can coast over the ceiling. */
    private static final double LOWEST_CEILING_MARGIN = 30;
    private static final int ROWS = 6;

    public SettingsScreen() {
        super(Component.literal("Auto Elytra Flight Continued"));
    }

    /** Top of the first row; the rows are centred, but never start above the header text. */
    private int top() {
        return Math.max(46, height / 2 - ROW_H * ROWS / 2 - 6);
    }

    @Override
    protected void init() {
        FlightConfig cfg = FlightConfig.get();
        int left = width / 2 - COL_W - GAP / 2;
        int right = width / 2 + GAP / 2;
        int y = top();

        addRenderableWidget(toggle(left, y, "Speed limit", cfg.speedLimitEnabled, v -> cfg.speedLimitEnabled = v,
                "For servers that punish fast flight. On: flies a slower cycle that stays under the limit. "
                        + "Off: the original fast cycle, which dives to about 47 m/s."));
        addRenderableWidget(slider(right, y, "Limit", LOWEST_SPEED_LIMIT, 60, 0.5, cfg.speedLimit,
                v -> String.format(Locale.ROOT, "%.1f m/s", v), v -> cfg.speedLimit = v,
                "The server's speed limit. Dives end 2.5 m/s below it. From 50 m/s up the fast cycle fits under it and is used again."));

        y += ROW_H;
        addRenderableWidget(toggle(left, y, "Altitude ceiling", cfg.ceilingEnabled, v -> cfg.ceilingEnabled = v,
                "On: near the ceiling it stops climbing and glides down gently, then carries on. The three sliders below shape that glide."));
        addRenderableWidget(slider(right, y, "Ceiling", 200, 2000, 10, cfg.maxAltitude,
                v -> "Y=" + Math.round(v), v -> cfg.maxAltitude = v,
                "Auto flight never takes you above this height."));

        y += ROW_H;
        addRenderableWidget(slider(left, y, "Glide down", 20, 300, 10, cfg.ceilingDescent,
                v -> Math.round(v) + " blocks", v -> cfg.ceilingDescent = v,
                "How far it glides down from the ceiling before it starts climbing again."));
        addRenderableWidget(slider(right, y, "Start below ceiling", LOWEST_CEILING_MARGIN, 150, 5, cfg.ceilingMargin,
                v -> Math.round(v) + " blocks", v -> cfg.ceilingMargin = v,
                "How far under the ceiling the glide down begins. A climb carries on upwards for a moment after it is cut short; "
                        + "this is the room left for that."));

        y += ROW_H;
        addRenderableWidget(slider(left, y, "Glide angle", 2, 15, 1, cfg.ceilingDescentAngle,
                v -> Math.round(v) + " degrees", v -> cfg.ceilingDescentAngle = v,
                "How steeply it glides down from the ceiling. 5 degrees sinks about 3 blocks/s at about 31 m/s; steeper is faster in both."));
        addRenderableWidget(slider(right, y, "Durability warning", 0, 200, 5, cfg.durabilityWarning,
                v -> v < 1 ? "Off" : Math.round(v) + " left", v -> cfg.durabilityWarning = (int) Math.round(v),
                "Sounds a warning once your elytra has this little durability left (about one point per second of flight). "
                        + "It only warns; it never lands or switches auto flight off."));

        y += ROW_H;
        addRenderableWidget(toggle(left, y, "HUD", cfg.showHud, v -> cfg.showHud = v,
                "Auto flight state, altitude and speed, shown while gliding."));
        addRenderableWidget(toggle(right, y, "Altitude graph", cfg.showGraph, v -> cfg.showGraph = v,
                "The altitude-over-distance graph above the HUD text, coloured by speed."));

        y += ROW_H;
        Button arda = Button.builder(Component.literal("Arda Anarchy preset"), b -> {
            cfg.applyArdaPreset();
            rebuildWidgets();
        }).bounds(left, y, COL_W, 20).build();
        arda.setTooltip(tip("Speed limit on at 39.5 m/s and ceiling on at Y=1000."));
        addRenderableWidget(arda);
        Button reset = Button.builder(Component.literal("Reset to defaults"), b -> {
            cfg.resetToDefaults();
            rebuildWidgets();
        }).bounds(right, y, COL_W, 20).build();
        reset.setTooltip(tip("Everything back to a fresh install: fast cycle, no speed limit, no ceiling."));
        addRenderableWidget(reset);

        y += ROW_H + 14;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 75, y, 150, 20).build());
    }

    private ValueSlider slider(int x, int y, String label, double min, double max, double step, double value,
                               DoubleFunction<String> format, DoubleConsumer setter, String tooltip) {
        ValueSlider slider = new ValueSlider(x, y, COL_W, 20, label, min, max, step, value, format, setter);
        slider.setTooltip(tip(tooltip));
        return slider;
    }

    private CycleButton<Boolean> toggle(int x, int y, String label, boolean value, Consumer<Boolean> setter, String tooltip) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
                .create(x, y, COL_W, 20, Component.literal(label), (b, v) -> setter.accept(v));
        button.setTooltip(tip(tooltip));
        return button;
    }

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        FlightConfig cfg = FlightConfig.get();
        int top = top();
        g.centeredText(font, title, width / 2, top - 32, WHITE);
        g.centeredText(font, "While gliding, the toggle key switches auto flight on and off.", width / 2, top - 18, GRAY);
        String flying = cfg.profile() == cfg.fastProfile
                ? "Flying the fast cycle: up to about 47 m/s, about 23 blocks/s forward."
                : "Flying the speed-limited cycle: up to about 36 m/s, about 18 blocks/s forward.";
        g.centeredText(font, flying, width / 2, top + ROW_H * ROWS + 1, GRAY);
    }

    @Override
    public void onClose() {
        // Saved on the way out rather than per click, so dragging a slider doesn't write the file every frame.
        FlightConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
