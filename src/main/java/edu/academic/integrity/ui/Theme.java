package edu.academic.integrity.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Shared visual language for the dependency-free Swing interface. */
public final class Theme {
    public static final Color NAVY = new Color(22, 43, 77);
    public static final Color NAVY_HOVER = new Color(31, 58, 96);
    public static final Color BLUE = new Color(38, 103, 187);
    public static final Color BLUE_LIGHT = new Color(231, 241, 253);
    public static final Color BACKGROUND = new Color(244, 247, 251);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(28, 39, 56);
    public static final Color MUTED = new Color(99, 113, 132);
    public static final Color BORDER = new Color(217, 225, 235);
    public static final Color SUCCESS = new Color(25, 126, 82);
    public static final Color WARNING = new Color(181, 112, 12);
    public static final Color DANGER = new Color(181, 53, 53);
    public static final Color CRITICAL = new Color(143, 34, 47);
    public static final Color EXACT = new Color(255, 221, 128);
    public static final Color MODIFIED = new Color(151, 211, 255);
    public static final Color FUZZY = new Color(190, 230, 188);

    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font BODY_BOLD = BODY.deriveFont(Font.BOLD);
    public static final Font SMALL = BODY.deriveFont(12f);
    public static final Font TITLE = BODY.deriveFont(Font.BOLD, 24f);
    public static final Font SECTION = BODY.deriveFont(Font.BOLD, 17f);

    private Theme() { }

    public static void install() {
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", BODY_BOLD);
        UIManager.put("ToggleButton.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("TextArea.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("CheckBox.font", BODY);
        UIManager.put("RadioButton.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", BODY_BOLD);
        UIManager.put("TabbedPane.font", BODY_BOLD);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", BODY_BOLD);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Viewport.background", SURFACE);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", BLUE_LIGHT);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("ProgressBar.foreground", BLUE);
        UIManager.put("ProgressBar.background", new Color(226, 232, 240));
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    public static JButton primaryButton(String text) {
        JButton button = baseButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(BLUE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BLUE.darker()),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = baseButton(text);
        button.setForeground(TEXT);
        button.setBackground(SURFACE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        return button;
    }

    public static JButton dangerButton(String text) {
        JButton button = secondaryButton(text);
        button.setForeground(DANGER);
        return button;
    }

    private static JButton baseButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(8, 14, 8, 14));
        button.setOpaque(true);
        return button;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(SMALL);
        return label;
    }

    public static void accessibleName(JComponent component, String name, String description) {
        component.getAccessibleContext().setAccessibleName(name);
        component.getAccessibleContext().setAccessibleDescription(description);
    }
}
