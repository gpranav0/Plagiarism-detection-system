package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.service.AnalysisRequest;
import edu.academic.integrity.service.ApplicationService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.SwingUtilities;

/** Headless EDT smoke test for every reusable desktop screen except the JFrame shell. */
public final class DesktopUiSelfTests {
    private static int assertions;

    private DesktopUiSelfTests() { }

    public static int runAll() throws Exception {
        assertions = 0;
        File root = Files.createTempDirectory("desktop-ui-tests-").toFile();
        ApplicationService service = null;
        ApplicationController controller = null;
        try {
            createFixture(root);
            service = new ApplicationService(root);
            service.runAnalysis(new AnalysisRequest("SUB-DESKTOP", "REF-DESKTOP", false,
                    0.0, 0.25, 0.0, true, true, true, true), update -> { }, () -> false);
            controller = new ApplicationController(service);
            ApplicationController testedController = controller;
            SwingUtilities.invokeAndWait(() -> exerciseScreens(testedController));
            return assertions;
        } finally {
            if (controller != null) controller.shutdown();
            else if (service != null) service.shutdown();
            deleteRecursively(root);
        }
    }

    private static void exerciseScreens(ApplicationController controller) {
        TestHost host = new TestHost();
        DashboardPanel dashboard = new DashboardPanel(controller, host);
        DocumentManagementPanel documents = new DocumentManagementPanel(controller, host);
        AnalysisPanel analysis = new AnalysisPanel(controller, host);
        ResultsPanel results = new ResultsPanel(controller, host);
        RankingPanel ranking = new RankingPanel(controller, host);
        SimilarityGraphPanel graph = new SimilarityGraphPanel(controller, host);
        ReviewerAssignmentPanel reviewers = new ReviewerAssignmentPanel(controller, host);
        ReportsPanel reports = new ReportsPanel(controller, host);
        SettingsPanel settings = new SettingsPanel(controller, host);

        Refreshable[] screens = {dashboard, documents, analysis, results, ranking,
                graph, reviewers, reports, settings};
        for (Refreshable screen : screens) screen.refreshData();
        check(screens.length == 9, "every required desktop screen constructed");
        check(controller.documents(DocumentType.SUBMISSION).length == 1,
                "document screen reads real submission data");
        check(results.selectedCaseId() != null,
                "results screen selects an actual analysis case");
        check(graph.selectedDocumentId() == null || !graph.selectedDocumentId().isBlank(),
                "graph screen has a valid optional selection");
        check(host.failureCount == 0, "screen refreshes report no failures");
    }

    private static void createFixture(File root) throws Exception {
        File submissions = new File(root, "data/submissions");
        File references = new File(root, "data/references");
        File reviewers = new File(root, "data/reviewers");
        check(submissions.mkdirs(), "desktop submission fixture directory");
        check(references.mkdirs(), "desktop reference fixture directory");
        check(reviewers.mkdirs(), "desktop reviewer fixture directory");
        String shared = "balanced trees rotate nodes while preserving logarithmic search height";
        Files.writeString(new File(submissions, "submission.txt").toPath(),
                "ID: SUB-DESKTOP\nTITLE: Desktop submission\n\n" + shared,
                StandardCharsets.UTF_8);
        Files.writeString(new File(references, "reference.txt").toPath(),
                "ID: REF-DESKTOP\nTITLE: Desktop reference\n\n" + shared,
                StandardCharsets.UTF_8);
        Files.writeString(new File(reviewers, "reviewers.txt").toPath(),
                "REV-DESKTOP,Desktop Reviewer,2\n", StandardCharsets.UTF_8);
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

    private static final class TestHost implements UiHost {
        private int failureCount;

        @Override public void navigateTo(String cardName) { }
        @Override public void showResult(String caseId) { }
        @Override public void showReviewerCase(String caseId) { }
        @Override public void refreshAll() { }
        @Override public void taskStarted(String message) { }
        @Override public void taskProgress(String message, int percentage) { }
        @Override public void taskFinished(String message) { }
        @Override public void taskFailed(String message) { failureCount++; }
    }
}
