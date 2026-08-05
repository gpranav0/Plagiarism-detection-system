package edu.academic.integrity.tests;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.service.AnalysisRequest;
import edu.academic.integrity.service.AnalysisRun;
import edu.academic.integrity.service.ApplicationService;
import edu.academic.integrity.service.AssignmentPlan;
import edu.academic.integrity.service.GraphSnapshot;
import edu.academic.integrity.service.RankingQuery;
import edu.academic.integrity.service.ReportEntry;
import edu.academic.integrity.service.SettingsSnapshot;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone smoke test for the headless desktop integration boundary. */
public final class ServiceControllerSelfTests {
    private static int assertions;

    private ServiceControllerSelfTests() { }

    public static void main(String[] args) throws Exception {
        int completed = runAll();
        System.out.println("SERVICE_CONTROLLER_TESTS_PASSED: " + completed + " assertions");
    }

    public static int runAll() throws Exception {
        assertions = 0;
        File root = Files.createTempDirectory("service-controller-tests-").toFile();
        try {
            createFixture(root);
            exerciseService(root);
            exerciseController(root);
            return assertions;
        } finally {
            deleteRecursively(root);
        }
    }

    private static void exerciseService(File root) throws Exception {
        ApplicationService service = new ApplicationService(root);
        try {
            check(service.dashboard().submissionCount() == 1, "submission loaded at startup");
            check(service.dashboard().referenceCount() == 1, "reference loaded at startup");
            check(service.findDocument("SUB-UI") != null, "indexed document lookup");
            check(service.validateCorpus().invalidFiles() == 0, "corpus validation");

            AnalysisRequest request = new AnalysisRequest("SUB-UI", "REF-UI", false,
                    0.0, 0.25, 0.0, true, true, true, true);
            AnalysisRun run = service.runAnalysis(request, update -> { }, () -> false);
            check(run.results().length == 1 && !run.batch().parallel(),
                    "selected sequential analysis");
            AnalysisResult[] ranked = service.ranked(new RankingQuery(true, "", "SUB-UI"));
            check(ranked.length == 1, "custom ranked projection");
            check(service.result(ranked[0].caseId()) != null, "case detail projection");
            GraphSnapshot graph = service.graphSnapshot();
            check(graph.nodes().length == 2 && graph.edges().length == 1,
                    "complete graph projection");

            AssignmentPlan plan = service.suggestAssignments();
            check(plan.assignedCount() == 1 && plan.unassignedCount() == 0,
                    "maximum-flow assignment projection");
            check(service.previewReport(ranked[0].caseId()).contains("Case ID"),
                    "report preview");
            service.exportReports();
            ReportEntry[] reports = service.reportFiles();
            check(reports.length >= 3, "text, summary, and compressed reports listed");
            File text = firstTextReport(reports);
            check(text != null && service.readReport(text).contains("PLAGIARISM"),
                    "existing report read");
            File compressed = service.compressReport(text, null);
            File decoded = service.decompressReport(compressed, null);
            check(decoded.isFile() && Files.readString(decoded.toPath()).contains("PLAGIARISM"),
                    "Huffman report round trip");

            SettingsSnapshot settings = service.settings();
            service.saveSettings(settings);
            check(service.dashboard().totalDocuments() == 2,
                    "atomic settings swap reimports corpus");
        } finally {
            service.shutdown();
        }
    }

    private static void exerciseController(File root) throws Exception {
        ApplicationController controller = new ApplicationController(root);
        try {
            File extra = new File(root, "single-extra.txt");
            Files.writeString(extra.toPath(), "ID: SUB-EXTRA\n\nA separate valid submission body.",
                    StandardCharsets.UTF_8);
            CountDownLatch completed = new CountDownLatch(1);
            Throwable[] failure = new Throwable[1];
            controller.importFileAsync(extra, DocumentType.SUBMISSION,
                    new ApplicationController.TaskCallback<>() {
                        @Override
                        public void onSuccess(ImportSummary result) {
                            if (result.importedCount() != 1) {
                                failure[0] = new AssertionError("single-file import count");
                            }
                            completed.countDown();
                        }

                        @Override
                        public void onFailure(String message) {
                            failure[0] = new AssertionError(message);
                            completed.countDown();
                        }
                    });
            check(completed.await(20, TimeUnit.SECONDS), "async controller callback");
            if (failure[0] != null) throw new AssertionError(failure[0]);
            check(controller.documents(DocumentType.SUBMISSION).length == 2,
                    "async mutation visible through synchronous snapshot");
        } finally {
            controller.shutdown();
        }
    }

    private static File firstTextReport(ReportEntry[] reports) {
        for (ReportEntry report : reports) {
            if (!report.compressed() && !report.name().equals("analysis-summary.txt")) {
                return report.file();
            }
        }
        return null;
    }

    private static void createFixture(File root) throws Exception {
        File submissions = new File(root, "data/submissions");
        File references = new File(root, "data/references");
        File reviewers = new File(root, "data/reviewers");
        check(submissions.mkdirs(), "submission fixture directory");
        check(references.mkdirs(), "reference fixture directory");
        check(reviewers.mkdirs(), "reviewer fixture directory");
        String shared = "balanced search trees use rotations and preserve logarithmic height";
        Files.writeString(new File(submissions, "submission.txt").toPath(),
                "ID: SUB-UI\nTITLE: UI submission\n\n" + shared, StandardCharsets.UTF_8);
        Files.writeString(new File(references, "reference.txt").toPath(),
                "ID: REF-UI\nTITLE: UI reference\n\n" + shared, StandardCharsets.UTF_8);
        Files.writeString(new File(reviewers, "reviewers.txt").toPath(),
                "REV-UI,UI Reviewer,2\n", StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (!target.delete()) target.deleteOnExit();
    }
}
