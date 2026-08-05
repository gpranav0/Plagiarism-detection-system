package edu.academic.integrity.controller;

import edu.academic.integrity.exception.ProjectException;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.ValidationSummary;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.report.ReportExportSummary;
import edu.academic.integrity.service.AnalysisRequest;
import edu.academic.integrity.service.AnalysisRun;
import edu.academic.integrity.service.ApplicationService;
import edu.academic.integrity.service.AssignmentPlan;
import edu.academic.integrity.service.CancellationToken;
import edu.academic.integrity.service.DashboardSnapshot;
import edu.academic.integrity.service.GraphSnapshot;
import edu.academic.integrity.service.LogSeverity;
import edu.academic.integrity.service.ProgressListener;
import edu.academic.integrity.service.ProgressUpdate;
import edu.academic.integrity.service.RankingQuery;
import edu.academic.integrity.service.ReportEntry;
import edu.academic.integrity.service.ResultDetail;
import edu.academic.integrity.service.SettingsSnapshot;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Converts UI actions into serialized service operations. It deliberately has
 * no Swing dependency; a graphical client marshals callbacks onto its own UI thread.
 */
public final class ApplicationController implements AutoCloseable {
    /** Callback contract for controller tasks. Callbacks run on the daemon worker thread. */
    public interface TaskCallback<T> {
        default void onProgress(ProgressUpdate update) { }
        void onSuccess(T result);
        default void onFailure(String friendlyMessage) { }
        default void onCancelled() { }
    }

    private final ApplicationService service;
    private final ExecutorService worker;
    private final Object taskMonitor = new Object();
    private TaskState<?> currentTask;
    private boolean closed;

    public ApplicationController(File projectRoot) throws ProjectException {
        this(new ApplicationService(projectRoot));
    }

