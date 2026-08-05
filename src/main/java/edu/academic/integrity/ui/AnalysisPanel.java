package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.service.AnalysisRequest;
import edu.academic.integrity.service.AnalysisRun;
import edu.academic.integrity.service.RankingQuery;
import edu.academic.integrity.service.SettingsSnapshot;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Configures and starts real engine analysis without blocking the EDT. */
public final class AnalysisPanel extends ScreenPanel {
    private static final String ALL_REFERENCES = "Complete reference corpus";

    private final ApplicationController controller;
    private final UiHost host;
    private final JComboBox<String> submission = new JComboBox<>();
    private final JComboBox<String> reference = new JComboBox<>();
    private final JSpinner candidateThreshold = ratioSpinner(0.18);
    private final JSpinner reviewThreshold = ratioSpinner(0.32);
    private final JSpinner graphThreshold = ratioSpinner(0.30);
    private final JCheckBox exact = new JCheckBox("Exact phrase family", true);
    private final JCheckBox shingle = new JCheckBox("Word and character shingles", true);
    private final JCheckBox fuzzy = new JCheckBox("Fuzzy alignment", true);
    private final JCheckBox graph = new JCheckBox("Graph enrichment", true);
    private final JRadioButton sequential = new JRadioButton("Sequential");
    private final JRadioButton parallel = new JRadioButton("Bounded parallel", true);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel progressText = Theme.mutedLabel("Ready to analyze");
    private final JButton start = Theme.primaryButton("Start analysis");
    private final JButton cancel = Theme.secondaryButton("Cancel");

