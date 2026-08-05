package edu.academic.integrity.app;

import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.ValidationSummary;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.SimilarityGroup;
import edu.academic.integrity.report.ReportExportSummary;
import edu.academic.integrity.ui.MainApplication;

import java.io.File;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        AcademicIntegritySystem system = null;
        try {
            File root = resolveRoot(args);
            if (hasArgument(args, "--help")) {
                printHelp();
            } else if (!hasArgument(args, "--demo") && !hasArgument(args, "--console")) {
                MainApplication.launch(root);
            } else if (hasArgument(args, "--demo")) {
                system = new AcademicIntegritySystem(root);
                runDemo(system);
            } else {
                system = new AcademicIntegritySystem(root);
                new ConsoleApplication(system).run();
                system = null;
            }
        } catch (Exception exception) {
            System.err.println("Fatal initialization or demo failure: " + exception.getMessage());
            if (system != null) system.logger().error("Fatal application failure", exception);
            System.exit(1);
        } finally {
            if (system != null) system.shutdown();
        }
    }

    private static void runDemo(AcademicIntegritySystem system) throws Exception {
        System.out.println("Running deterministic end-to-end sample analysis...");
        ImportSummary submissions = system.importStandardSubmissions();
        ImportSummary references = system.importStandardReferences();
        ValidationSummary validation = system.validateStandardCorpus();
        System.out.println("Imported " + submissions.importedCount() + " submissions and "
                + references.importedCount() + " references; validation errors="
                + validation.invalidFiles());

        BatchAnalysisResult batch = system.analyzeBatch();
        CaseAssignment[] assignments = system.assignReviewers();
        ReportExportSummary reports = system.exportReports();
        system.runBenchmarks();

        System.out.println("Pairs: " + batch.totalPairCount() + ", shortlisted: "
                + batch.candidateCount() + ", verified: " + batch.comparisonCount());
        System.out.printf("Candidate reduction: %.2f%%%n", batch.candidateReduction() * 100.0);
        AnalysisResult highest = system.highestRisk();
        if (highest != null) {
            System.out.printf("Highest risk: %s (%s, %.2f%%) with %d evidence passage(s)%n",
                    highest.caseId(), highest.score().riskLabel(), highest.score().total() * 100.0,
                    highest.evidence().length);
            System.out.printf("Components: exact=%.2f%%, shingle=%.2f%%, fuzzy=%.2f%%, graph=%.2f%%%n",
                    highest.score().exactMatch() * 100.0,
                    highest.score().shingleSimilarity() * 100.0,
                    highest.score().fuzzyAlignment() * 100.0,
                    highest.score().graphSignal() * 100.0);
        }
        SimilarityGroup[] groups = system.similarityGroups();
        System.out.println("Similarity groups: " + groups.length + ", reviewer assignments: "
                + assignments.length);
        System.out.println("Reports: " + reports.summaryPath());
        System.out.println("Indexes: " + system.invariantSummary());
        System.out.println("DEMO_SUCCESS");
    }

    private static File resolveRoot(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--root".equals(args[i])) return new File(args[i + 1]).getAbsoluteFile();
        }
        return new File(System.getProperty("user.dir")).getAbsoluteFile();
    }

    private static boolean hasArgument(String[] args, String expected) {
        for (String argument : args) if (expected.equals(argument)) return true;
        return false;
    }

    private static void printHelp() {
        System.out.println("Usage: java ... edu.academic.integrity.app.Main [--console|--demo] [--root PATH]");
        System.out.println("No arguments starts the Java Swing desktop interface.");
        System.out.println("--console starts the legacy 13-option interactive console.");
        System.out.println("--demo imports sample data, analyzes, assigns, exports, and benchmarks.");
    }
}