    public ApplicationController(ApplicationService service) {
        if (service == null) throw new IllegalArgumentException("service cannot be null");
        this.service = service;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "academic-integrity-controller");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, failure) ->
                    this.service.logDetailedError("Uncaught controller worker failure", failure));
            return thread;
        });
    }

    // Fast/read-only projections used directly while no long-running mutation is active.
    public DashboardSnapshot dashboard() { return service.dashboard(); }
    public Document[] documents(DocumentType type) { return service.documents(type); }
    public Document findDocument(String documentId) { return service.findDocument(documentId); }
    public ResultDetail result(String caseId) { return service.result(caseId); }
    public AnalysisResult[] ranked(RankingQuery query) { return service.ranked(query); }
    public GraphSnapshot graphSnapshot() { return service.graphSnapshot(); }
    public GraphSnapshot selectPath(String fromDocumentId, String toDocumentId) {
        try {
            return service.selectPath(fromDocumentId, toDocumentId);
        } catch (RuntimeException failure) {
            service.logDetailedError("Select similarity path" + technicalDetails(failure), failure);
            throw failure;
        }
    }
    public AssignmentPlan reviewers() { return service.reviewers(); }
    public String previewReport(String caseId) {
        try {
            return service.previewReport(caseId);
        } catch (RuntimeException failure) {
            service.logDetailedError("Preview report" + technicalDetails(failure), failure);
            throw failure;
        }
    }
    public ReportEntry[] reportFiles() { return service.reportFiles(); }
    public String readReport(File file) throws IOException {
        try {
            return service.readReport(file);
        } catch (IOException | RuntimeException failure) {
            service.logDetailedError("Read archived report" + technicalDetails(failure), failure);
            throw failure;
        }
    }
    public String logs(LogSeverity severity, int maximumLines) throws IOException {
        try {
            return service.logs(severity, maximumLines);
        } catch (IOException | RuntimeException failure) {
            service.logDetailedError("Read application logs" + technicalDetails(failure), failure);
            throw failure;
        }
    }
    public SettingsSnapshot settings() { return service.settings(); }
    public AnalysisRun lastRun() { return service.lastRun(); }
    public File projectRoot() { return service.projectRoot(); }
    public void logDetailedError(String action, Throwable failure) {
        service.logDetailedError(action, failure);
    }

    public boolean isBusy() {
        synchronized (taskMonitor) { return currentTask != null; }
    }

    public boolean importFileAsync(File file, DocumentType type,
            TaskCallback<ImportSummary> callback) {
        return submit("Import selected document", callback,
                (progress, token) -> service.importFile(file, type));
    }

    public boolean importDirectoryAsync(File directory, DocumentType type,
            TaskCallback<ImportSummary> callback) {
        return submit("Import document directory", callback,
                (progress, token) -> service.importDirectory(directory, type));
    }

    public boolean removeDocumentAsync(String documentId, TaskCallback<Document> callback) {
        return submit("Remove loaded document", callback,
                (progress, token) -> service.removeDocument(documentId));
    }

    public boolean reloadCorpusAsync(TaskCallback<ImportSummary> callback) {
        return submit("Reload corpus", callback,
                (progress, token) -> service.reloadCorpus());
    }

    public boolean validateCorpusAsync(TaskCallback<ValidationSummary> callback) {
        return submit("Validate corpus", callback,
                (progress, token) -> service.validateCorpus());
    }

    public boolean runAnalysisAsync(AnalysisRequest request,
            TaskCallback<AnalysisRun> callback) {
        return submit("Run plagiarism analysis", callback,
                (progress, token) -> service.runAnalysis(request, progress, token));
    }

    public boolean suggestAssignmentsAsync(TaskCallback<AssignmentPlan> callback) {
        return submit("Suggest reviewer assignments", callback,
                (progress, token) -> service.suggestAssignments());
    }

    public boolean overrideAssignmentAsync(String caseId, String reviewerId,
            TaskCallback<AssignmentPlan> callback) {
        return submit("Override reviewer assignment", callback,
                (progress, token) -> service.overrideAssignment(caseId, reviewerId));
    }

    public boolean unassignAsync(String caseId, TaskCallback<AssignmentPlan> callback) {
        return submit("Remove reviewer assignment", callback,
                (progress, token) -> service.unassign(caseId));
    }

    public boolean exportAssignmentsAsync(File destination, TaskCallback<File> callback) {
        return submit("Export reviewer assignments", callback,
                (progress, token) -> service.exportAssignments(destination));
    }

    public boolean exportReportsAsync(TaskCallback<ReportExportSummary> callback) {
        return exportReportsAsync(null, callback);
    }

    public boolean exportReportsAsync(File outputDirectory,
            TaskCallback<ReportExportSummary> callback) {
        return submit("Export analysis reports", callback,
                (progress, token) -> service.exportReports(outputDirectory));
    }

    public boolean compressReportAsync(File source, File destination,
            TaskCallback<File> callback) {
        return submit("Compress report", callback,
                (progress, token) -> service.compressReport(source, destination));
    }

    public boolean decompressReportAsync(File source, File destination,
            TaskCallback<File> callback) {
        return submit("Decompress report", callback,
                (progress, token) -> service.decompressReport(source, destination));
    }

    public boolean saveSettingsAsync(SettingsSnapshot snapshot,
            TaskCallback<SettingsSnapshot> callback) {
        return submit("Save application settings", callback,
                (progress, token) -> service.saveSettings(snapshot));
    }

    public boolean runBenchmarksAsync(TaskCallback<String> callback) {
        return submit("Run algorithm benchmarks", callback,
                (progress, token) -> service.runBenchmarks());
    }

    /** Requests interruption and cooperative cancellation at the next safe boundary. */
    public boolean cancelCurrentTask() {
        TaskState<?> task;
        synchronized (taskMonitor) {
            task = currentTask;
            if (task == null) return false;
            task.cancelled = true;
        }
        Thread runner = task.runner;
        if (runner != null) runner.interrupt();
        return true;
    }

    private <T> boolean submit(String action, TaskCallback<T> callback, Work<T> work) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
        if (work == null) throw new IllegalArgumentException("work cannot be null");
        TaskState<T> state = new TaskState<>(action, callback);
        synchronized (taskMonitor) {
            if (closed) {
                callback.onFailure("The application is shutting down.");
                return false;
            }
            if (currentTask != null) {
                callback.onFailure("Another operation is already in progress. Please wait or cancel it.");
                return false;
            }
            currentTask = state;
        }
        try {
            worker.submit(() -> execute(state, work));
            return true;
        } catch (RuntimeException failure) {
            synchronized (taskMonitor) {
                if (currentTask == state) currentTask = null;
            }
            service.logDetailedError("Unable to schedule " + action, failure);
            callback.onFailure("The operation could not be started because the application is shutting down.");
            return false;
        }
    }

    private <T> void execute(TaskState<T> state, Work<T> work) {
        state.runner = Thread.currentThread();
        ProgressListener progress = update -> {
            if (!state.cancelled) state.callback.onProgress(update);
        };
        CancellationToken token = () -> state.cancelled || Thread.currentThread().isInterrupted();
        try {
            if (token.isCancellationRequested()) throw new CancellationException();
            state.callback.onProgress(new ProgressUpdate("starting", 0, 1,
                    state.action + " started"));
            T result = work.run(progress, token);
            if (token.isCancellationRequested()) throw new CancellationException();
            state.callback.onSuccess(result);
        } catch (CancellationException exception) {
            state.callback.onCancelled();
        } catch (Throwable failure) {
            if (state.cancelled || Thread.currentThread().isInterrupted()
                    || causedByInterruption(failure)) {
                state.callback.onCancelled();
            } else {
                service.logDetailedError(state.action + technicalDetails(failure), failure);
                state.callback.onFailure(friendlyMessage(failure));
            }
        } finally {
            Thread.interrupted();
            synchronized (taskMonitor) {
                if (currentTask == state) currentTask = null;
            }
        }
    }

    private String friendlyMessage(Throwable failure) {
        Throwable cause = rootCause(failure);
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
            String message = cause.getMessage();
            if (message != null && !message.isBlank()) return message;
        }
        if (cause instanceof IOException) {
            return "The file operation could not be completed. Check the selected path and permissions.";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "The operation could not be completed. Details were written to the error log."
                : "The operation could not be completed: " + message;
    }

    private Throwable rootCause(Throwable failure) {
        Throwable result = failure;
        int depth = 0;
        while (result.getCause() != null && result.getCause() != result && depth++ < 12) {
            result = result.getCause();
        }
        return result;
    }

    private boolean causedByInterruption(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof InterruptedException
                    || current instanceof CancellationException) return true;
            current = current.getCause();
        }
        return false;
    }

    private String technicalDetails(Throwable failure) {
        StringBuilder detail = new StringBuilder(" | ");
        Throwable current = failure;
        int causes = 0;
        while (current != null && causes++ < 4) {
            if (causes > 1) detail.append(" caused by ");
            detail.append(current.getClass().getName()).append(": ").append(current.getMessage());
            current = current.getCause();
        }
        StackTraceElement[] trace = failure.getStackTrace();
        int limit = Math.min(trace.length, 6);
        for (int i = 0; i < limit; i++) detail.append(" @ ").append(trace[i]);
        return detail.toString();
    }

    @Override
    public void close() { shutdown(); }

    public void shutdown() {
        TaskState<?> task;
        synchronized (taskMonitor) {
            if (closed) return;
            closed = true;
            task = currentTask;
            if (task != null) task.cancelled = true;
        }
        if (task != null && task.runner != null) task.runner.interrupt();
        worker.shutdownNow();
        service.shutdown();
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(ProgressListener progress, CancellationToken cancellation) throws Exception;
    }

    private static final class TaskState<T> {
        private final String action;
        private final TaskCallback<T> callback;
        private volatile boolean cancelled;
        private volatile Thread runner;

        private TaskState(String action, TaskCallback<T> callback) {
            this.action = action;
            this.callback = callback;
        }
    }
}
