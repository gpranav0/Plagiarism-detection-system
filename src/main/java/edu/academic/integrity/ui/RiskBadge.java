package edu.academic.integrity.ui;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** Compact, accessible risk classification label shared by result views. */
public final class RiskBadge extends JLabel {
    private static final Color LOW_FOREGROUND = new Color(20, 105, 70);
    private static final Color LOW_BACKGROUND = new Color(226, 244, 235);
    private static final Color MEDIUM_FOREGROUND = new Color(139, 87, 9);
    private static final Color MEDIUM_BACKGROUND = new Color(255, 244, 209);
    private static final Color HIGH_FOREGROUND = new Color(168, 67, 24);
    private static final Color HIGH_BACKGROUND = new Color(255, 232, 219);
    private static final Color CRITICAL_FOREGROUND = Theme.CRITICAL;
    private static final Color CRITICAL_BACKGROUND = new Color(249, 224, 228);
    private static final Color UNKNOWN_FOREGROUND = Theme.MUTED;
    private static final Color UNKNOWN_BACKGROUND = new Color(235, 239, 244);

    private String risk = "UNKNOWN";

    public RiskBadge() {
        this("UNKNOWN");
    }

    public RiskBadge(String risk) {
        super("", SwingConstants.CENTER);
        setOpaque(true);
        setFont(Theme.SMALL.deriveFont(java.awt.Font.BOLD));
        setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
        setMinimumSize(new Dimension(72, 24));
        Theme.accessibleName(this, "Risk classification", "Current similarity risk level");
        setRisk(risk);
    }

    public void setRisk(String value) {
        risk = normalize(value);
        setText(risk);
        setForeground(foregroundForRisk(risk));
        setBackground(backgroundForRisk(risk));
        getAccessibleContext().setAccessibleDescription("Risk classification: " + risk);
        setToolTipText("Similarity risk: " + risk);
    }

    public String risk() {
        return risk;
    }

    /** Strong risk color suitable for graph nodes and compact indicators. */
    public static Color colorForRisk(String risk) {
        return switch (normalize(risk)) {
            case "LOW" -> Theme.SUCCESS;
            case "MEDIUM" -> Theme.WARNING;
            case "HIGH" -> new Color(204, 83, 30);
            case "CRITICAL" -> Theme.CRITICAL;
            default -> Theme.MUTED;
        };
    }

    public static Color foregroundForRisk(String risk) {
        return switch (normalize(risk)) {
            case "LOW" -> LOW_FOREGROUND;
            case "MEDIUM" -> MEDIUM_FOREGROUND;
            case "HIGH" -> HIGH_FOREGROUND;
            case "CRITICAL" -> CRITICAL_FOREGROUND;
            default -> UNKNOWN_FOREGROUND;
        };
    }

    public static Color backgroundForRisk(String risk) {
        return switch (normalize(risk)) {
            case "LOW" -> LOW_BACKGROUND;
            case "MEDIUM" -> MEDIUM_BACKGROUND;
            case "HIGH" -> HIGH_BACKGROUND;
            case "CRITICAL" -> CRITICAL_BACKGROUND;
            default -> UNKNOWN_BACKGROUND;
        };
    }

    public static String riskForScore(double score) {
        double bounded = Double.isFinite(score) ? Math.max(0.0, Math.min(1.0, score)) : 0.0;
        if (bounded >= 0.75) return "CRITICAL";
        if (bounded >= 0.55) return "HIGH";
        if (bounded >= 0.35) return "MEDIUM";
        return "LOW";
    }

    private static String normalize(String value) {
        if (value == null) return "UNKNOWN";
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "CRITICAL" -> normalized;
            default -> "UNKNOWN";
        };
    }
}
