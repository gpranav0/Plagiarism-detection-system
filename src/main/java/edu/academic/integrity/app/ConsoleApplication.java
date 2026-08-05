package edu.academic.integrity.app;

import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.LogReader;
import edu.academic.integrity.io.ValidationSummary;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.RelationshipEdge;
import edu.academic.integrity.model.SimilarityGroup;
import edu.academic.integrity.report.ReportExportSummary;

import java.io.BufferedReader;
import java.io.File;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;

public final class ConsoleApplication {
    private final AcademicIntegritySystem system;
    private final BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    private boolean running = true;

    public ConsoleApplication(AcademicIntegritySystem system) {
        this.system = system;
    }

    public void run() {
        printBanner();
        while (running) {
            printMenu();
            try {
                String value = prompt("Choose an option: ");
                int option = Integer.parseInt(value.trim());
                perform(option);
            } catch (EOFException exception) {
                running = false;
                System.out.println("\nInput closed; exiting safely.");
            } catch (NumberFormatException exception) {
                System.out.println("Please enter an option number from 1 to 13.");
            } catch (Exception exception) {
                System.out.println("Operation failed safely: " + exception.getMessage());
                system.logger().error("Console operation failed", exception);
            }
        }
        system.shutdown();
    }

    private void perform(int option) throws Exception {
        switch (option) {
            case 1 -> importDocuments(DocumentType.SUBMISSION);
            case 2 -> importDocuments(DocumentType.REFERENCE);
            case 3 -> validateFiles();
            case 4 -> analyzeOne();
            case 5 -> analyzeBatch();
            case 6 -> displayEvidence();
            case 7 -> displayRanking();
            case 8 -> displayGroupsAndPaths();
            case 9 -> assignCases();
            case 10 -> exportReports();
            case 11 -> displayBenchmarks();
            case 12 -> displayLogs();
            case 13 -> running = false;
            default -> System.out.println("Unknown option. Choose a number from 1 to 13.");
        }
    }

    private void importDocuments(DocumentType type) throws IOException {
        File defaultDirectory = type == DocumentType.SUBMISSION
                ? system.paths().submissions : system.paths().references;
        String entered = prompt("Directory [" + defaultDirectory + "]: ").trim();
        File directory = entered.isEmpty() ? defaultDirectory : new File(entered);
        ImportSummary summary = system.importDocuments(directory, type);
        System.out.println("Imported " + summary.importedCount() + " valid "
                + type.name().toLowerCase() + " document(s); skipped " + summary.errorCount() + ".");
        for (String error : summary.errors()) System.out.println("  - " + error);
    }

    private void validateFiles() {
        ValidationSummary summary = system.validateImportedSources();
        System.out.println("Validation: " + summary.validFiles() + " valid, "
                + summary.invalidFiles() + " invalid.");
        for (String error : summary.errors()) System.out.println("  - " + error);
        System.out.println(system.invariantSummary());
    }

    private void analyzeOne() throws IOException {
        Document[] submissions = system.submissions();
        if (submissions.length == 0) throw new IllegalStateException("Import submissions first");
        printDocuments("Loaded submissions", submissions);
        String id = prompt("Submission ID: ").trim();
        BatchAnalysisResult batch = system.analyzeOneSubmission(id);
        System.out.println("Compared against " + batch.comparisonCount() + " reference(s) in "
                + formatMillis(batch.elapsedNanos()) + " ms.");
        displayRanking();
    }

    private void analyzeBatch() {
        BatchAnalysisResult batch = system.analyzeBatch();
        System.out.println("Batch complete: " + batch.comparisonCount() + " of "
                + batch.totalPairCount() + " pairs verified after MinHash shortlisting.");
        System.out.println("Candidate reduction: " + percent(batch.candidateReduction())
                + " | elapsed: " + formatMillis(batch.elapsedNanos()) + " ms");
        System.out.println(system.invariantSummary());
    }

