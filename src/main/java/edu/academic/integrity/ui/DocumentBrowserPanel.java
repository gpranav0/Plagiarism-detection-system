package edu.academic.integrity.ui;

import edu.academic.integrity.model.Document;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Reusable submission/reference browser with metadata and content preview. */
public final class DocumentBrowserPanel extends JPanel {
    private final DocumentTableModel model = new DocumentTableModel();
    private final JTable table = new JTable(model);
    private final JLabel id = new JLabel("—");
    private final JLabel title = new JLabel("—");
    private final JLabel author = new JLabel("—");
    private final JLabel source = new JLabel("—");
    private final JLabel status = new JLabel("Select a document to inspect it");
    private final JTextArea preview = new JTextArea();

    public DocumentBrowserPanel() {
        super(new BorderLayout());
        setOpaque(false);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showDocument(selectedDocument());
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel details = buildDetails();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, details);
        split.setResizeWeight(0.58);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildDetails() {
        JPanel details = new JPanel(new BorderLayout(0, 12));
        details.setBackground(Theme.SURFACE);
        details.setBorder(Theme.cardBorder());
        JLabel heading = new JLabel("Document details");
        heading.setFont(Theme.SECTION);
        heading.setForeground(Theme.TEXT);
        details.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 4));
        fields.setOpaque(false);
        fields.add(field("ID", id));
        fields.add(field("Title", title));
        fields.add(field("Author", author));
        fields.add(field("Source", source));
        status.setForeground(Theme.SUCCESS);
        fields.add(field("Validation", status));

        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setBackground(new java.awt.Color(249, 251, 254));
        preview.setForeground(Theme.TEXT);
        preview.setMargin(new java.awt.Insets(10, 10, 10, 10));
        JScrollPane contentScroll = new JScrollPane(preview);
        contentScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Content preview"));
        contentScroll.setPreferredSize(new Dimension(360, 260));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(fields, BorderLayout.NORTH);
        center.add(contentScroll, BorderLayout.CENTER);
        details.add(center, BorderLayout.CENTER);
        return details;
    }

    private JPanel field(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel name = Theme.mutedLabel(label + ":");
        name.setPreferredSize(new Dimension(72, 20));
        value.setForeground(Theme.TEXT);
        value.setToolTipText(value.getText());
        row.add(name, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    public void setDocuments(Document[] documents) {
        model.setDocuments(documents);
        if (model.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            showDocument(null);
        }
    }

    public Document selectedDocument() {
        return model.documentAt(table.getSelectedRow());
    }

    public void selectOnly(Document document) {
        model.setDocuments(document == null ? new Document[0] : new Document[]{document});
        if (document != null) table.setRowSelectionInterval(0, 0);
        else showDocument(null);
    }

    private void showDocument(Document document) {
        if (document == null) {
            id.setText("—");
            title.setText("—");
            author.setText("—");
            source.setText("—");
            status.setText("No document selected");
            status.setForeground(Theme.MUTED);
            preview.setText("Select a document from the table to preview its validated content.");
            return;
        }
        id.setText(document.id());
        title.setText(document.title());
        author.setText(document.author().isBlank() ? "Not provided" : document.author());
        File file = new File(document.filePath());
        source.setText(file.getName());
        source.setToolTipText(document.filePath());
        status.setText("Valid • UTF-8 text loaded");
        status.setForeground(Theme.SUCCESS);
        preview.setText(document.content());
        preview.setCaretPosition(0);
    }
}
