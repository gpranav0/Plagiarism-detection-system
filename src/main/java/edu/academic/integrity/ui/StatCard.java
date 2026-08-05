package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Compact metric card used by the dashboard and reviewer screen. */
public final class StatCard extends JPanel {
    private final JLabel value = new JLabel("0");
    private final JLabel detail = Theme.mutedLabel(" ");

    public StatCard(String label) {
        super(new BorderLayout(0, 7));
        setBackground(Theme.SURFACE);
        setBorder(Theme.cardBorder());
        JLabel caption = new JLabel(label);
        caption.setForeground(Theme.MUTED);
        caption.setFont(Theme.BODY_BOLD);
        value.setFont(Theme.BODY.deriveFont(java.awt.Font.BOLD, 28f));
        value.setForeground(Theme.NAVY);
        add(caption, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        add(detail, BorderLayout.SOUTH);
    }

    public void setValue(String text) {
        value.setText(text == null ? "—" : text);
    }

    public void setDetail(String text) {
        detail.setText(text == null || text.isBlank() ? " " : text);
    }
}
