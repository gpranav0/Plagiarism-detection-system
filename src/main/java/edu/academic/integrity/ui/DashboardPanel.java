package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.service.DashboardSnapshot;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Corpus health, workflow metrics, and recent activity at a glance. */
public final class DashboardPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final StatCard submissions = new StatCard("Submissions");
    private final StatCard references = new StatCard("Reference documents");
    private final StatCard analyses = new StatCard("Completed analyses");
    private final StatCard highRisk = new StatCard("High-risk cases");
    private final StatCard pending = new StatCard("Pending assignments");
    private final JTextArea activity = new JTextArea();
    private final JLabel corpusSummary = new JLabel("No corpus loaded");

    public DashboardPanel(ApplicationController controller, UiHost host) {
        super("Dashboard", "Monitor the current corpus and move cases through evidence-based review.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setOpaque(false);

        JPanel metrics = new JPanel(new GridLayout(0, 3, 12, 12));
        metrics.setOpaque(false);
        metrics.add(submissions);
        metrics.add(references);
        metrics.add(analyses);
        metrics.add(highRisk);
        metrics.add(pending);
        metrics.add(actionCard());
        root.add(metrics, BorderLayout.NORTH);

        JPanel recent = new JPanel(new BorderLayout(0, 10));
        recent.setBackground(Theme.SURFACE);
        recent.setBorder(Theme.cardBorder());
        JLabel heading = new JLabel("Recent activity");
        heading.setFont(Theme.SECTION);
        heading.setForeground(Theme.TEXT);
        recent.add(heading, BorderLayout.NORTH);
        activity.setEditable(false);
        activity.setLineWrap(true);
        activity.setWrapStyleWord(true);
        activity.setRows(10);
        activity.setBackground(Theme.SURFACE);
        activity.setForeground(Theme.MUTED);
        activity.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
        JScrollPane scroll = new JScrollPane(activity);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        recent.add(scroll, BorderLayout.CENTER);
        root.add(recent, BorderLayout.CENTER);
        return root;
    }

    private JPanel actionCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.SURFACE);
        card.setBorder(Theme.cardBorder());
        JLabel title = new JLabel("Quick actions");
        title.setFont(Theme.BODY_BOLD);
        title.setForeground(Theme.MUTED);
        corpusSummary.setFont(Theme.BODY_BOLD);
        corpusSummary.setForeground(Theme.NAVY);
        JButton importButton = Theme.secondaryButton("Import documents");
        importButton.setMnemonic('I');
        importButton.setToolTipText("Open submission and reference document management");
        importButton.addActionListener(event -> host.navigateTo(NavigationPanel.DOCUMENTS));
        JButton analyzeButton = Theme.primaryButton("Start analysis");
        analyzeButton.setMnemonic('A');
        analyzeButton.setToolTipText("Configure and run a plagiarism analysis");
        analyzeButton.addActionListener(event -> host.navigateTo(NavigationPanel.ANALYSIS));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(importButton);
        buttons.add(analyzeButton);
        card.add(title);
        card.add(Box.createVerticalStrut(9));
        card.add(corpusSummary);
        card.add(Box.createVerticalStrut(15));
        card.add(buttons);
        return card;
    }

    @Override
    public void refreshData() {
        DashboardSnapshot snapshot = controller.dashboard();
        submissions.setValue(UiFormat.number(snapshot.submissionCount()));
        submissions.setDetail("Validated student work");
        references.setValue(UiFormat.number(snapshot.referenceCount()));
        references.setDetail("Sources available for comparison");
        analyses.setValue(UiFormat.number(snapshot.completedAnalyses()));
        analyses.setDetail("Current in-memory result set");
        highRisk.setValue(UiFormat.number(snapshot.highRiskCases()));
        highRisk.setDetail("High or critical risk");
        pending.setValue(UiFormat.number(snapshot.pendingAssignments()));
        pending.setDetail("Awaiting reviewer capacity");
        corpusSummary.setText(snapshot.totalDocuments() + " documents ready");
        activity.setText(snapshot.recentActivity().isBlank()
                ? "No activity has been recorded yet. Import documents to begin."
                : snapshot.recentActivity());
        activity.setCaretPosition(0);
    }
}
