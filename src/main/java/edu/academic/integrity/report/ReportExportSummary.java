package edu.academic.integrity.report;

public final class ReportExportSummary {
    private final int textReports;
    private final int compressedReports;
    private final String summaryPath;

    public ReportExportSummary(int textReports, int compressedReports, String summaryPath) {
        this.textReports = textReports;
        this.compressedReports = compressedReports;
        this.summaryPath = summaryPath;
    }

    public int textReports() { return textReports; }
    public int compressedReports() { return compressedReports; }
    public String summaryPath() { return summaryPath; }
}