    private void displayEvidence() throws IOException {
        AnalysisResult[] results = system.rankedResults();
        if (!system.hasAnalysis()) throw new IllegalStateException("Run an analysis first");
        if (results.length == 0) {
            System.out.println("Analysis completed, but MinHash shortlisted no document pairs.");
            return;
        }
        System.out.println("Cases (highest risk first):");
        for (int i = 0; i < results.length; i++) {
            System.out.println("  " + results[i].caseId() + " — " + percent(results[i].score().total()));
        }
        String id = prompt("Case ID [highest-risk]: ").trim();
        AnalysisResult result = id.isEmpty() ? results[0] : system.findResult(id);
        if (result == null) throw new IllegalArgumentException("Unknown case ID: " + id);
        printEvidence(result);
    }

    private void printEvidence(AnalysisResult result) {
        System.out.println("\n" + result.caseId() + " | " + result.submission().id() + " <-> "
                + result.reference().id());
        System.out.println("Composite " + percent(result.score().total()) + " ("
                + result.score().riskLabel() + ")");
        System.out.println("  exact=" + percent(result.score().exactMatch())
                + ", shingles=" + percent(result.score().shingleSimilarity())
                + ", fuzzy=" + percent(result.score().fuzzyAlignment())
                + ", graph=" + percent(result.score().graphSignal()));
        PassageMatch[] evidence = result.evidence();
        if (evidence.length == 0) System.out.println("  No reviewable matched passage.");
        for (int i = 0; i < evidence.length; i++) {
            PassageMatch match = evidence[i];
            System.out.println("  [" + (i + 1) + "] " + match.type() + " via " + match.algorithm());
            System.out.println("      submission " + result.submission().locate(match.submissionStart())
                    + " -> " + result.submission().locate(match.submissionEnd()));
            System.out.println("      reference  " + result.reference().locate(match.referenceStart())
                    + " -> " + result.reference().locate(match.referenceEnd()));
            System.out.println("      \"" + shortened(match.excerpt(), 160) + "\"");
        }
    }

    private void displayRanking() {
        if (!system.hasAnalysis()) throw new IllegalStateException("Run an analysis first");
        AnalysisResult[] results = system.rankedSubmissions();
        if (results.length == 0) {
            System.out.println("Analysis completed, but no submission-reference pair was shortlisted.");
            return;
        }
        System.out.println("\nSuspicious submission ranking (strongest reference match)");
        for (int i = 0; i < results.length; i++) {
            AnalysisResult result = results[i];
            System.out.printf("%3d. %-18s %7s  %-8s strongest reference: %s (%s)%n", i + 1,
                    shortened(result.submission().id(), 18), percent(result.score().total()),
                    result.score().riskLabel(), result.reference().id(), result.caseId());
        }
        var analytics = system.submissionRangeAnalytics();
        if (!analytics.isEmpty()) {
            var summary = analytics.summarize(0, results.length - 1);
            System.out.println("Range analytics: min=" + percent(summary.minimum())
                    + ", max=" + percent(summary.maximum())
                    + ", average=" + percent(summary.average())
                    + ", reviewable=" + summary.flaggedCount());
        }
    }

    private void displayGroupsAndPaths() throws IOException {
        if (!system.hasAnalysis()) throw new IllegalStateException("Run an analysis first");
        if (system.rankedResults().length == 0) {
            System.out.println("Analysis completed with no shortlisted relationships; "
                    + "loaded documents remain available as isolated graph vertices.");
        }
        SimilarityGroup[] groups = system.similarityGroups();
        if (groups.length == 0) {
            System.out.println("No multi-document similarity group exceeds the graph threshold.");
        }
        for (SimilarityGroup group : groups) {
            System.out.print(group.id() + ": ");
            printJoined(group.documentIds(), " -> ");
        }
        RelationshipEdge[] compact = system.compactRelationships();
        if (compact.length > 0) System.out.println("Compact Kruskal relationship links:");
        for (RelationshipEdge edge : compact) {
            System.out.println("  " + edge.firstDocumentId() + " <-> " + edge.secondDocumentId()
                    + " (" + percent(edge.similarity()) + ")");
        }
        String first = prompt("Path start document ID [leave blank to return]: ").trim();
        if (first.isEmpty()) return;
        String[] breadthFirst = system.breadthFirstRelationships(first);
        if (breadthFirst.length == 0) throw new IllegalArgumentException("Unknown document ID: " + first);
        System.out.print("BFS reachability from " + first + ": ");
        printJoined(breadthFirst, " -> ");
        System.out.print("DFS exploration from " + first + ": ");
        printJoined(system.depthFirstRelationships(first), " -> ");
        String second = prompt("Path destination document ID: ").trim();
        CopyingPath path = system.copyingPath(first, second);
        if (!path.exists()) {
            System.out.println("No graph path connects those documents.");
            return;
        }
        System.out.print("Strongest relationship path: ");
        printJoined(path.documentIds(), " -> ");
        double[] similarities = path.relationshipSimilarities();
        for (int i = 0; i < similarities.length; i++) {
            System.out.println("  edge " + (i + 1) + ": " + percent(similarities[i]));
        }
    }

