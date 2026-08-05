package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.service.SettingsSnapshot;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Persistent engine configuration plus measured algorithm benchmarks. */
public final class SettingsPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final JSpinner wordShingle = integerSpinner(4, 1, 100);
    private final JSpinner characterShingle = integerSpinner(9, 2, 1000);
    private final JSpinner minimumPhrase = integerSpinner(28, 4, 10000);
    private final JSpinner candidateThreshold = ratioSpinner(0.18);
    private final JSpinner reviewThreshold = ratioSpinner(0.32);
    private final JSpinner graphThreshold = ratioSpinner(0.30);
    private final JSpinner exactWeight = ratioSpinner(0.35);
    private final JSpinner shingleWeight = ratioSpinner(0.30);
    private final JSpinner fuzzyWeight = ratioSpinner(0.25);
    private final JSpinner graphWeight = ratioSpinner(0.10);
    private final JSpinner maxEvidence = integerSpinner(8, 1, 1000);
    private final JSpinner threadCount = integerSpinner(4, 1, 128);
    private final JSpinner maxFileBytes = new JSpinner(
            new SpinnerNumberModel(2_000_000L, 1L, Long.MAX_VALUE, 100_000L));
    private final JCheckBox removeStopwords = new JCheckBox("Remove configured stop words", true);
    private final JCheckBox exactEnabled = new JCheckBox("Exact phrase algorithms", true);
    private final JCheckBox shingleEnabled = new JCheckBox("Shingle similarity", true);
    private final JCheckBox fuzzyEnabled = new JCheckBox("Fuzzy alignment", true);
    private final JCheckBox graphEnabled = new JCheckBox("Graph enrichment", true);
    private final JTextField submissionDirectory = new JTextField(32);
    private final JTextField referenceDirectory = new JTextField(32);
    private final JTextField reportDirectory = new JTextField(32);
    private final JTextField stopwordFile = new JTextField(32);
    private final JTextArea benchmark = new JTextArea();
    private final JLabel benchmarkStatus = Theme.mutedLabel(
            "No benchmark has run in this session. Any empty/sample display is illustrative.");

    public SettingsPanel(ApplicationController controller, UiHost host) {
        super("Settings and benchmarks",
                "Configure the analysis pipeline and compare measured custom algorithm performance.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JTabbedPane buildContent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Analysis settings", buildAnalysisSettings());
        tabs.addTab("Paths and algorithms", buildPathsAndAlgorithms());
        tabs.addTab("Benchmarks", buildBenchmarks());
        return tabs;
    }

    private JPanel buildAnalysisSettings() {
        JPanel root = formPanel();
        GridBagConstraints c = constraints();
        int row = 0;
        addField(root, c, row++, "Word shingle size", wordShingle,
                "Consecutive normalized tokens per word shingle");
        addField(root, c, row++, "Character shingle size", characterShingle,
                "Characters per bounded character shingle");
        addField(root, c, row++, "Minimum exact phrase", minimumPhrase,
                "Minimum phrase length in characters");
        addField(root, c, row++, "Candidate threshold", candidateThreshold,
                "MinHash ratio used for batch shortlisting");
        addField(root, c, row++, "Review threshold", reviewThreshold,
                "Composite ratio queued for reviewer assignment");
        addField(root, c, row++, "Graph edge threshold", graphThreshold,
                "Minimum composite ratio for a relationship edge");
        addField(root, c, row++, "Exact / shingle weights",
                paired(exactWeight, shingleWeight), "Weights are normalized across enabled families");
        addField(root, c, row++, "Fuzzy / graph weights",
                paired(fuzzyWeight, graphWeight), "Weights must have a positive enabled total");
        addField(root, c, row++, "Maximum evidence", maxEvidence,
                "Maximum compact evidence passages retained per case");
        addField(root, c, row++, "Thread count", threadCount,
                "Upper bound for parallel comparison workers");
        addField(root, c, row++, "Maximum file bytes", maxFileBytes,
                "Reject larger input documents during validation");
        addField(root, c, row, "Text preparation", removeStopwords,
                "Use the configured stop-word file during normalization");
        return withSaveBar(root);
    }

    private JPanel buildPathsAndAlgorithms() {
        JPanel root = formPanel();
        GridBagConstraints c = constraints();
        int row = 0;
        addField(root, c, row++, "Submission directory",
                pathChooser(submissionDirectory, true), "Relative paths resolve from the project root");
        addField(root, c, row++, "Reference directory",
                pathChooser(referenceDirectory, true), "Source corpus location");
        addField(root, c, row++, "Report directory",
                pathChooser(reportDirectory, true), "Readable and compressed report output");
        addField(root, c, row++, "Stop-word file",
                pathChooser(stopwordFile, false), "One optional word per UTF-8 line");
        JPanel algorithms = new JPanel(new java.awt.GridLayout(0, 2, 16, 8));
        algorithms.setOpaque(false);
        algorithms.add(exactEnabled);
        algorithms.add(shingleEnabled);
        algorithms.add(fuzzyEnabled);
        algorithms.add(graphEnabled);
        exactEnabled.setToolTipText("KMP, verified Rabin-Karp, Z-algorithm, and Aho-Corasick");
        shingleEnabled.setToolTipText("Word and character shingle similarity");
        fuzzyEnabled.setToolTipText("Smith-Waterman, LCS, and edit distance");
        graphEnabled.setToolTipText("Custom relationship graph signal");
        addField(root, c, row, "Enabled algorithms", algorithms,
                "At least one family must remain enabled");
        return withSaveBar(root);
    }

    private JPanel buildBenchmarks() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.SURFACE);
        header.setBorder(Theme.cardBorder());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton run = Theme.primaryButton("Run actual benchmarks");
        run.setToolTipText("Measure the current JVM, machine, and loaded corpus in the background");
        run.addActionListener(event -> runBenchmarks());
        actions.add(run);
        header.add(actions, BorderLayout.WEST);
        header.add(benchmarkStatus, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);
        benchmark.setEditable(false);
        benchmark.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        benchmark.setBackground(Theme.SURFACE);
        benchmark.setForeground(Theme.TEXT);
        benchmark.setText("ILLUSTRATIVE UNTIL RUN\n\n"
                + "The benchmark view will compare:\n"
                + "  • BST versus AVL insertion\n"
                + "  • sequential versus bounded-parallel analysis\n"
                + "  • merge, quick, heap, counting, and radix sorting\n"
                + "  • exact KMP matching versus fuzzy Smith-Waterman alignment\n\n"
                + "Run the benchmark to replace this note with measured results from the current corpus.");
        JScrollPane scroll = new JScrollPane(benchmark);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        root.add(scroll, BorderLayout.CENTER);
        return root;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(Theme.cardBorder());
        return panel;
    }

    private JPanel withSaveBar(JPanel form) {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setOpaque(false);
        root.add(new JScrollPane(form) {{
            setBorder(BorderFactory.createEmptyBorder());
            getVerticalScrollBar().setUnitIncrement(16);
        }}, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(Theme.SURFACE);
        actions.setBorder(Theme.cardBorder());
        JButton restore = Theme.secondaryButton("Reload saved values");
        restore.addActionListener(event -> refreshData());
        JButton save = Theme.primaryButton("Save settings");
        save.setMnemonic('S');
        save.addActionListener(event -> saveSettings());
        actions.add(restore);
        actions.add(save);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private void saveSettings() {
        SettingsSnapshot snapshot;
        try {
            snapshot = new SettingsSnapshot(intValue(wordShingle), intValue(characterShingle),
                    intValue(minimumPhrase), doubleValue(candidateThreshold),
                    doubleValue(reviewThreshold), doubleValue(graphThreshold),
                    doubleValue(exactWeight), doubleValue(shingleWeight), doubleValue(fuzzyWeight),
                    doubleValue(graphWeight), intValue(maxEvidence), intValue(threadCount),
                    ((Number) maxFileBytes.getValue()).longValue(), removeStopwords.isSelected(),
                    exactEnabled.isSelected(), shingleEnabled.isSelected(), fuzzyEnabled.isSelected(),
                    graphEnabled.isSelected(), submissionDirectory.getText(),
                    referenceDirectory.getText(), reportDirectory.getText(), stopwordFile.getText());
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Check settings",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int decision = JOptionPane.showConfirmDialog(this,
                "Saving settings reloads the configured corpus and clears current analysis results. Continue?",
                "Save and reload", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        host.taskStarted("Saving settings and rebuilding engine services…");
        controller.saveSettingsAsync(snapshot, new UiTaskCallback<>(host, "Settings saved") {
            @Override
            protected void handleSuccess(SettingsSnapshot result) {
                populate(result);
            }
        });
    }

    private void runBenchmarks() {
        host.taskStarted("Running measured algorithm benchmarks…");
        benchmarkStatus.setText("Running actual benchmarks against the current corpus…");
        controller.runBenchmarksAsync(new UiTaskCallback<>(host, "Benchmarks completed") {
            @Override
            protected void handleSuccess(String result) {
                benchmarkStatus.setForeground(Theme.SUCCESS);
                benchmarkStatus.setText("Actual measured results • timings depend on JVM and hardware");
                benchmark.setText(result);
                benchmark.setCaretPosition(0);
            }

            @Override
            protected void handleFailure(String message) {
                benchmarkStatus.setForeground(Theme.DANGER);
                benchmarkStatus.setText("Benchmark run failed; see error logs for details");
            }
        });
    }

    private JPanel pathChooser(JTextField field, boolean directory) {
        JPanel panel = new JPanel(new BorderLayout(7, 0));
        panel.setOpaque(false);
        panel.add(field, BorderLayout.CENTER);
        JButton browse = Theme.secondaryButton("Browse…");
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(directory
                    ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            if (!field.getText().isBlank()) chooser.setSelectedFile(new File(field.getText()));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }

    private JPanel paired(JSpinner first, JSpinner second) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(first);
        panel.add(new JLabel("/"));
        panel.add(second);
        return panel;
    }

    private GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 7, 6, 12);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, String label,
                          java.awt.Component value, String help) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        JLabel name = new JLabel(label);
        name.setFont(Theme.BODY_BOLD);
        panel.add(name, c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(value, c);
        c.gridx = 2;
        c.weightx = 0;
        panel.add(Theme.mutedLabel(help), c);
    }

    private static JSpinner integerSpinner(int value, int minimum, int maximum) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 1));
    }

    private static JSpinner ratioSpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 1.0, 0.01));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        return spinner;
    }

    @Override
    public void refreshData() {
        populate(controller.settings());
    }

    private void populate(SettingsSnapshot settings) {
        wordShingle.setValue(settings.wordShingleSize());
        characterShingle.setValue(settings.characterShingleSize());
        minimumPhrase.setValue(settings.minExactPhraseCharacters());
        candidateThreshold.setValue(settings.candidateThreshold());
        reviewThreshold.setValue(settings.reviewThreshold());
        graphThreshold.setValue(settings.graphEdgeThreshold());
        exactWeight.setValue(settings.exactWeight());
        shingleWeight.setValue(settings.shingleWeight());
        fuzzyWeight.setValue(settings.fuzzyWeight());
        graphWeight.setValue(settings.graphWeight());
        maxEvidence.setValue(settings.maxEvidence());
        threadCount.setValue(settings.workerCount());
        maxFileBytes.setValue(settings.maxFileBytes());
        removeStopwords.setSelected(settings.removeStopwords());
        exactEnabled.setSelected(settings.exactEnabled());
        shingleEnabled.setSelected(settings.shingleEnabled());
        fuzzyEnabled.setSelected(settings.fuzzyEnabled());
        graphEnabled.setSelected(settings.graphEnabled());
        submissionDirectory.setText(settings.submissionDirectory());
        referenceDirectory.setText(settings.referenceDirectory());
        reportDirectory.setText(settings.reportDirectory());
        stopwordFile.setText(settings.stopwordFile());
    }

    private int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private double doubleValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }
}
