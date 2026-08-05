package edu.academic.integrity.ui;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Friendly empty-state message that can replace a table or detail view. */
public final class EmptyStatePanel extends JPanel {
    private final JLabel message = Theme.mutedLabel("");

    public EmptyStatePanel(String text) {
        super(new GridBagLayout());
        setBackground(Theme.SURFACE);
        setBorder(Theme.cardBorder());
        message.setText(text);
        add(message);
    }

    public void setMessage(String text) {
        message.setText(text);
    }
}
