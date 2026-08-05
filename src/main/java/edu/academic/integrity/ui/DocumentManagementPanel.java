package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.ValidationSummary;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Import, search, validate, preview, unload, and reload corpus documents. */
public final class DocumentManagementPanel extends ScreenPanel {
    private final ApplicationController controller;
    private final UiHost host;
    private final DocumentBrowserPanel submissions = new DocumentBrowserPanel();
    private final DocumentBrowserPanel references = new DocumentBrowserPanel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField search = new JTextField(18);
    private final JLabel validation = Theme.mutedLabel("Files are validated during import");

    public DocumentManagementPanel(ApplicationController controller, UiHost host) {
        super("Document management",
                "Maintain separate submission and reference corpora with validated UTF-8 text files.");
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.add(buildToolbar(), BorderLayout.NORTH);
        tabs.addTab("Submissions", submissions);
        tabs.addTab("References", references);
        tabs.setToolTipTextAt(0, "Student documents to be checked");
        tabs.setToolTipTextAt(1, "Source documents used for comparison");
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildToolbar() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setBackground(Theme.SURFACE);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setOpaque(false);
        JButton importFile = Theme.primaryButton("Import file");
        importFile.setMnemonic('F');
        importFile.setToolTipText("Import one validated .txt document into the active corpus");
        importFile.addActionListener(event -> chooseFile());
        JButton importDirectory = Theme.secondaryButton("Import directory");
        importDirectory.setMnemonic('D');
        importDirectory.setToolTipText("Import every supported text file in a directory");
        importDirectory.addActionListener(event -> chooseDirectory());
        JButton remove = Theme.dangerButton("Remove selected");
        remove.setToolTipText("Unload the selected document without deleting its source file");
        remove.addActionListener(event -> removeSelected());
        JButton reload = Theme.secondaryButton("Reload corpus");
        reload.setToolTipText("Clear in-memory documents and re-read registered sources");
        reload.addActionListener(event -> reloadCorpus());
        JButton validate = Theme.secondaryButton("Validate sources");
        validate.addActionListener(event -> validateCorpus());
        actions.add(importFile);
        actions.add(importDirectory);
        actions.add(remove);
        actions.add(reload);
        actions.add(validate);
        wrapper.add(actions, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);
        search.setToolTipText("Exact document ID lookup using the custom B-tree/B+ tree index");
        search.addActionListener(event -> searchById());
        JButton find = Theme.secondaryButton("Find ID");
        find.addActionListener(event -> searchById());
        JButton clear = Theme.secondaryButton("Clear");
        clear.addActionListener(event -> {
            search.setText("");
            refreshData();
        });
        searchPanel.add(new JLabel("Document ID"));
        searchPanel.add(search);
        searchPanel.add(find);
        searchPanel.add(clear);
        wrapper.add(searchPanel, BorderLayout.EAST);
        wrapper.add(validation, BorderLayout.SOUTH);
        return wrapper;
    }

    private DocumentType activeType() {
        return tabs.getSelectedIndex() == 0 ? DocumentType.SUBMISSION : DocumentType.REFERENCE;
    }

    private DocumentBrowserPanel activeBrowser() {
        return tabs.getSelectedIndex() == 0 ? submissions : references;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import " + activeType().name().toLowerCase() + " text file");
        chooser.setFileFilter(new FileNameExtensionFilter("UTF-8 text documents (*.txt)", "txt"));
        chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        host.taskStarted("Importing " + file.getName() + "…");
        controller.importFileAsync(file, activeType(), new UiTaskCallback<>(host, "Document imported") {
            @Override
            protected void handleSuccess(ImportSummary result) {
                showImportSummary(result);
            }
        });
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import a directory of " + activeType().name().toLowerCase() + "s");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File directory = chooser.getSelectedFile();
        host.taskStarted("Importing documents from " + directory.getName() + "…");
        controller.importDirectoryAsync(directory, activeType(),
                new UiTaskCallback<>(host, "Directory import completed") {
                    @Override
                    protected void handleSuccess(ImportSummary result) {
                        showImportSummary(result);
                    }
                });
    }

    private void showImportSummary(ImportSummary summary) {
        String message = summary.importedCount() + " document(s) imported";
        if (summary.errorCount() > 0) {
            message += "; " + summary.errorCount() + " file(s) rejected. See error logs.";
            validation.setForeground(Theme.WARNING);
        } else {
            validation.setForeground(Theme.SUCCESS);
        }
        validation.setText(message);
    }

    private void removeSelected() {
        Document selected = activeBrowser().selectedDocument();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a document to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int decision = JOptionPane.showConfirmDialog(this,
                "Unload “" + selected.id() + "” from the corpus?\n\n"
                        + "The source file will remain on disk. Existing analysis results will be cleared.",
                "Remove loaded document", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        host.taskStarted("Removing " + selected.id() + "…");
        controller.removeDocumentAsync(selected.id(), new UiTaskCallback<>(host, "Document removed") {
            @Override
            protected void handleSuccess(Document result) { }
        });
    }

    private void reloadCorpus() {
        int decision = JOptionPane.showConfirmDialog(this,
                "Reload all registered corpus sources?\nCurrent analysis results will be cleared.",
                "Reload corpus", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (decision != JOptionPane.OK_OPTION) return;
        host.taskStarted("Reloading corpus…");
        controller.reloadCorpusAsync(new UiTaskCallback<>(host, "Corpus reloaded") {
            @Override
            protected void handleSuccess(ImportSummary result) {
                showImportSummary(result);
            }
        });
    }

    private void validateCorpus() {
        host.taskStarted("Validating registered document sources…");
        controller.validateCorpusAsync(new UiTaskCallback<>(host, "Corpus validation completed") {
            @Override
            protected void handleSuccess(ValidationSummary result) {
                validation.setText(result.validFiles() + " valid file(s), "
                        + result.invalidFiles() + " invalid file(s)");
                validation.setForeground(result.invalidFiles() == 0 ? Theme.SUCCESS : Theme.WARNING);
                if (result.invalidFiles() > 0) {
                    JOptionPane.showMessageDialog(DocumentManagementPanel.this,
                            "Some source files were rejected. Open Reports & Logs for details.",
                            "Validation completed with warnings", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }

    private void searchById() {
        String id = search.getText().trim();
        if (id.isEmpty()) {
            refreshData();
            return;
        }
        Document document;
        try {
            document = controller.findDocument(id);
        } catch (RuntimeException exception) {
            document = null;
        }
        if (document == null || document.type() != activeType()) {
            activeBrowser().selectOnly(null);
            validation.setText("No " + activeType().name().toLowerCase()
                    + " with ID “" + id + "” was found");
            validation.setForeground(Theme.WARNING);
            return;
        }
        activeBrowser().selectOnly(document);
        validation.setText("Found through the custom document index");
        validation.setForeground(Theme.SUCCESS);
    }

    @Override
    public void refreshData() {
        submissions.setDocuments(controller.documents(DocumentType.SUBMISSION));
        references.setDocuments(controller.documents(DocumentType.REFERENCE));
    }
}
