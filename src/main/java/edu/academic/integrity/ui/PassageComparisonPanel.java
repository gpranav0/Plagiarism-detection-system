package edu.academic.integrity.ui;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.MatchType;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.SourceLocation;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Explainable side-by-side evidence view backed only by an {@link AnalysisResult}.
 * Original UTF-16 offsets are clamped and mapped to Swing's normalized line endings.
 */
public final class PassageComparisonPanel extends JPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String RESULT_CARD = "result";
    private static final String EVIDENCE_EMPTY_CARD = "evidence-empty";
    private static final String EVIDENCE_LIST_CARD = "evidence-list";

    private static final Highlighter.HighlightPainter EXACT_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.EXACT);
    private static final Highlighter.HighlightPainter SHINGLE_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.MODIFIED);
    private static final Highlighter.HighlightPainter FUZZY_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(Theme.FUZZY);

    private final CardLayout cards = new CardLayout();
    private final JPanel resultCard = new JPanel(new BorderLayout(0, 12));
    private final JLabel caseLabel = new JLabel();
    private final JLabel pairLabel = Theme.mutedLabel("");
    private final JLabel compositeLabel = new JLabel();
    private final RiskBadge riskBadge = new RiskBadge();
    private final JTextPane submissionPane = createTextPane("Submitted passage");
    private final JTextPane referencePane = createTextPane("Source passage");
    private final JLabel submissionHeading = new JLabel("Submission");
    private final JLabel referenceHeading = new JLabel("Source");
    private final EvidenceListModel evidenceModel = new EvidenceListModel();
    private final JList<PassageMatch> evidenceList = new JList<>(evidenceModel);
    private final CardLayout evidenceCards = new CardLayout();
    private final JPanel evidenceBody = new JPanel(evidenceCards);

    private AnalysisResult result;
    private DisplayText submissionDisplay = DisplayText.empty();
    private DisplayText referenceDisplay = DisplayText.empty();

    public PassageComparisonPanel() {
        this(null);
    }

    public PassageComparisonPanel(AnalysisResult initialResult) {
        super();
        setLayout(cards);
        setBackground(Theme.BACKGROUND);
        add(new EmptyStatePanel("Select an analysis result to inspect its passage evidence."),
                EMPTY_CARD);
        buildResultCard();
        add(resultCard, RESULT_CARD);
        setResult(initialResult);
    }

    public void setResult(AnalysisResult analysisResult) {
        result = analysisResult;
        if (analysisResult == null) {
            evidenceModel.setValues(new PassageMatch[0]);
            submissionPane.setText("");
            referencePane.setText("");
            cards.show(this, EMPTY_CARD);
            return;
        }

        caseLabel.setText(analysisResult.caseId());
        pairLabel.setText(analysisResult.submission().id() + " compared with "
                + analysisResult.reference().id());
        compositeLabel.setText("Composite " + UiFormat.percent(analysisResult.score().total()));
        riskBadge.setRisk(analysisResult.score().riskLabel());
        submissionHeading.setText("Submission  -  " + analysisResult.submission().id());
        referenceHeading.setText("Source  -  " + analysisResult.reference().id());

        submissionDisplay = DisplayText.from(analysisResult.submission().content());
        referenceDisplay = DisplayText.from(analysisResult.reference().content());
        submissionPane.setText(submissionDisplay.text);
        referencePane.setText(referenceDisplay.text);
        submissionPane.setCaretPosition(0);
        referencePane.setCaretPosition(0);

        PassageMatch[] evidence = compactEvidence(analysisResult.evidence());
        evidenceModel.setValues(evidence);
        evidenceCards.show(evidenceBody,
                evidence.length == 0 ? EVIDENCE_EMPTY_CARD : EVIDENCE_LIST_CARD);
        applyHighlights(evidence);
        cards.show(this, RESULT_CARD);
        revalidate();
        repaint();
    }

    public AnalysisResult result() {
        return result;
    }

    public int evidenceCount() {
        return evidenceModel.getSize();
    }

    private void buildResultCard() {
        resultCard.setOpaque(false);
        resultCard.setBorder(Theme.cardBorder());
        resultCard.add(buildSummary(), BorderLayout.NORTH);

        JPanel submission = textColumn(submissionHeading, submissionPane);
        JPanel reference = textColumn(referenceHeading, referencePane);
        JSplitPane passages = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, submission, reference);
        passages.setResizeWeight(0.5);
        passages.setContinuousLayout(true);
        passages.setBorder(null);
        passages.setDividerSize(8);

        evidenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        evidenceList.setCellRenderer(new EvidenceRenderer());
        evidenceList.setVisibleRowCount(4);
        evidenceList.setBackground(Theme.SURFACE);
        evidenceList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) reveal(evidenceList.getSelectedValue());
        });
        Theme.accessibleName(evidenceList, "Matched evidence",
                "Exact, modified, and fuzzy passage evidence with source line ranges");

        JScrollPane evidenceScroll = new JScrollPane(evidenceList);
        evidenceScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        evidenceBody.setOpaque(false);
        evidenceBody.add(evidenceScroll, EVIDENCE_LIST_CARD);
        evidenceBody.add(new EmptyStatePanel(
                "No passage evidence met the configured evidence criteria."),
                EVIDENCE_EMPTY_CARD);

        JPanel evidencePanel = new JPanel(new BorderLayout(0, 7));
        evidencePanel.setOpaque(false);
        JLabel evidenceHeading = new JLabel("Visible evidence and line ranges");
        evidenceHeading.setFont(Theme.BODY_BOLD);
        evidenceHeading.setForeground(Theme.TEXT);
        evidencePanel.add(evidenceHeading, BorderLayout.NORTH);
        evidencePanel.add(evidenceBody, BorderLayout.CENTER);
        evidencePanel.setPreferredSize(new Dimension(320, 172));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, passages, evidencePanel);
        mainSplit.setResizeWeight(0.72);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        resultCard.add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel buildSummary() {
        JPanel summary = new JPanel(new BorderLayout(14, 0));
        summary.setOpaque(false);

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        caseLabel.setFont(Theme.SECTION);
        caseLabel.setForeground(Theme.TEXT);
        caseLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pairLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        labels.add(caseLabel);
        labels.add(Box.createVerticalStrut(3));
        labels.add(pairLabel);
        summary.add(labels, BorderLayout.WEST);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        legend.setOpaque(false);
        legend.add(legendItem("Exact", Theme.EXACT));
        legend.add(legendItem("Modified", Theme.MODIFIED));
        legend.add(legendItem("Fuzzy", Theme.FUZZY));
        summary.add(legend, BorderLayout.CENTER);

        JPanel score = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        score.setOpaque(false);
        compositeLabel.setFont(Theme.BODY_BOLD);
        compositeLabel.setForeground(Theme.TEXT);
        score.add(compositeLabel);
        score.add(riskBadge);
        summary.add(score, BorderLayout.EAST);
        return summary;
    }

    private JPanel legendItem(String text, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        item.setOpaque(false);
        JLabel swatch = new JLabel("  ");
        swatch.setOpaque(true);
        swatch.setBackground(color);
        swatch.setBorder(BorderFactory.createLineBorder(color.darker()));
        JLabel label = Theme.mutedLabel(text);
        item.add(swatch);
        item.add(label);
        return item;
    }

    private JPanel textColumn(JLabel heading, JTextPane pane) {
        JPanel column = new JPanel(new BorderLayout(0, 7));
        column.setOpaque(false);
        heading.setFont(Theme.BODY_BOLD);
        heading.setForeground(Theme.TEXT);
        column.add(heading, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        column.add(scroll, BorderLayout.CENTER);
        return column;
    }

    private static JTextPane createTextPane(String accessibleName) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(Theme.SURFACE);
        pane.setForeground(Theme.TEXT);
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        pane.setMargin(new java.awt.Insets(12, 12, 12, 12));
        pane.setCaretColor(Theme.BLUE);
        Theme.accessibleName(pane, accessibleName,
                "Read-only document text with color-highlighted matching passages");
        return pane;
    }

    private void applyHighlights(PassageMatch[] evidence) {
        submissionPane.getHighlighter().removeAllHighlights();
        referencePane.getHighlighter().removeAllHighlights();
        // Paint broad fuzzy evidence first so exact evidence remains visually strongest on overlaps.
        addHighlights(evidence, MatchType.FUZZY, FUZZY_PAINTER);
        addHighlights(evidence, MatchType.SHINGLE, SHINGLE_PAINTER);
        addHighlights(evidence, MatchType.EXACT, EXACT_PAINTER);
        addHighlights(evidence, MatchType.MULTI_PATTERN, EXACT_PAINTER);
    }

    private void addHighlights(PassageMatch[] evidence, MatchType type,
                               Highlighter.HighlightPainter painter) {
        for (PassageMatch match : evidence) {
            if (match != null && match.type() == type) {
                highlight(submissionPane, submissionDisplay,
                        match.submissionStart(), match.submissionEnd(), painter);
                highlight(referencePane, referenceDisplay,
                        match.referenceStart(), match.referenceEnd(), painter);
            }
        }
    }

    private void highlight(JTextPane pane, DisplayText display, int originalStart,
                           int originalEnd, Highlighter.HighlightPainter painter) {
        int start = display.displayOffset(originalStart);
        int end = display.displayOffset(originalEnd);
        int length = pane.getDocument().getLength();
        start = Math.max(0, Math.min(length, start));
        end = Math.max(start, Math.min(length, end));
        if (end <= start) return;
        try {
            pane.getHighlighter().addHighlight(start, end, painter);
        } catch (BadLocationException ignored) {
            // Bounds are clamped above; a concurrently replaced document is safely ignored.
        }
    }

    private void reveal(PassageMatch match) {
        if (match == null) return;
        moveCaret(submissionPane, submissionDisplay.displayOffset(match.submissionStart()));
        moveCaret(referencePane, referenceDisplay.displayOffset(match.referenceStart()));
    }

    private void moveCaret(JTextPane pane, int offset) {
        int safe = Math.max(0, Math.min(pane.getDocument().getLength(), offset));
        pane.setCaretPosition(safe);
    }

    private String rangeText(PassageMatch match) {
        Document submission = result.submission();
        Document reference = result.reference();
        SourceLocation submissionStart = submission.locate(match.submissionStart());
        SourceLocation submissionEnd = submission.locate(lastMatchedOffset(
                match.submissionStart(), match.submissionEnd()));
        SourceLocation referenceStart = reference.locate(match.referenceStart());
        SourceLocation referenceEnd = reference.locate(lastMatchedOffset(
                match.referenceStart(), match.referenceEnd()));
        return "Submission lines " + submissionStart.line() + "-" + submissionEnd.line()
                + "  |  Source lines " + referenceStart.line() + "-" + referenceEnd.line();
    }

    private int lastMatchedOffset(int start, int endExclusive) {
        return endExclusive > start ? endExclusive - 1 : start;
    }

    private PassageMatch[] compactEvidence(PassageMatch[] source) {
        if (source == null) return new PassageMatch[0];
        int count = 0;
        for (PassageMatch match : source) if (match != null) count++;
        PassageMatch[] result = new PassageMatch[count];
        int output = 0;
        for (PassageMatch match : source) if (match != null) result[output++] = match;
        return result;
    }

    private final class EvidenceRenderer extends JPanel
            implements ListCellRenderer<PassageMatch> {
        private final JLabel primary = new JLabel();
        private final JLabel secondary = new JLabel();
        private final JLabel percentage = new JLabel();

        private EvidenceRenderer() {
            super(new BorderLayout(10, 3));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            primary.setFont(Theme.BODY_BOLD);
            secondary.setFont(Theme.SMALL);
            secondary.setForeground(Theme.MUTED);
            labels.add(primary);
            labels.add(Box.createVerticalStrut(2));
            labels.add(secondary);
            percentage.setFont(Theme.BODY_BOLD);
            percentage.setHorizontalAlignment(SwingConstants.RIGHT);
            add(labels, BorderLayout.CENTER);
            add(percentage, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PassageMatch> list,
                PassageMatch match, int index, boolean selected, boolean focused) {
            String algorithm = match.algorithm().isBlank() ? match.type().name() : match.algorithm();
            primary.setText(match.type().name().replace('_', ' ') + "  -  " + algorithm);
            secondary.setText(rangeText(match));
            percentage.setText(UiFormat.percent(match.similarity()));
            percentage.setForeground(colorFor(match.type()));
            setBackground(selected ? Theme.BLUE_LIGHT : Theme.SURFACE);
            setOpaque(true);
            return this;
        }

        private Color colorFor(MatchType type) {
            return switch (type) {
                case EXACT, MULTI_PATTERN -> Theme.WARNING;
                case SHINGLE -> Theme.BLUE;
                case FUZZY -> Theme.SUCCESS;
            };
        }
    }

    private static final class EvidenceListModel extends AbstractListModel<PassageMatch> {
        private PassageMatch[] values = new PassageMatch[0];

        @Override
        public int getSize() {
            return values.length;
        }

        @Override
        public PassageMatch getElementAt(int index) {
            return values[index];
        }

        private void setValues(PassageMatch[] source) {
            int oldSize = values.length;
            values = source == null ? new PassageMatch[0] : copy(source);
            if (oldSize > 0) fireIntervalRemoved(this, 0, oldSize - 1);
            if (values.length > 0) fireIntervalAdded(this, 0, values.length - 1);
        }

        private PassageMatch[] copy(PassageMatch[] source) {
            PassageMatch[] copy = new PassageMatch[source.length];
            System.arraycopy(source, 0, copy, 0, source.length);
            return copy;
        }
    }

    /** Text plus boundary mapping after CRLF/lone-CR normalization for Swing display. */
    private static final class DisplayText {
        private final String text;
        private final int[] originalToDisplay;

        private DisplayText(String text, int[] originalToDisplay) {
            this.text = text;
            this.originalToDisplay = originalToDisplay;
        }

        private static DisplayText empty() {
            return new DisplayText("", new int[]{0});
        }

        private static DisplayText from(String source) {
            if (source == null || source.isEmpty()) return empty();
            StringBuilder displayed = new StringBuilder(source.length());
            int[] mapping = new int[source.length() + 1];
            int original = 0;
            mapping[0] = 0;
            while (original < source.length()) {
                char current = source.charAt(original);
                if (current == '\r') {
                    displayed.append('\n');
                    mapping[original + 1] = displayed.length();
                    original++;
                    if (original < source.length() && source.charAt(original) == '\n') {
                        mapping[original + 1] = displayed.length();
                        original++;
                    }
                } else {
                    displayed.append(current);
                    mapping[original + 1] = displayed.length();
                    original++;
                }
            }
            return new DisplayText(displayed.toString(), mapping);
        }

        private int displayOffset(int originalOffset) {
            int safe = Math.max(0, Math.min(originalToDisplay.length - 1, originalOffset));
            return originalToDisplay[safe];
        }
    }
}
