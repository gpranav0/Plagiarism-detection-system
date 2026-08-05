package edu.academic.integrity.report;

import edu.academic.integrity.algorithms.compression.HuffmanCodec;
import edu.academic.integrity.io.ActivityLogger;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.ScoreBreakdown;
import edu.academic.integrity.model.SourceLocation;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
// NIO move options are required for temporary-write-then-replace report recovery.
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ReportWriter {
    private final File reportDirectory;
    private final ActivityLogger logger;
    private final double reviewThreshold;
    private final HuffmanCodec huffman = new HuffmanCodec();

    public ReportWriter(File reportDirectory, ActivityLogger logger) {
        this(reportDirectory, logger, 0.0);
    }

    public ReportWriter(File reportDirectory, ActivityLogger logger, double reviewThreshold) {
        if (Double.isNaN(reviewThreshold) || reviewThreshold < 0.0 || reviewThreshold > 1.0) {
            throw new IllegalArgumentException("reviewThreshold must be between zero and one");
        }
        this.reportDirectory = reportDirectory;
        this.logger = logger;
        this.reviewThreshold = reviewThreshold;
    }

    public ReportExportSummary export(AnalysisResult[] results, CaseAssignment[] assignments)
            throws IOException {
        return export(results, assignments, null);
    }

    public ReportExportSummary export(AnalysisResult[] results, CaseAssignment[] assignments,
                                      BatchAnalysisResult batch) throws IOException {
        ensureDirectory();
        File runDirectory = createRunDirectory();
        int textCount = 0;
        int compressedCount = 0;
        for (AnalysisResult result : results) {
            CaseAssignment assignment = findAssignment(assignments, result.caseId());
            String report = format(result, assignment);
            File textFile = new File(runDirectory, safeName(result.caseId()) + ".txt");
            atomicWriteText(textFile, report);
            textCount++;
            File compressedFile = new File(runDirectory, safeName(result.caseId()) + ".huff");
            atomicWriteBytes(compressedFile, huffman.compressText(report));
            compressedCount++;
        }
        String summary = formatSummary(results, assignments, batch);
        File summaryFile = new File(runDirectory, "analysis-summary.txt");
        atomicWriteText(summaryFile, summary);
        logger.info("Exported " + textCount + " case reports and batch summary");
        return new ReportExportSummary(textCount, compressedCount, summaryFile.getAbsolutePath());
    }

    public String format(AnalysisResult result, CaseAssignment assignment) {
        StringBuilder report = new StringBuilder(2048);
        ScoreBreakdown score = result.score();
        report.append("ADVANCED PLAGIARISM DETECTION REPORT\n");
        report.append("=====================================\n");
        report.append("Case ID: ").append(result.caseId()).append('\n');
        report.append("Submission: ").append(result.submission().id())
                .append(" — ").append(result.submission().title()).append('\n');
        report.append("Reference: ").append(result.reference().id())
                .append(" — ").append(result.reference().title()).append('\n');
        report.append("Risk: ").append(score.riskLabel()).append('\n');
        report.append("Composite score: ").append(percent(score.total())).append("\n\n");

        report.append("EXPLAINABLE SCORE COMPONENTS\n");
        report.append("  Exact phrase coverage: ").append(percent(score.exactMatch())).append('\n');
        report.append("  Word/character shingles: ").append(percent(score.shingleSimilarity())).append('\n');
        report.append("  Fuzzy alignment (Smith-Waterman/LCS/edit): ")
                .append(percent(score.fuzzyAlignment())).append('\n');
        report.append("  Similarity-graph signal: ").append(percent(score.graphSignal())).append('\n');
        report.append("  Runtime: ").append(String.format("%.3f", result.elapsedNanos() / 1_000_000.0))
                .append(" ms\n\n");

        report.append("MATCHED EVIDENCE\n");
        PassageMatch[] evidence = result.evidence();
        if (evidence.length == 0) {
            report.append("  No reviewable passage exceeded the evidence criteria.\n");
        }
        for (int i = 0; i < evidence.length; i++) {
            PassageMatch match = evidence[i];
            SourceLocation submissionStart = result.submission().locate(match.submissionStart());
            SourceLocation submissionEnd = result.submission().locate(match.submissionEnd());
            SourceLocation referenceStart = result.reference().locate(match.referenceStart());
            SourceLocation referenceEnd = result.reference().locate(match.referenceEnd());
            report.append("\n  Evidence ").append(i + 1).append(" — ").append(match.type()).append('\n');
            report.append("    Algorithm: ").append(match.algorithm()).append('\n');
            report.append("    Similarity: ").append(percent(match.similarity())).append('\n');
            report.append("    Submission location: ").append(submissionStart)
                    .append(" to ").append(submissionEnd).append('\n');
            report.append("    Reference location: ").append(referenceStart)
                    .append(" to ").append(referenceEnd).append('\n');
            report.append("    Excerpt: \"").append(singleLine(match.excerpt(), 260)).append("\"\n");
        }

        report.append("\nGRAPH EVIDENCE\n");
        if (score.graphSignal() <= 0.0) {
            report.append("  This pair did not form a relationship above the configured graph threshold.\n");
        } else {
            report.append("  The pair formed a weighted similarity-graph edge; its direct similarity and\n");
            report.append("  local connection density contributed ").append(percent(score.graphSignal()))
                    .append(" to the visible graph component.\n");
        }

        report.append("\nREVIEW WORKFLOW\n");
        if (assignment == null) {
            if (result.score().total() >= reviewThreshold) {
                report.append("  Reviewer: AWAITING ASSIGNMENT (assignment not run or capacity unavailable)\n");
            } else {
                report.append("  Reviewer: not queued (below review threshold)\n");
            }
        } else {
            report.append("  Reviewer: ").append(assignment.reviewer().id())
                    .append(" — ").append(assignment.reviewer().name()).append('\n');
        }
        report.append("  Decision policy: automated results are evidence for human review, not a verdict.\n");
        return report.toString();
    }

    private String formatSummary(AnalysisResult[] results, CaseAssignment[] assignments,
                                 BatchAnalysisResult batch) {
        StringBuilder summary = new StringBuilder(1024);
        summary.append("PLAGIARISM ANALYSIS BATCH SUMMARY\n");
        summary.append("=================================\n");
        if (batch != null) {
            summary.append("Total possible pairs: ").append(batch.totalPairCount()).append('\n');
            summary.append("MinHash candidates: ").append(batch.candidateCount()).append('\n');
            summary.append("Verified comparisons: ").append(batch.comparisonCount()).append('\n');
            summary.append("Candidate reduction: ")
                    .append(String.format("%.2f%%", batch.candidateReduction() * 100.0)).append('\n');
            summary.append("Execution mode: ").append(batch.parallel() ? "bounded parallel" : "sequential")
                    .append('\n');
            summary.append("Elapsed: ").append(String.format("%.3f", batch.elapsedMillis()))
                    .append(" ms\n");
        }
        summary.append("Cases emitted: ").append(results.length).append("\n\n");
        for (AnalysisResult result : results) {
            CaseAssignment assignment = findAssignment(assignments, result.caseId());
            summary.append(result.caseId()).append(" | ")
                    .append(result.submission().id()).append(" <-> ")
                    .append(result.reference().id()).append(" | ")
                    .append(percent(result.score().total())).append(" | ")
                    .append(result.score().riskLabel()).append(" | ")
                    .append(assignment == null
                            ? (result.score().total() >= reviewThreshold
                                ? "AWAITING_ASSIGNMENT" : "BELOW_THRESHOLD")
                            : assignment.reviewer().id())
                    .append('\n');
        }
        return summary.toString();
    }

    private CaseAssignment findAssignment(CaseAssignment[] assignments, String caseId) {
        if (assignments == null) return null;
        for (CaseAssignment assignment : assignments) {
            if (assignment != null && assignment.result().caseId().equals(caseId)) return assignment;
        }
        return null;
    }

    private void atomicWriteText(File destination, String content) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temporary,
                StandardCharsets.UTF_8))) {
            writer.write(content);
        }
        replace(temporary, destination);
    }

    private void atomicWriteBytes(File destination, byte[] content) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            output.write(content);
        }
        replace(temporary, destination);
    }

    private void replace(File temporary, File destination) throws IOException {
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureDirectory() throws IOException {
        if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
            throw new IOException("Unable to create report directory: " + reportDirectory);
        }
    }

    private File createRunDirectory() throws IOException {
        String base = "run-" + System.currentTimeMillis();
        for (int suffix = 0; suffix < 1_000; suffix++) {
            File candidate = new File(reportDirectory, suffix == 0 ? base : base + '-' + suffix);
            if (candidate.mkdir()) return candidate;
            if (!candidate.exists()) {
                throw new IOException("Unable to create report run directory: " + candidate);
            }
        }
        throw new IOException("Unable to allocate a unique report run directory");
    }

    private String percent(double value) {
        return String.format("%.2f%%", value * 100.0);
    }

    private String safeName(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return result.toString();
    }

    private String singleLine(String value, int maximum) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), maximum));
        for (int i = 0; i < value.length() && result.length() < maximum; i++) {
            char c = value.charAt(i);
            result.append(Character.isWhitespace(c) ? ' ' : c);
        }
        if (value.length() > maximum) result.append("...");
        return result.toString();
    }
}
