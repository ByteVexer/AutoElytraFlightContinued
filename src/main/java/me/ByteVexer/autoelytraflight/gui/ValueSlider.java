package me.ByteVexer.autoelytraflight.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A vanilla slider over a numeric range with a fixed step, writing straight into a setter. */
public final class ValueSlider extends AbstractSliderButton {

    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> format;
    private final DoubleConsumer setter;

    public ValueSlider(int x, int y, int width, int height, String label, double min, double max, double step,
                       double initial, DoubleFunction<String> format, DoubleConsumer setter) {
        super(x, y, width, height, Component.empty(), (clamp(initial, min, max) - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.setter = setter;
        updateMessage();
    }

    private double current() {
        double raw = min + value * (max - min);
        return clamp(Math.round(raw / step) * step, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + format.apply(current())));
    }

    @Override
    protected void applyValue() {
        setter.accept(current());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
