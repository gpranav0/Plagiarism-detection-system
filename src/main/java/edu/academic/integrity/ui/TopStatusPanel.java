package edu.academic.integrity.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** Persistent corpus summary and global task progress area. */
public final class TopStatusPanel extends JPanel {
    private final JLabel corpus = new JLabel("Corpus: loading…");
    private final JLabel state = Theme.mutedLabel("Ready");
    private final JProgressBar progress = new JProgressBar(0, 100);

    public TopStatusPanel() {
        super(new BorderLayout(16, 0));
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 22, 12, 22)));
        corpus.setFont(Theme.BODY_BOLD);
        corpus.setForeground(Theme.TEXT);
        add(corpus, BorderLayout.WEST);

        JPanel task = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        task.setOpaque(false);
        progress.setPreferredSize(new java.awt.Dimension(180, 17));
        progress.setStringPainted(false);
        progress.setVisible(false);
        task.add(state);
        task.add(progress);
        add(task, BorderLayout.EAST);
    }

    public void setCorpusCounts(int submissions, int references) {
        corpus.setText("Corpus  •  " + submissions + " submissions  •  "
                + references + " references");
    }

    public void setTaskState(String text, int percentage, boolean busy) {
        state.setText(text == null || text.isBlank() ? (busy ? "Working…" : "Ready") : text);
        progress.setVisible(busy);
        progress.setIndeterminate(busy && percentage < 0);
        if (percentage >= 0) progress.setValue(Math.max(0, Math.min(100, percentage)));
    }
}
