package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.Reviewer;
import edu.academic.integrity.service.AssignmentPlan;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Capacity-aware maximum-flow assignment with safe manual overrides. */
public final class ReviewerAssignmentPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final StatCard reviewerCount = new StatCard("Available reviewers");
    private final StatCard totalCapacity = new StatCard("Total capacity");
    private final StatCard assignedCount = new StatCard("Assigned cases");
    private final StatCard unassignedCount = new StatCard("Unassigned cases");
    private final StatCard maxFlow = new StatCard("Maximum-flow result");
    private final DefaultTableModel reviewerModel = tableModel(
            "Reviewer", "Name", "Assigned", "Capacity", "Remaining", "Utilization");
    private final DefaultTableModel assignmentModel = tableModel(
            "Case", "Submission", "Score", "Risk", "Reviewer");
    private final DefaultTableModel unassignedModel = tableModel(
            "Case", "Submission", "Score", "Risk");
    private final JTable reviewerTable = new JTable(reviewerModel);
    private final JTable assignmentTable = new JTable(assignmentModel);
    private final JTable unassignedTable = new JTable(unassignedModel);
    private AssignmentPlan plan;
    private String requestedCaseId;

    public ReviewerAssignmentPanel(ApplicationController controller, UiHost host) {
        super("Reviewer assignment",
                "Route reviewable cases with Edmonds–Karp maximum flow and explicit capacity limits.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        JPanel metrics = new JPanel(new GridLayout(1, 5, 10, 0));
        metrics.setOpaque(false);
        metrics.add(reviewerCount);
        metrics.add(totalCapacity);
        metrics.add(assignedCount);
        metrics.add(unassignedCount);
        metrics.add(maxFlow);
        root.add(metrics, BorderLayout.NORTH);

        configureTable(reviewerTable);
        configureTable(assignmentTable);
        configureTable(unassignedTable);
        JScrollPane reviewerScroll = titledScroll(reviewerTable, "Reviewer capacity and utilization");
        JTabbedPaneWithCases cases = new JTabbedPaneWithCases(
                titledScroll(assignmentTable, "Committed and suggested assignments"),
                titledScroll(unassignedTable, "Reviewable cases awaiting capacity"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reviewerScroll, cases);
        split.setResizeWeight(0.38);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);
        root.add(buildActions(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(Theme.SURFACE);
        actions.setBorder(Theme.cardBorder());
        JButton suggest = Theme.primaryButton("Suggest assignments");
        suggest.setToolTipText("Run the real maximum-flow assignment using current capacities");
        suggest.addActionListener(event -> suggestAssignments());
        JButton override = Theme.secondaryButton("Manual override");
        override.setToolTipText("Assign the selected case while still enforcing reviewer capacity");
        override.addActionListener(event -> manualOverride());
        JButton unassign = Theme.secondaryButton("Unassign selected");
        unassign.addActionListener(event -> unassignSelected());
        JButton inspect = Theme.secondaryButton("Inspect evidence");
        inspect.addActionListener(event -> inspectSelected());
        JButton export = Theme.secondaryButton("Export assignments");
        export.addActionListener(event -> exportAssignments());
        actions.add(inspect);
        actions.add(unassign);
        actions.add(override);
        actions.add(export);
        actions.add(suggest);
        return actions;
    }

    private void suggestAssignments() {
        host.taskStarted("Computing maximum-flow reviewer assignments…");
        controller.suggestAssignmentsAsync(new UiTaskCallback<>(host, "Reviewer assignments ready") {
            @Override
            protected void handleSuccess(AssignmentPlan result) {
                plan = result;
                populate(result);
            }
        });
    }

    private void manualOverride() {
        if (plan == null || plan.reviewers().length == 0) {
            JOptionPane.showMessageDialog(this, "No reviewers are configured.",
                    "Manual assignment unavailable", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String caseId = selectedCaseId();
        if (caseId == null) {
            JOptionPane.showMessageDialog(this, "Select an assigned or unassigned case first.",
                    "No case selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Reviewer[] reviewers = plan.reviewers();
        String[] choices = new String[reviewers.length];
        for (int i = 0; i < reviewers.length; i++) {
            choices[i] = reviewers[i].id() + " — " + reviewers[i].name()
                    + " (capacity " + reviewers[i].capacity() + ")";
        }
        JComboBox<String> reviewer = new JComboBox<>(choices);
        JPanel prompt = new JPanel(new BorderLayout(0, 8));
        prompt.add(new JLabel("Assign case " + caseId + " to:"), BorderLayout.NORTH);
        prompt.add(reviewer, BorderLayout.CENTER);
        int decision = JOptionPane.showConfirmDialog(this, prompt, "Manual reviewer override",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        String reviewerId = reviewers[reviewer.getSelectedIndex()].id();
        host.taskStarted("Applying reviewer override…");
        controller.overrideAssignmentAsync(caseId, reviewerId,
                new UiTaskCallback<>(host, "Reviewer override applied") {
                    @Override
                    protected void handleSuccess(AssignmentPlan result) {
                        plan = result;
                        populate(result);
                    }
                });
    }

    private void unassignSelected() {
        int row = assignmentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an assigned case first.",
                    "No assignment selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String caseId = String.valueOf(assignmentModel.getValueAt(row, 0));
        int decision = JOptionPane.showConfirmDialog(this,
                "Remove the reviewer assignment for “" + caseId + "”?",
                "Unassign case", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        host.taskStarted("Removing reviewer assignment…");
        controller.unassignAsync(caseId, new UiTaskCallback<>(host, "Case unassigned") {
            @Override
            protected void handleSuccess(AssignmentPlan result) {
                plan = result;
                populate(result);
            }
        });
    }

    private void inspectSelected() {
        String caseId = selectedCaseId();
        if (caseId != null) host.showResult(caseId);
    }

    private void exportAssignments() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export reviewer assignments");
        chooser.setSelectedFile(new File("reviewer-assignments.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        host.taskStarted("Exporting reviewer assignments…");
        controller.exportAssignmentsAsync(chooser.getSelectedFile(),
                new UiTaskCallback<>(host, "Assignments exported") {
                    @Override
                    protected void handleSuccess(File result) {
                        JOptionPane.showMessageDialog(ReviewerAssignmentPanel.this,
                                "Assignments were saved to:\n" + result.getAbsolutePath(),
                                "Export complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
    }

    public void selectCase(String caseId) {
        requestedCaseId = caseId;
        selectRequestedCase();
    }

    private void selectRequestedCase() {
        if (requestedCaseId == null) return;
        if (selectRow(assignmentTable, assignmentModel, requestedCaseId)) return;
        selectRow(unassignedTable, unassignedModel, requestedCaseId);
    }

    private boolean selectRow(JTable table, DefaultTableModel model, String caseId) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (caseId.equals(String.valueOf(model.getValueAt(row, 0)))) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return true;
            }
        }
        return false;
    }

    private String selectedCaseId() {
        int unassignedRow = unassignedTable.getSelectedRow();
        if (unassignedRow >= 0) {
            return String.valueOf(unassignedModel.getValueAt(unassignedRow, 0));
        }
        int assignmentRow = assignmentTable.getSelectedRow();
        return assignmentRow < 0 ? requestedCaseId
                : String.valueOf(assignmentModel.getValueAt(assignmentRow, 0));
    }

    @Override
    public void refreshData() {
        plan = controller.reviewers();
        populate(plan);
    }

    private void populate(AssignmentPlan current) {
        reviewerModel.setRowCount(0);
        assignmentModel.setRowCount(0);
        unassignedModel.setRowCount(0);
        int capacity = 0;
        for (AssignmentPlan.ReviewerUtilization value : current.utilization()) {
            Reviewer reviewer = value.reviewer();
            capacity += reviewer.capacity();
            reviewerModel.addRow(new Object[]{reviewer.id(), reviewer.name(), value.assignedCases(),
                    reviewer.capacity(), value.remainingCapacity(),
                    UiFormat.percent(value.utilizationRatio())});
        }
        for (CaseAssignment assignment : current.assignments()) {
            AnalysisResult result = assignment.result();
            assignmentModel.addRow(new Object[]{result.caseId(), result.submission().id(),
                    UiFormat.percent(result.score().total()), result.score().riskLabel(),
                    assignment.reviewer().id() + " — " + assignment.reviewer().name()});
        }
        for (AnalysisResult result : current.unassigned()) {
            unassignedModel.addRow(new Object[]{result.caseId(), result.submission().id(),
                    UiFormat.percent(result.score().total()), result.score().riskLabel()});
        }
        reviewerCount.setValue(String.valueOf(current.reviewers().length));
        totalCapacity.setValue(String.valueOf(capacity));
        assignedCount.setValue(String.valueOf(current.assignedCount()));
        unassignedCount.setValue(String.valueOf(current.unassignedCount()));
        maxFlow.setValue(String.valueOf(current.maxFlowCount()));
        maxFlow.setDetail("Cases routed by Edmonds–Karp");
        selectRequestedCase();
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private void configureTable(JTable table) {
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(27);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
    }

    private JScrollPane titledScroll(JTable table, String title) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), title));
        return scroll;
    }

    /** Tiny tab wrapper keeps the two case sets visually separate. */
    private static final class JTabbedPaneWithCases extends javax.swing.JTabbedPane {
        private JTabbedPaneWithCases(JScrollPane assignments, JScrollPane unassigned) {
            addTab("Assignments", assignments);
            addTab("Awaiting capacity", unassigned);
        }
    }
}
