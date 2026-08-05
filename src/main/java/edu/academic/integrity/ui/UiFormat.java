package edu.academic.integrity.ui;

import java.text.DecimalFormat;

/** Presentation-only formatting helpers. */
public final class UiFormat {
    private static final DecimalFormat PERCENT = new DecimalFormat("0.00%");
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0");

    private UiFormat() { }

    public static String percent(double value) {
        return PERCENT.format(value);
    }

    public static String number(long value) {
        return NUMBER.format(value);
    }

    public static String millis(long nanos) {
        return new DecimalFormat("0.000").format(nanos / 1_000_000.0) + " ms";
    }

    public static String bytes(long bytes) {
        long absolute = Math.abs(bytes);
        if (absolute < 1024L) return bytes + " B";
        if (absolute < 1024L * 1024L) {
            return new DecimalFormat("0.0").format(bytes / 1024.0) + " KB";
        }
        return new DecimalFormat("0.0").format(bytes / (1024.0 * 1024.0)) + " MB";
    }
}
