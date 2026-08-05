package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** Labelled zero-to-one score with a precise percentage and visual bar. */
public final class ScoreBar extends JPanel {
    private static final int SCALE = 1_000;

    private final JLabel label = new JLabel();
    private final JLabel value = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, SCALE);
    private double score;

    public ScoreBar(String text) {
        this(text, 0.0);
    }

    public ScoreBar(String text, double initialScore) {
        super(new BorderLayout(10, 5));
        setOpaque(false);
        label.setFont(Theme.BODY_BOLD);
        label.setForeground(Theme.TEXT);
        value.setFont(Theme.SMALL);
        value.setForeground(Theme.MUTED);
        progress.setStringPainted(false);
        progress.setFocusable(false);
        add(label, BorderLayout.WEST);
        add(value, BorderLayout.EAST);
        add(progress, BorderLayout.SOUTH);
        setLabel(text);
        setScore(initialScore);
        Theme.accessibleName(progress, text, "Similarity score from zero to one hundred percent");
    }

    public void setLabel(String text) {
        String safe = text == null || text.isBlank() ? "Score" : text;
        label.setText(safe);
        progress.getAccessibleContext().setAccessibleName(safe);
    }

    public void setScore(double newScore) {
        score = clamp(newScore);
        value.setText(UiFormat.percent(score));
        progress.setValue((int) Math.round(score * SCALE));
        progress.getAccessibleContext().setAccessibleDescription(
                label.getText() + ": " + UiFormat.percent(score));
        progress.setToolTipText(label.getText() + ": " + UiFormat.percent(score));
    }

    public double score() {
        return score;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value) || value < 0.0) return 0.0;
        return Math.min(1.0, value);
    }
}