    public AnalysisPanel(ApplicationController controller, UiHost host) {
        super("Analysis",
                "Choose a submission, comparison scope, thresholds, and enabled algorithm families.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setOpaque(false);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.SURFACE);
        form.setBorder(Theme.cardBorder());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 7, 7, 12);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addField(form, constraints, row++, "Submission", submission,
                "Select the student document that will be analyzed");
        addField(form, constraints, row++, "Compare against", reference,
                "Choose one source or use every loaded reference document");

        JPanel mode = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        mode.setOpaque(false);
        ButtonGroup modes = new ButtonGroup();
        modes.add(sequential);
        modes.add(parallel);
        sequential.setToolTipText("Process candidate comparisons one at a time");
        parallel.setToolTipText("Use the configured bounded worker pool");
        mode.add(sequential);
        mode.add(parallel);
        addField(form, constraints, row++, "Execution", mode,
                "Parallel mode preserves deterministic result ordering");

        JPanel thresholds = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        thresholds.setOpaque(false);
        thresholds.add(labelled("Candidate", candidateThreshold));
        thresholds.add(labelled("Review", reviewThreshold));
        thresholds.add(labelled("Graph edge", graphThreshold));
        candidateThreshold.setToolTipText("Minimum MinHash estimate used to shortlist corpus pairs");
        reviewThreshold.setToolTipText("Minimum composite score queued for human review");
        graphThreshold.setToolTipText("Minimum composite score required for a graph edge");
        addField(form, constraints, row++, "Thresholds", thresholds,
                "Ratios range from 0.00 to 1.00");

        JPanel algorithms = new JPanel(new java.awt.GridLayout(2, 2, 12, 6));
        algorithms.setOpaque(false);
        exact.setToolTipText("KMP, verified Rabin-Karp, Z-algorithm, and Aho-Corasick");
        shingle.setToolTipText("Word and character shingle Jaccard similarity");
        fuzzy.setToolTipText("Smith-Waterman, LCS, and edit-distance alignment");
        graph.setToolTipText("Relationship graph signal derived from custom graph algorithms");
        algorithms.add(exact);
        algorithms.add(shingle);
        algorithms.add(fuzzy);
        algorithms.add(graph);
        addField(form, constraints, row++, "Algorithms", algorithms,
                "Disabled families are not executed and receive zero score weight");
        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 8));
        footer.setBackground(Theme.SURFACE);
        footer.setBorder(Theme.cardBorder());
        progress.setStringPainted(true);
        progress.setValue(0);
        footer.add(progressText, BorderLayout.NORTH);
        footer.add(progress, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        start.setMnemonic('S');
        start.addActionListener(event -> startAnalysis());
        cancel.setEnabled(false);
        cancel.setToolTipText("Request cooperative cancellation at the next safe algorithm boundary");
        cancel.addActionListener(event -> cancelAnalysis());
        buttons.add(cancel);
        buttons.add(start);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private void addField(JPanel panel, GridBagConstraints constraints, int row, String label,
                          java.awt.Component component, String help) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        JLabel name = new JLabel(label);
        name.setFont(Theme.BODY_BOLD);
        name.setForeground(Theme.TEXT);
        panel.add(name, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        panel.add(Theme.mutedLabel(help), constraints);
    }

    private JPanel labelled(String label, JSpinner spinner) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        panel.add(new JLabel(label));
        panel.add(spinner);
        return panel;
    }

    private static JSpinner ratioSpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 1.0, 0.01));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        return spinner;
    }

    private void startAnalysis() {
        String submissionId = (String) submission.getSelectedItem();
        String referenceSelection = (String) reference.getSelectedItem();
        String referenceId = ALL_REFERENCES.equals(referenceSelection) ? null : referenceSelection;
        AnalysisRequest request;
        try {
            request = new AnalysisRequest(submissionId, referenceId, parallel.isSelected(),
                    value(candidateThreshold), value(reviewThreshold), value(graphThreshold),
                    exact.isSelected(), shingle.isSelected(), fuzzy.isSelected(), graph.isSelected());
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Check analysis options",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setRunning(true);
        progress.setIndeterminate(true);
        progressText.setText("Preparing custom indexes and candidate plan…");
        host.taskStarted("Analysis running in the background…");
        controller.runAnalysisAsync(request, new UiTaskCallback<>(host, "Analysis completed") {
            @Override
            protected void handleProgress(edu.academic.integrity.service.ProgressUpdate update) {
                if (update == null) return;
                progress.setIndeterminate(update.total() == 0);
                progress.setValue(update.percent());
                progressText.setText(update.message());
            }

            @Override
            protected void handleSuccess(AnalysisRun run) {
                setRunning(false);
                progress.setIndeterminate(false);
                progress.setValue(100);
                progressText.setText(run.results().length + " comparison(s) completed in "
                        + String.format("%.2f ms", run.elapsedMillis()) + " • memory delta "
                        + UiFormat.bytes(run.memoryDeltaBytes()));
                AnalysisResult[] ranked = controller.ranked(
                        new RankingQuery(true, "", request.submissionId()));
                if (ranked.length > 0) host.showResult(ranked[0].caseId());
                else host.navigateTo(NavigationPanel.RESULTS);
            }

            @Override
            protected void handleFailure(String message) {
                setRunning(false);
                progress.setIndeterminate(false);
                progress.setValue(0);
                progressText.setText("Analysis did not complete: " + message);
            }

            @Override
            protected void handleCancellation() {
                setRunning(false);
                progress.setIndeterminate(false);
                progress.setValue(0);
                progressText.setText("Analysis cancelled safely");
            }
        });
    }

    private void cancelAnalysis() {
        if (controller.cancelCurrentTask()) {
            progressText.setText("Cancellation requested; waiting for a safe algorithm boundary…");
            cancel.setEnabled(false);
        }
    }

    private void setRunning(boolean running) {
        start.setEnabled(!running);
        cancel.setEnabled(running);
        submission.setEnabled(!running);
        reference.setEnabled(!running);
    }

    private double value(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    @Override
    public void refreshData() {
        String selectedSubmission = (String) submission.getSelectedItem();
        String selectedReference = (String) reference.getSelectedItem();
        submission.removeAllItems();
        for (Document document : controller.documents(DocumentType.SUBMISSION)) {
            submission.addItem(document.id());
        }
        reference.removeAllItems();
        reference.addItem(ALL_REFERENCES);
        for (Document document : controller.documents(DocumentType.REFERENCE)) {
            reference.addItem(document.id());
        }
        restoreSelection(submission, selectedSubmission);
        restoreSelection(reference, selectedReference);

        SettingsSnapshot settings = controller.settings();
        candidateThreshold.setValue(settings.candidateThreshold());
        reviewThreshold.setValue(settings.reviewThreshold());
        graphThreshold.setValue(settings.graphEdgeThreshold());
        exact.setSelected(settings.exactEnabled());
        shingle.setSelected(settings.shingleEnabled());
        fuzzy.setSelected(settings.fuzzyEnabled());
        graph.setSelected(settings.graphEnabled());
        boolean ready = submission.getItemCount() > 0 && reference.getItemCount() > 1;
        start.setEnabled(ready && !controller.isBusy());
        if (!ready) progressText.setText("Import at least one submission and one reference document");
    }

    private void restoreSelection(JComboBox<String> combo, String selection) {
        if (selection == null) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (selection.equals(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }
}
