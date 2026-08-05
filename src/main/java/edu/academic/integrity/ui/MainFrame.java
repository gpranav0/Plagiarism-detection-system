package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.service.DashboardSnapshot;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** Responsive application shell with card navigation and safe lifecycle handling. */
public final class MainFrame extends JFrame
        implements NavigationPanel.Listener, UiHost {
    private final ApplicationController controller;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final TopStatusPanel topStatus = new TopStatusPanel();
    private final StatusBar statusBar = new StatusBar();
    private final DashboardPanel dashboard;
    private final DocumentManagementPanel documents;
    private final AnalysisPanel analysis;
    private final ResultsPanel results;
    private final RankingPanel ranking;
    private final SimilarityGraphPanel graph;
    private final ReviewerAssignmentPanel reviewers;
    private final ReportsPanel reports;
    private final SettingsPanel settings;
    private final Refreshable[] refreshables;
    private String activeCard = NavigationPanel.DASHBOARD;
    private boolean shutdown;

    public MainFrame(ApplicationController controller) {
        super("Advanced Plagiarism Detection & Academic Integrity Analysis");
        if (controller == null) throw new IllegalArgumentException("controller is required");
        this.controller = controller;
        dashboard = new DashboardPanel(controller, this);
        documents = new DocumentManagementPanel(controller, this);
        analysis = new AnalysisPanel(controller, this);
        results = new ResultsPanel(controller, this);
        ranking = new RankingPanel(controller, this);
        graph = new SimilarityGraphPanel(controller, this);
        reviewers = new ReviewerAssignmentPanel(controller, this);
        reports = new ReportsPanel(controller, this);
        settings = new SettingsPanel(controller, this);
        refreshables = new Refreshable[]{dashboard, documents, analysis, results, ranking,
                graph, reviewers, reports, settings};
        buildWindow();
        installKeyboardActions();
        refreshAll();
    }

    private void buildWindow() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(1440, 900);
        setLocationRelativeTo(null);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BACKGROUND);

        getContentPane().add(new NavigationPanel(this), BorderLayout.WEST);
        getContentPane().add(topStatus, BorderLayout.NORTH);
        cards.setBackground(Theme.BACKGROUND);
        cards.setBorder(BorderFactory.createEmptyBorder());
        addCard(NavigationPanel.DASHBOARD, dashboard);
        addCard(NavigationPanel.DOCUMENTS, documents);
        addCard(NavigationPanel.ANALYSIS, analysis);
        addCard(NavigationPanel.RESULTS, results);
        addCard(NavigationPanel.RANKING, ranking);
        addCard(NavigationPanel.GRAPH, graph);
        addCard(NavigationPanel.REVIEWERS, reviewers);
        addCard(NavigationPanel.REPORTS, reports);
        addCard(NavigationPanel.SETTINGS, settings);
        getContentPane().add(cards, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestShutdown();
            }
        });
    }

    private void addCard(String name, JComponent component) {
        cards.add(component, name);
    }

    private void installKeyboardActions() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-task");
        root.getActionMap().put("cancel-task", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (controller.cancelCurrentTask()) {
                    taskProgress("Cancellation requested…", -1);
                }
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "refresh-view");
        root.getActionMap().put("refresh-view", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!controller.isBusy()) refreshActiveCard();
            }
        });
    }

    @Override
    public void navigateTo(String cardName) {
        activeCard = cardName;
        cardLayout.show(cards, cardName);
        if (!controller.isBusy()) refreshActiveCard();
    }

    @Override
    public void showResult(String caseId) {
        results.showCase(caseId);
        activeCard = NavigationPanel.RESULTS;
        cardLayout.show(cards, NavigationPanel.RESULTS);
    }

    @Override
    public void showReviewerCase(String caseId) {
        reviewers.selectCase(caseId);
        activeCard = NavigationPanel.REVIEWERS;
        cardLayout.show(cards, NavigationPanel.REVIEWERS);
        if (!controller.isBusy()) reviewers.refreshData();
    }

    @Override
    public void refreshAll() {
        if (shutdown || controller.isBusy()) return;
        for (Refreshable refreshable : refreshables) {
            try {
                refreshable.refreshData();
            } catch (RuntimeException exception) {
                controller.logDetailedError("Screen refresh failed", exception);
                statusBar.showError("A screen could not be refreshed. Details were written to the log.");
            }
        }
        updateCorpusStatus();
    }

    private void refreshActiveCard() {
        Refreshable refreshable = switch (activeCard) {
            case NavigationPanel.DOCUMENTS -> documents;
            case NavigationPanel.ANALYSIS -> analysis;
            case NavigationPanel.RESULTS -> results;
            case NavigationPanel.RANKING -> ranking;
            case NavigationPanel.GRAPH -> graph;
            case NavigationPanel.REVIEWERS -> reviewers;
            case NavigationPanel.REPORTS -> reports;
            case NavigationPanel.SETTINGS -> settings;
            default -> dashboard;
        };
        try {
            refreshable.refreshData();
            updateCorpusStatus();
        } catch (RuntimeException exception) {
            controller.logDetailedError("Active screen refresh failed", exception);
            taskFailed("The selected screen could not be refreshed");
        }
    }

    private void updateCorpusStatus() {
        DashboardSnapshot snapshot = controller.dashboard();
        topStatus.setCorpusCounts(snapshot.submissionCount(), snapshot.referenceCount());
    }

    @Override
    public void taskStarted(String message) {
        topStatus.setTaskState(message, -1, true);
        statusBar.showInfo(message);
    }

    @Override
    public void taskProgress(String message, int percentage) {
        topStatus.setTaskState(message, percentage, true);
        statusBar.showInfo(message);
    }

    @Override
    public void taskFinished(String message) {
        topStatus.setTaskState("Ready", 100, false);
        statusBar.showSuccess(message);
    }

    @Override
    public void taskFailed(String message) {
        boolean stillBusy = controller.isBusy();
        topStatus.setTaskState(stillBusy ? "Another operation is still running" : "Ready",
                stillBusy ? -1 : 0, stillBusy);
        statusBar.showError(message);
        JOptionPane.showMessageDialog(this,
                message + ".\n\nTechnical details were written to the error log.",
                "Operation could not be completed", JOptionPane.ERROR_MESSAGE);
    }

    private void requestShutdown() {
        if (shutdown) return;
        String message = controller.isBusy()
                ? "An operation is still running. Cancel it and exit safely?"
                : "Exit the academic integrity application?";
        int choice = JOptionPane.showConfirmDialog(this, message, "Safe shutdown",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        shutdown = true;
        controller.cancelCurrentTask();
        controller.shutdown();
        dispose();
    }
}
