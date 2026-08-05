package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Common title and description treatment for application cards. */
public abstract class ScreenPanel extends JPanel implements Refreshable {
    private final JPanel content = new JPanel(new BorderLayout(0, 16));

    protected ScreenPanel(String title, String description) {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.TITLE);
        titleLabel.setForeground(Theme.TEXT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descriptionLabel = Theme.mutedLabel(description);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(5));
        heading.add(descriptionLabel);

        content.setOpaque(false);
        content.add(heading, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    protected final void setScreenContent(Component component) {
        content.add(component, BorderLayout.CENTER);
    }
}