    private void assignCases() {
        AnalysisResult[] reviewable = system.reviewableResults();
        CaseAssignment[] assignments = system.assignReviewers();
        System.out.println("Assigned " + assignments.length + " reviewable case(s) with maximum flow:");
        for (CaseAssignment assignment : assignments) {
            System.out.println("  " + assignment.result().caseId() + " -> "
                    + assignment.reviewer().id() + " (" + assignment.reviewer().name() + ")");
        }
        int waiting = reviewable.length - assignments.length;
        if (waiting > 0) {
            System.out.println("Awaiting reviewer capacity: " + waiting + " case(s)");
            for (AnalysisResult result : reviewable) {
                if (!isAssigned(result.caseId(), assignments)) {
                    System.out.println("  " + result.caseId() + " — " + percent(result.score().total()));
                }
            }
        }
    }

    private void exportReports() throws IOException {
        ReportExportSummary summary = system.exportReports();
        System.out.println("Exported " + summary.textReports() + " readable and "
                + summary.compressedReports() + " Huffman-compressed case reports.");
        System.out.println("Summary: " + summary.summaryPath());
    }

    private void displayBenchmarks() throws IOException {
        System.out.println(system.runBenchmarks());
    }

    private void displayLogs() throws IOException {
        System.out.println("\nActivity log (last 50 lines)");
        System.out.println(LogReader.tail(system.logger().activityFile(), 50));
        System.out.println("Error log (last 50 lines)");
        System.out.println(LogReader.tail(system.logger().errorFile(), 50));
    }

    private void printDocuments(String heading, Document[] documents) {
        System.out.println(heading + ":");
        for (Document document : documents) {
            System.out.println("  " + document.id() + " — " + document.title());
        }
    }

    private void printJoined(String[] values, String separator) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) System.out.print(separator);
            System.out.print(values[i]);
        }
        System.out.println();
    }

    private boolean isAssigned(String caseId, CaseAssignment[] assignments) {
        for (CaseAssignment assignment : assignments) {
            if (assignment.result().caseId().equals(caseId)) return true;
        }
        return false;
    }

    private String prompt(String message) throws IOException {
        System.out.print(message);
        String line = input.readLine();
        if (line == null) {
            throw new EOFException("Console input closed");
        }
        return line;
    }

    private void printBanner() {
        System.out.println("===============================================================");
        System.out.println(" Advanced Plagiarism Detection & Academic Integrity Analysis");
        System.out.println(" Explainable evidence for human academic review");
        System.out.println("===============================================================");
    }

    private void printMenu() {
        System.out.println("\n1.  Import student submissions");
        System.out.println("2.  Import reference documents");
        System.out.println("3.  Validate files and metadata");
        System.out.println("4.  Run plagiarism analysis on one document");
        System.out.println("5.  Run batch analysis");
        System.out.println("6.  Display matched passages and source locations");
        System.out.println("7.  Rank suspicious submissions");
        System.out.println("8.  Display similarity groups and copying paths");
        System.out.println("9.  Assign cases to reviewers");
        System.out.println("10. Export reports");
        System.out.println("11. Display algorithm benchmarks");
        System.out.println("12. View activity and error logs");
        System.out.println("13. Exit safely");
    }

    private String percent(double ratio) { return String.format("%.2f%%", ratio * 100.0); }
    private String formatMillis(long nanos) { return String.format("%.3f", nanos / 1_000_000.0); }

    private String shortened(String value, int maximum) {
        if (value.length() <= maximum) return value;
        return value.substring(0, Math.max(0, maximum - 3)) + "...";
    }
}
