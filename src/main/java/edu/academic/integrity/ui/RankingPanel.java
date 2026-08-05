package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.service.RankingQuery;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** Suspicion ranking sourced from the custom AVL tree/max heap result index. */
public final class RankingPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final ResultTableModel model = new ResultTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> order = new JComboBox<>(new String[]{
            "Highest score first", "Lowest score first"
    });
    private final JComboBox<String> risk = new JComboBox<>(new String[]{
            "All risk levels", "CRITICAL", "HIGH", "MEDIUM", "LOW"
    });
    private final JTextField documentId = new JTextField(14);
    private final JTextArea details = new JTextArea();
    private final JButton evidence = Theme.primaryButton("Inspect evidence");
    private final JButton review = Theme.secondaryButton("Select for review");

    public RankingPanel(ApplicationController controller, UiHost host) {
        super("Risk ranking",
                "Review strongest submission matches ordered by the project’s custom AVL tree and heap.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.add(buildToolbar(), BorderLayout.NORTH);

        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateDetails();
        });
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(4).setMaxWidth(90);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JPanel detailPanel = new JPanel(new BorderLayout(0, 10));
        detailPanel.setPreferredSize(new Dimension(300, 300));
        detailPanel.setBackground(Theme.SURFACE);
        detailPanel.setBorder(Theme.cardBorder());
        JLabel title = new JLabel("Score details");
        title.setFont(Theme.SECTION);
        detailPanel.add(title, BorderLayout.NORTH);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBackground(Theme.SURFACE);
        details.setForeground(Theme.TEXT);
        details.setText("Select a ranked submission to inspect its strongest match.");
        detailPanel.add(details, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setOpaque(false);
        evidence.setEnabled(false);
        review.setEnabled(false);
        evidence.addActionListener(event -> openEvidence());
        review.addActionListener(event -> sendToReview());
        actions.add(evidence);
        actions.add(review);
        detailPanel.add(actions, BorderLayout.SOUTH);

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        split.setResizeWeight(0.76);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setBackground(Theme.SURFACE);
        toolbar.setBorder(Theme.cardBorder());
        order.setToolTipText("Ordering is requested from the custom result index");
        risk.setToolTipText("Filter the custom-ranked snapshot by risk classification");
        documentId.setToolTipText("Exact submission document ID");
        order.addActionListener(event -> refreshData());
        risk.addActionListener(event -> refreshData());
        documentId.addActionListener(event -> refreshData());
        JButton apply = Theme.secondaryButton("Apply");
        apply.addActionListener(event -> refreshData());
        JButton clear = Theme.secondaryButton("Clear filters");
        clear.addActionListener(event -> {
            order.setSelectedIndex(0);
            risk.setSelectedIndex(0);
            documentId.setText("");
            refreshData();
        });
        toolbar.add(new JLabel("Order"));
        toolbar.add(order);
        toolbar.add(new JLabel("Risk"));
        toolbar.add(risk);
        toolbar.add(new JLabel("Submission ID"));
        toolbar.add(documentId);
        toolbar.add(apply);
        toolbar.add(clear);
        return toolbar;
    }

    private void updateDetails() {
        AnalysisResult result = selectedResult();
        boolean selected = result != null;
        evidence.setEnabled(selected);
        review.setEnabled(selected);
        if (!selected) {
            details.setText("No ranked case is selected.");
            return;
        }
        details.setText("Case: " + result.caseId() + "\n"
                + "Submission: " + result.submission().id() + " — "
                + result.submission().title() + "\n"
                + "Strongest source: " + result.reference().id() + "\n\n"
                + "Composite: " + UiFormat.percent(result.score().total()) + "\n"
                + "Risk: " + result.score().riskLabel() + "\n"
                + "Exact: " + UiFormat.percent(result.score().exactMatch()) + "\n"
                + "Shingles: " + UiFormat.percent(result.score().shingleSimilarity()) + "\n"
                + "Fuzzy: " + UiFormat.percent(result.score().fuzzyAlignment()) + "\n"
                + "Graph: " + UiFormat.percent(result.score().graphSignal()) + "\n\n"
                + result.evidence().length + " visible evidence passage(s)");
        details.setCaretPosition(0);
    }

    private AnalysisResult selectedResult() {
        return model.resultAt(table.getSelectedRow());
    }

    private void openEvidence() {
        AnalysisResult result = selectedResult();
        if (result != null) host.showResult(result.caseId());
    }

    private void sendToReview() {
        AnalysisResult result = selectedResult();
        if (result != null) host.showReviewerCase(result.caseId());
    }

    @Override
    public void refreshData() {
        String riskSelection = (String) risk.getSelectedItem();
        String riskFilter = risk.getSelectedIndex() == 0 ? "" : riskSelection;
        RankingQuery query = new RankingQuery(order.getSelectedIndex() == 0,
                riskFilter, documentId.getText().trim());
        AnalysisResult[] results = controller.ranked(query);
        model.setResults(results);
        if (results.length > 0) table.setRowSelectionInterval(0, 0);
        else updateDetails();
    }
}
