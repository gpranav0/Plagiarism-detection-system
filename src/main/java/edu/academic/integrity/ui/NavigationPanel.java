package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Left-side navigation. Card switching remains a view concern. */
public final class NavigationPanel extends JPanel {
    public interface Listener {
        void navigateTo(String cardName);
    }

    public static final String DASHBOARD = "dashboard";
    public static final String DOCUMENTS = "documents";
    public static final String ANALYSIS = "analysis";
    public static final String RESULTS = "results";
    public static final String RANKING = "ranking";
    public static final String GRAPH = "graph";
    public static final String REVIEWERS = "reviewers";
    public static final String REPORTS = "reports";
    public static final String SETTINGS = "settings";

    private final ButtonGroup group = new ButtonGroup();

    public NavigationPanel(Listener listener) {
        super(new BorderLayout());
        setBackground(Theme.NAVY);
        setPreferredSize(new Dimension(224, 620));
        setBorder(BorderFactory.createEmptyBorder(22, 14, 18, 14));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel mark = new JLabel("  AI  ");
        mark.setOpaque(true);
        mark.setBackground(Theme.BLUE);
        mark.setForeground(Color.WHITE);
        mark.setFont(Theme.BODY.deriveFont(java.awt.Font.BOLD, 18f));
        mark.setBorder(BorderFactory.createEmptyBorder(8, 7, 8, 7));
        mark.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Integrity Lab");
        title.setForeground(Color.WHITE);
        title.setFont(Theme.SECTION);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Academic analysis suite");
        subtitle.setForeground(new Color(177, 194, 217));
        subtitle.setFont(Theme.SMALL);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(mark);
        brand.add(Box.createVerticalStrut(12));
        brand.add(title);
        brand.add(Box.createVerticalStrut(3));
        brand.add(subtitle);
        add(brand, BorderLayout.NORTH);

        JPanel links = new JPanel();
        links.setOpaque(false);
        links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        links.add(Box.createVerticalStrut(26));
        addLink(links, "Dashboard", DASHBOARD, listener, true);
        addLink(links, "Documents", DOCUMENTS, listener, false);
        addLink(links, "Run analysis", ANALYSIS, listener, false);
        addLink(links, "Evidence & results", RESULTS, listener, false);
        addLink(links, "Risk ranking", RANKING, listener, false);
        addLink(links, "Similarity graph", GRAPH, listener, false);
        addLink(links, "Reviewer routing", REVIEWERS, listener, false);
        addLink(links, "Reports & logs", REPORTS, listener, false);
        addLink(links, "Settings & benchmarks", SETTINGS, listener, false);
        add(links, BorderLayout.CENTER);

        JLabel policy = new JLabel("Decision support • Human review");
        policy.setForeground(new Color(177, 194, 217));
        policy.setFont(Theme.SMALL);
        add(policy, BorderLayout.SOUTH);
    }

    private void addLink(JPanel parent, String label, String cardName,
                         Listener listener, boolean selected) {
        JToggleButton button = new JToggleButton(label, selected);
        button.setHorizontalAlignment(JToggleButton.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setPreferredSize(new Dimension(196, 40));
        button.setForeground(Color.WHITE);
        button.setBackground(selected ? Theme.BLUE : Theme.NAVY);
        button.setFont(Theme.BODY_BOLD);
        button.setFocusPainted(true);
        button.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.addActionListener(event -> {
            styleSelection(parent);
            listener.navigateTo(cardName);
        });
        group.add(button);
        parent.add(button);
        parent.add(Box.createVerticalStrut(4));
    }

    private void styleSelection(JPanel parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JToggleButton button) {
                button.setBackground(button.isSelected() ? Theme.BLUE : Theme.NAVY);
            }
        }
    }
}
