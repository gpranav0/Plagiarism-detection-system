package edu.academic.integrity.ui;

import edu.academic.integrity.model.AnalysisResult;
import javax.swing.table.AbstractTableModel;

/** JTable adapter for already-ranked custom-index output; it never sorts rows. */
public final class ResultTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Rank", "Submission", "Strongest source", "Composite", "Risk",
            "Exact", "Shingle", "Fuzzy", "Graph"
    };
    private AnalysisResult[] results = new AnalysisResult[0];

    public void setResults(AnalysisResult[] values) {
        results = values == null ? new AnalysisResult[0] : copy(values);
        fireTableDataChanged();
    }

    public AnalysisResult resultAt(int row) {
        return row < 0 || row >= results.length ? null : results[row];
    }

    @Override
    public int getRowCount() {
        return results.length;
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
        AnalysisResult result = results[row];
        return switch (column) {
            case 0 -> row + 1;
            case 1 -> result.submission().id();
            case 2 -> result.reference().id();
            case 3 -> UiFormat.percent(result.score().total());
            case 4 -> result.score().riskLabel();
            case 5 -> UiFormat.percent(result.score().exactMatch());
            case 6 -> UiFormat.percent(result.score().shingleSimilarity());
            case 7 -> UiFormat.percent(result.score().fuzzyAlignment());
            case 8 -> UiFormat.percent(result.score().graphSignal());
            default -> "";
        };
    }

    private AnalysisResult[] copy(AnalysisResult[] source) {
        AnalysisResult[] result = new AnalysisResult[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
