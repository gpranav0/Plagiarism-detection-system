package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.SourceLocation;
import edu.academic.integrity.service.RankingQuery;
import edu.academic.integrity.service.AnalysisRun;
import edu.academic.integrity.service.ResultDetail;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Complete score-and-evidence view for one real engine result. */
public final class ResultsPanel extends ScreenPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String RESULT_CARD = "result";

    private final ApplicationController controller;
    private final UiHost host;
    private final JComboBox<CaseChoice> caseSelector = new JComboBox<>();
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailDeck = new JPanel(detailCards);
    private final EmptyStatePanel emptyState = new EmptyStatePanel(
            "Run an analysis to inspect explainable scores and matched passages.");
    private final PassageComparisonPanel passages = new PassageComparisonPanel();
    private final RiskBadge risk = new RiskBadge();
    private final JLabel composite = new JLabel("0.00%");
    private final JLabel source = valueLabel();
    private final JLabel evidence = valueLabel();
    private final JLabel runtime = valueLabel();
    private final JLabel memory = valueLabel();
    private final ScoreBar exact = new ScoreBar("Exact match");
    private final ScoreBar shingles = new ScoreBar("Shingle similarity");
    private final ScoreBar fuzzy = new ScoreBar("Fuzzy alignment");
    private final ScoreBar graph = new ScoreBar("Graph signal");

    private boolean updatingSelector;
    private String selectedCaseId;

    public ResultsPanel(ApplicationController controller, UiHost host) {
        super("Evidence and results",
                "Inspect every score beside the exact source passages that support it.");
        if (controller == null || host == null) {
            throw new IllegalArgumentException("controller and host are required");
        }
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    /** Selects and displays a case, including cases requested from another screen. */
    public void showCase(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            showEmpty("Select an analysis case to inspect its evidence.");
            return;
        }
        String normalized = caseId.trim();
        try {
            ResultDetail detail = controller.result(normalized);
            if (detail == null) {
                showEmpty("That result is no longer available. Run or refresh the analysis.");
                return;
            }
            selectedCaseId = normalized;
            selectChoice(normalized);
            display(detail);
        } catch (RuntimeException failure) {
            controller.logDetailedError("Result detail could not be loaded", failure);
            showEmpty("The selected result could not be loaded.");
            host.taskFailed("Unable to load the selected analysis result.");
        }
    }

    public String selectedCaseId() {
        return selectedCaseId;
    }

    @Override
    public void refreshData() {
        String requested = selectedCaseId;
        AnalysisResult[] ranked;
        try {
            AnalysisRun run = controller.lastRun();
            ranked = run == null ? controller.ranked(RankingQuery.descending()) : run.results();
        } catch (RuntimeException failure) {
            controller.logDetailedError("Result ranking could not be refreshed", failure);
            ranked = new AnalysisResult[0];
            host.taskFailed("Unable to refresh analysis results.");
        }
        populateChoices(ranked);
        if (ranked.length == 0) {
            selectedCaseId = null;
            showEmpty("Run an analysis to inspect explainable scores and matched passages.");
            return;
        }
        if (requested != null && containsCase(ranked, requested)) {
            showCase(requested);
        } else {
            showCase(ranked[0].caseId());
        }
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.add(buildToolbar(), BorderLayout.NORTH);

        detailDeck.setOpaque(false);
        detailDeck.add(emptyState, EMPTY_CARD);
        detailDeck.add(buildResultCard(), RESULT_CARD);
        root.add(detailDeck, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setBackground(Theme.SURFACE);
        toolbar.setBorder(Theme.cardBorder());

        JPanel selection = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selection.setOpaque(false);
        JLabel label = new JLabel("Analysis case");
        label.setFont(Theme.BODY_BOLD);
        caseSelector.setPrototypeDisplayValue(new CaseChoice("", "SUBMISSION -> REFERENCE  (CRITICAL 100.00%)"));
        caseSelector.setToolTipText("Cases are supplied in custom-index risk order");
        caseSelector.addActionListener(event -> {
            if (updatingSelector) return;
            CaseChoice selected = (CaseChoice) caseSelector.getSelectedItem();
            if (selected != null) showCase(selected.caseId);
        });
        selection.add(label);
        selection.add(caseSelector);
        toolbar.add(selection, BorderLayout.CENTER);

        JButton refresh = Theme.secondaryButton("Refresh results");
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> refreshData());
        toolbar.add(refresh, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildResultCard() {
        JPanel result = new JPanel(new BorderLayout(0, 12));
        result.setOpaque(false);
        result.add(buildOverview(), BorderLayout.NORTH);
        result.add(passages, BorderLayout.CENTER);
        return result;
    }

    private JPanel buildOverview() {
        JPanel overview = new JPanel(new BorderLayout(18, 12));
        overview.setBackground(Theme.SURFACE);
        overview.setBorder(Theme.cardBorder());

        JPanel headline = new JPanel();
        headline.setOpaque(false);
        headline.setLayout(new BoxLayout(headline, BoxLayout.Y_AXIS));
        JLabel caption = Theme.mutedLabel("Composite plagiarism score");
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        composite.setFont(Theme.BODY.deriveFont(Font.BOLD, 32f));
        composite.setForeground(Theme.NAVY);
        composite.setAlignmentX(Component.LEFT_ALIGNMENT);
        risk.setAlignmentX(Component.LEFT_ALIGNMENT);
        headline.add(caption);
        headline.add(Box.createVerticalStrut(4));
        headline.add(composite);
        headline.add(Box.createVerticalStrut(7));
        headline.add(risk);
        overview.add(headline, BorderLayout.WEST);

        JPanel facts = new JPanel(new GridLayout(2, 2, 18, 8));
        facts.setOpaque(false);
        facts.add(fact("Matched source", source));
        facts.add(fact("Visible evidence", evidence));
        facts.add(fact("Case runtime", runtime));
        facts.add(fact("Approximate run memory delta", memory));
        overview.add(facts, BorderLayout.CENTER);

        JPanel scores = new JPanel(new GridLayout(1, 4, 16, 0));
        scores.setOpaque(false);
        scores.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        scores.add(exact);
        scores.add(shingles);
        scores.add(fuzzy);
        scores.add(graph);
        overview.add(scores, BorderLayout.SOUTH);
        return overview;
    }

    private JPanel fact(String captionText, JLabel value) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel caption = Theme.mutedLabel(captionText);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(caption);
        panel.add(Box.createVerticalStrut(2));
        panel.add(value);
        return panel;
    }

    private void display(ResultDetail detail) {
        AnalysisResult result = detail.result();
        composite.setText(UiFormat.percent(result.score().total()));
        risk.setRisk(result.score().riskLabel());
        source.setText(result.reference().id() + " - " + result.reference().title());
        evidence.setText(evidenceSummary(result));
        runtime.setText(UiFormat.millis(result.elapsedNanos()));
        memory.setText(UiFormat.bytes(detail.approximateMemoryDeltaBytes()) + " (approx.)");
        exact.setScore(result.score().exactMatch());
        shingles.setScore(result.score().shingleSimilarity());
        fuzzy.setScore(result.score().fuzzyAlignment());
        graph.setScore(result.score().graphSignal());
        passages.setResult(result);
        detailCards.show(detailDeck, RESULT_CARD);
        revalidate();
        repaint();
    }

    private String evidenceSummary(AnalysisResult result) {
        PassageMatch[] matches = result.evidence();
        if (matches.length == 0) return "No passage ranges retained";
        int minimumSubmissionLine = Integer.MAX_VALUE;
        int maximumSubmissionLine = 0;
        int minimumReferenceLine = Integer.MAX_VALUE;
        int maximumReferenceLine = 0;
        Document submission = result.submission();
        Document reference = result.reference();
        int count = 0;
        for (PassageMatch match : matches) {
            if (match == null) continue;
            SourceLocation submissionStart = submission.locate(match.submissionStart());
            SourceLocation submissionEnd = submission.locate(lastOffset(
                    match.submissionStart(), match.submissionEnd()));
            SourceLocation referenceStart = reference.locate(match.referenceStart());
            SourceLocation referenceEnd = reference.locate(lastOffset(
                    match.referenceStart(), match.referenceEnd()));
            minimumSubmissionLine = Math.min(minimumSubmissionLine, submissionStart.line());
            maximumSubmissionLine = Math.max(maximumSubmissionLine, submissionEnd.line());
            minimumReferenceLine = Math.min(minimumReferenceLine, referenceStart.line());
            maximumReferenceLine = Math.max(maximumReferenceLine, referenceEnd.line());
            count++;
        }
        if (count == 0) return "No passage ranges retained";
        return count + " passage" + (count == 1 ? "" : "s")
                + "; submission lines " + minimumSubmissionLine + "-" + maximumSubmissionLine
                + ", source lines " + minimumReferenceLine + "-" + maximumReferenceLine;
    }

    private int lastOffset(int start, int endExclusive) {
        return endExclusive > start ? endExclusive - 1 : start;
    }

    private void populateChoices(AnalysisResult[] results) {
        updatingSelector = true;
        try {
            caseSelector.removeAllItems();
            for (AnalysisResult result : results) {
                String text = result.submission().id() + " -> " + result.reference().id()
                        + "  (" + result.score().riskLabel() + " "
                        + UiFormat.percent(result.score().total()) + ")";
                caseSelector.addItem(new CaseChoice(result.caseId(), text));
            }
            caseSelector.setEnabled(results.length > 0);
        } finally {
            updatingSelector = false;
        }
    }

    private void selectChoice(String caseId) {
        updatingSelector = true;
        try {
            for (int i = 0; i < caseSelector.getItemCount(); i++) {
                CaseChoice choice = caseSelector.getItemAt(i);
                if (choice.caseId.equals(caseId)) {
                    caseSelector.setSelectedIndex(i);
                    return;
                }
            }
        } finally {
            updatingSelector = false;
        }
    }

    private boolean containsCase(AnalysisResult[] results, String caseId) {
        for (AnalysisResult result : results) {
            if (result.caseId().equals(caseId)) return true;
        }
        return false;
    }

    private void showEmpty(String message) {
        emptyState.setMessage(message);
        passages.setResult(null);
        detailCards.show(detailDeck, EMPTY_CARD);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(Theme.BODY_BOLD);
        label.setForeground(Theme.TEXT);
        return label;
    }

    private static final class CaseChoice {
        private final String caseId;
        private final String text;

        private CaseChoice(String caseId, String text) {
            this.caseId = caseId;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
