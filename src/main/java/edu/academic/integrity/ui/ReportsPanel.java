package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.report.ReportExportSummary;
import edu.academic.integrity.service.LogSeverity;
import edu.academic.integrity.service.RankingQuery;
import edu.academic.integrity.service.ReportEntry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Current report preview, archive browsing, logs, and Huffman workflows. */
public final class ReportsPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final JComboBox<String> cases = new JComboBox<>();
    private final JTextArea reportPreview = textViewer();
    private final JList<ReportEntry> archive = new JList<>();
    private final JComboBox<String> severity = new JComboBox<>(new String[]{
            "All logs", "Activity only", "Errors only"
    });
    private final JTextArea logViewer = textViewer();

    public ReportsPanel(ApplicationController controller, UiHost host) {
        super("Reports and logs",
                "Preview and export evidence reports, inspect prior runs, and manage Huffman archives.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JTabbedPane buildContent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Reports", buildReportsTab());
        tabs.addTab("Activity and error logs", buildLogsTab());
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() == 1) loadLogs();
        });
        return tabs;
    }

    private JPanel buildReportsTab() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        toolbar.setBackground(Theme.SURFACE);
        toolbar.setBorder(Theme.cardBorder());
        cases.setPrototypeDisplayValue("PD-000-SUBMISSION-REFERENCE");
        cases.addActionListener(event -> previewCurrentCase());
        JButton preview = Theme.secondaryButton("Preview case");
        preview.addActionListener(event -> previewCurrentCase());
        JButton export = Theme.primaryButton("Export report packet");
        export.setToolTipText("Write readable and Huffman-compressed reports plus a batch summary");
        export.addActionListener(event -> exportReports(false));
        JButton exportTo = Theme.secondaryButton("Export to…");
        exportTo.addActionListener(event -> exportReports(true));
        toolbar.add(cases);
        toolbar.add(preview);
        toolbar.add(export);
        toolbar.add(exportTo);
        root.add(toolbar, BorderLayout.NORTH);

        JScrollPane previewScroll = new JScrollPane(reportPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Report preview"));

        archive.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archive.setCellRenderer(new ReportRenderer());
        archive.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) openArchivedReport();
        });
        JScrollPane archiveScroll = new JScrollPane(archive);
        archiveScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Previously generated reports"));
        JPanel archivePanel = new JPanel(new BorderLayout(0, 8));
        archivePanel.setOpaque(false);
        archivePanel.add(archiveScroll, BorderLayout.CENTER);
        JPanel archiveActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        archiveActions.setOpaque(false);
        JButton refresh = Theme.secondaryButton("Refresh archive");
        refresh.addActionListener(event -> refreshArchive());
        JButton compress = Theme.secondaryButton("Compress selected");
        compress.setToolTipText("Compress the selected readable report with the custom Huffman codec");
        compress.addActionListener(event -> compressSelected());
        JButton decompress = Theme.secondaryButton("Decompress selected");
        decompress.addActionListener(event -> decompressSelected());
        archiveActions.add(refresh);
        archiveActions.add(compress);
        archiveActions.add(decompress);
        archivePanel.add(archiveActions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewScroll, archivePanel);
        split.setResizeWeight(0.67);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildLogsTab() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setBackground(Theme.SURFACE);
        toolbar.setBorder(Theme.cardBorder());
        severity.setToolTipText("Filter between successful activity and detailed error records");
        severity.addActionListener(event -> loadLogs());
        JButton refresh = Theme.secondaryButton("Refresh logs");
        refresh.addActionListener(event -> loadLogs());
        toolbar.add(severity);
        toolbar.add(refresh);
        root.add(toolbar, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(logViewer);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Most recent 200 lines"));
        root.add(scroll, BorderLayout.CENTER);
        return root;
    }

    private void previewCurrentCase() {
        String caseId = (String) cases.getSelectedItem();
        if (caseId == null || caseId.isBlank()) {
            reportPreview.setText("Run an analysis to preview a case report.");
            return;
        }
        try {
            reportPreview.setText(controller.previewReport(caseId));
            reportPreview.setCaretPosition(0);
        } catch (RuntimeException exception) {
            controller.logDetailedError("Report preview could not be generated", exception);
            host.taskFailed("Unable to preview the selected report");
        }
    }

    private void exportReports(boolean chooseDirectory) {
        File output = null;
        if (chooseDirectory) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose report output directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            output = chooser.getSelectedFile();
        }
        host.taskStarted("Exporting text and Huffman report packet…");
        controller.exportReportsAsync(output, new UiTaskCallback<>(host, "Reports exported") {
            @Override
            protected void handleSuccess(ReportExportSummary result) {
                refreshArchive();
                JOptionPane.showMessageDialog(ReportsPanel.this,
                        result.textReports() + " readable report(s) and "
                                + result.compressedReports() + " compressed report(s) were written.\n\n"
                                + result.summaryPath(),
                        "Report export complete", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private void openArchivedReport() {
        ReportEntry selected = archive.getSelectedValue();
        if (selected == null) return;
        try {
            reportPreview.setText(controller.readReport(selected.file()));
            reportPreview.setCaretPosition(0);
        } catch (Exception exception) {
            controller.logDetailedError("Archived report could not be opened", exception);
            host.taskFailed("Unable to open the selected archived report");
        }
    }

    private void compressSelected() {
        ReportEntry selected = archive.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a readable report first.",
                    "No report selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (selected.compressed()) {
            JOptionPane.showMessageDialog(this, "That report is already Huffman-compressed.",
                    "Already compressed", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(selected.file().getParentFile());
        chooser.setSelectedFile(new File(selected.file().getParentFile(),
                selected.file().getName() + ".huff"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        host.taskStarted("Compressing report with Huffman coding…");
        controller.compressReportAsync(selected.file(), chooser.getSelectedFile(),
                new UiTaskCallback<>(host, "Report compressed") {
                    @Override
                    protected void handleSuccess(File result) {
                        refreshArchive();
                    }
                });
    }

    private void decompressSelected() {
        ReportEntry selected = archive.getSelectedValue();
        if (selected == null || !selected.compressed()) {
            JOptionPane.showMessageDialog(this, "Select a .huff report first.",
                    "Compressed report required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(selected.file().getParentFile());
        String name = selected.name().substring(0, selected.name().length() - 5) + ".decoded.txt";
        chooser.setSelectedFile(new File(selected.file().getParentFile(), name));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        host.taskStarted("Decompressing Huffman report…");
        controller.decompressReportAsync(selected.file(), chooser.getSelectedFile(),
                new UiTaskCallback<>(host, "Report decompressed") {
                    @Override
                    protected void handleSuccess(File result) {
                        refreshArchive();
                    }
                });
    }

    private void refreshArchive() {
        archive.setListData(controller.reportFiles());
    }

    private void loadLogs() {
        LogSeverity filter = switch (severity.getSelectedIndex()) {
            case 1 -> LogSeverity.ACTIVITY;
            case 2 -> LogSeverity.ERROR;
            default -> LogSeverity.ALL;
        };
        try {
            logViewer.setText(controller.logs(filter, 200));
            logViewer.setCaretPosition(0);
        } catch (Exception exception) {
            controller.logDetailedError("Application logs could not be read", exception);
            logViewer.setText("Logs could not be loaded. The detailed failure was written to errors.log.");
            host.taskFailed("Unable to read application logs");
        }
    }

    @Override
    public void refreshData() {
        String selection = (String) cases.getSelectedItem();
        cases.removeAllItems();
        for (AnalysisResult result : controller.ranked(new RankingQuery(true, "", ""))) {
            cases.addItem(result.caseId());
        }
        if (selection != null) cases.setSelectedItem(selection);
        if (cases.getItemCount() > 0 && cases.getSelectedIndex() < 0) cases.setSelectedIndex(0);
        refreshArchive();
        previewCurrentCase();
    }

    private static JTextArea textViewer() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(Theme.SURFACE);
        area.setForeground(Theme.TEXT);
        area.setMargin(new java.awt.Insets(10, 10, 10, 10));
        return area;
    }

    private static final class ReportRenderer extends DefaultListCellRenderer {
        private final SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy, HH:mm");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof ReportEntry report) {
                setText((report.compressed() ? "[HUFF] " : "[TEXT] ")
                        + report.relativePath() + "  •  " + UiFormat.bytes(report.sizeBytes())
                        + "  •  " + date.format(new Date(report.modifiedMillis())));
                setToolTipText(report.file().getAbsolutePath());
            }
            return this;
        }
    }
}
