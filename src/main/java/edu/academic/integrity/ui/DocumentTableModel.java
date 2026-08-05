package edu.academic.integrity.ui;

import edu.academic.integrity.model.Document;
import javax.swing.table.AbstractTableModel;

/** View adapter only; document storage and lookup remain in the core indexes. */
public final class DocumentTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Document ID", "Title", "Author", "Source", "Status"};
    private Document[] documents = new Document[0];

    public void setDocuments(Document[] values) {
        documents = values == null ? new Document[0] : copy(values);
        fireTableDataChanged();
    }

    public Document documentAt(int row) {
        return row < 0 || row >= documents.length ? null : documents[row];
    }

    @Override
    public int getRowCount() {
        return documents.length;
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Document document = documents[row];
        return switch (column) {
            case 0 -> document.id();
            case 1 -> document.title();
            case 2 -> document.author().isBlank() ? "—" : document.author();
            case 3 -> document.filePath();
            case 4 -> "Valid";
            default -> "";
        };
    }

    private Document[] copy(Document[] source) {
        Document[] result = new Document[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
