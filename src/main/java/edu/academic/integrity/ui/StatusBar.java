package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Non-modal success, validation, and error feedback. */
public final class StatusBar extends JPanel {
    private final JLabel message = new JLabel("Ready");
    private final JLabel mode = Theme.mutedLabel("Local processing • no network dependency");

    public StatusBar() {
        super(new BorderLayout());
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        message.setForeground(Theme.MUTED);
        add(message, BorderLayout.WEST);
        add(mode, BorderLayout.EAST);
    }

    public void showInfo(String text) {
        message.setForeground(Theme.MUTED);
        message.setText(text);
    }

    public void showSuccess(String text) {
        message.setForeground(Theme.SUCCESS);
        message.setText(text);
    }

    public void showError(String text) {
        message.setForeground(Theme.DANGER);
        message.setText(text);
    }
}
