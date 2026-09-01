package edu.academic.integrity.service;

import edu.academic.integrity.algorithms.compression.HuffmanCodec;
import edu.academic.integrity.algorithms.sort.GenericSorts;
import edu.academic.integrity.app.AcademicIntegritySystem;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.exception.ProjectException;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.LogReader;
import edu.academic.integrity.io.ValidationSummary;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.model.RelationshipEdge;
import edu.academic.integrity.model.Reviewer;
import edu.academic.integrity.model.SimilarityGroup;
import edu.academic.integrity.report.ReportExportSummary;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CancellationException;

/**
 * Headless UI integration boundary. All long-lived domain state remains in the
 * swappable application facade; this layer returns only immutable DTOs and arrays.
 */
public final class ApplicationService implements AutoCloseable {
    private static final int RECENT_ACTIVITY_LINES = 8;
    private static final int MAX_REPORT_BYTES = 16 * 1024 * 1024;

    private final File projectRoot;
    private final HuffmanCodec huffman = new HuffmanCodec();
    private AcademicIntegritySystem facade;
    private SourceRegistration[] customSources = new SourceRegistration[4];
    private int customSourceCount;
    private String[] removedDocumentIds = new String[4];
    private int removedDocumentCount;
    private AnalysisRun lastRun;
    private CopyingPath selectedPath = emptyPath();
    private int lastSuggestedFlowCount;
    private boolean assignmentSuggestionRun;
    private boolean closed;

    public ApplicationService(File projectRoot) throws ProjectException {
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot cannot be null");
        this.projectRoot = projectRoot.getAbsoluteFile();
        facade = new AcademicIntegritySystem(this.projectRoot);
        facade.reloadCorpus();
    }

    public synchronized DashboardSnapshot dashboard() {
        ensureOpen();
        AnalysisResult[] results = facade.rankedResults();
        int highRisk = 0;
        for (AnalysisResult result : results) {
            String risk = result.score().riskLabel();
            if ("HIGH".equals(risk) || "CRITICAL".equals(risk)) highRisk++;
        }
        int pending = unassigned(facade.reviewableResults(), facade.assignments()).length;
        String recent;
        try {
            recent = LogReader.tail(facade.logger().activityFile(), RECENT_ACTIVITY_LINES);
        } catch (IOException exception) {
            facade.logger().error("Unable to read recent dashboard activity", exception);
            recent = "Recent activity is temporarily unavailable.";
        }
        return new DashboardSnapshot(facade.submissions().length, facade.references().length,
                results.length, highRisk, pending, recent);
    }

    public synchronized Document[] documents(DocumentType type) {
        ensureOpen();
        requireType(type);
        Document[] result = type == DocumentType.SUBMISSION
                ? facade.submissions() : facade.references();
        GenericSorts.mergeSort(result, (first, second) -> compareText(first.id(), second.id()));
        return result;
    }

    public synchronized Document findDocument(String documentId) {
        ensureOpen();
        return facade.findDocument(requireText(documentId, "document ID"));
    }

    public synchronized ImportSummary importFile(File file, DocumentType type) {
        ensureOpen();
        requireType(type);
        requireReadableFile(file, "document");
        ImportSummary summary = facade.importFile(file.getAbsoluteFile(), type);
        registerCustomSource(file, type, true);
        forgetRemoved(summary.documents());
        if (summary.importedCount() > 0) clearDerivedViewState();
        return summary;
    }

    public synchronized ImportSummary importDirectory(File directory, DocumentType type) {
        ensureOpen();
        requireType(type);
        if (directory == null || !directory.isDirectory()) {
            throw new IllegalArgumentException("Select a readable document directory");
        }
        ImportSummary summary = facade.importDocuments(directory.getAbsoluteFile(), type);
        registerCustomSource(directory, type, false);
        forgetRemoved(summary.documents());
        if (summary.importedCount() > 0) clearDerivedViewState();
        return summary;
    }

    public synchronized Document removeDocument(String documentId) {
        ensureOpen();
        String id = requireText(documentId, "document ID");
        Document removed = facade.removeDocument(id);
        if (removed == null) throw new IllegalArgumentException("Document was not found: " + id);
        rememberRemoved(id);
        clearDerivedViewState();
        return removed;
    }

    public synchronized ImportSummary reloadCorpus() {
        ensureOpen();
        ImportSummary summary = facade.reloadCorpus();
        removedDocumentCount = 0;
        clearDerivedViewState();
        return summary;
    }

    public synchronized ValidationSummary validateCorpus() {
        ensureOpen();
        return facade.validateImportedSources();
    }

    public synchronized AnalysisRun runAnalysis(AnalysisRequest request,
            ProgressListener progress, CancellationToken cancellation) {
        ensureOpen();
        if (request == null) throw new IllegalArgumentException("analysis request cannot be null");
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;
        CancellationToken token = cancellation == null ? CancellationToken.NONE : cancellation;
        checkCancellation(token);
        listener.onProgress(new ProgressUpdate("validation", 0, 4,
                "Validating the selected corpus and analysis options"));
        applyRequest(request);
        if (facade.findDocument(request.submissionId()) == null) {
            throw new IllegalArgumentException("Submission was not found: " + request.submissionId());
        }
        if (!request.allReferences() && facade.findDocument(request.referenceId()) == null) {
            throw new IllegalArgumentException("Reference was not found: " + request.referenceId());
        }
        checkCancellation(token);
        listener.onProgress(new ProgressUpdate("preparation", 1, 4,
                "Preparing documents and candidate pairs"));

        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = usedMemory(runtime);
        long started = System.nanoTime();
        listener.onProgress(new ProgressUpdate("analysis", 2, 4,
                request.parallel() ? "Running bounded parallel comparison"
                        : "Running sequential comparison"));
        BatchAnalysisResult batch = facade.analyzeSubmission(request.submissionId(),
                request.referenceId(), request.parallel());
        long elapsed = System.nanoTime() - started;
        long memoryAfter = usedMemory(runtime);
        checkCancellation(token);
        listener.onProgress(new ProgressUpdate("indexing", 3, 4,
                "Indexing ranked cases and similarity relationships"));
        lastRun = new AnalysisRun(batch, batch.results(), memoryBefore, memoryAfter, elapsed);
        selectedPath = emptyPath();
        lastSuggestedFlowCount = 0;
        assignmentSuggestionRun = false;
        listener.onProgress(new ProgressUpdate("complete", 4, 4,
                "Analysis completed successfully"));
        return lastRun;
    }

    public synchronized ResultDetail result(String caseId) {
        ensureOpen();
        AnalysisResult result = facade.findResult(requireText(caseId, "case ID"));
        if (result == null) return null;
        return new ResultDetail(result, lastRun == null ? 0L : lastRun.memoryDeltaBytes());
    }

    /** Filters custom-index ranking output; no UI/table sorting is involved. */
    public synchronized AnalysisResult[] ranked(RankingQuery query) {
        ensureOpen();
        RankingQuery effective = query == null ? RankingQuery.descending() : query;
        AnalysisResult[] ranked = facade.rankedSubmissions(!effective.isDescending());
        int count = 0;
        for (AnalysisResult result : ranked) if (matches(result, effective)) count++;
        AnalysisResult[] filtered = new AnalysisResult[count];
        int output = 0;
        for (AnalysisResult result : ranked) {
            if (matches(result, effective)) filtered[output++] = result;
        }
        return filtered;
    }

    public synchronized GraphSnapshot graphSnapshot() {
        ensureOpen();
        return createGraphSnapshot(selectedPath);
    }

    public synchronized GraphSnapshot selectPath(String fromDocumentId, String toDocumentId) {
        ensureOpen();
        selectedPath = facade.copyingPath(requireText(fromDocumentId, "starting document ID"),
                requireText(toDocumentId, "destination document ID"));
        return createGraphSnapshot(selectedPath);
    }

    public synchronized AssignmentPlan reviewers() {
        ensureOpen();
        return assignmentPlan();
    }

    public synchronized AssignmentPlan suggestAssignments() {
        ensureOpen();
        CaseAssignment[] assignments = facade.assignReviewers();
        lastSuggestedFlowCount = assignments.length;
        assignmentSuggestionRun = true;
        return assignmentPlan();
    }

    public synchronized AssignmentPlan overrideAssignment(String caseId, String reviewerId) {
        ensureOpen();
        facade.overrideReviewerAssignment(requireText(caseId, "case ID"),
                requireText(reviewerId, "reviewer ID"));
        return assignmentPlan();
    }

    public synchronized AssignmentPlan unassign(String caseId) {
        ensureOpen();
        facade.removeReviewerAssignment(requireText(caseId, "case ID"));
        return assignmentPlan();
    }

    public synchronized File exportAssignments(File destinationFile) throws IOException {
        ensureOpen();
        if (destinationFile == null) throw new IllegalArgumentException("Choose an export file");
        return facade.exportAssignments(destinationFile.getAbsoluteFile());
    }

    public synchronized String previewReport(String caseId) {
        ensureOpen();
        return facade.previewReport(requireText(caseId, "case ID"));
    }

    public synchronized ReportExportSummary exportReports() throws IOException {
        ensureOpen();
        return facade.exportReports();
    }

    public synchronized ReportExportSummary exportReports(File outputDirectory) throws IOException {
        ensureOpen();
        if (outputDirectory == null) return facade.exportReports();
        return facade.exportReports(outputDirectory.getAbsoluteFile());
    }

    public synchronized ReportEntry[] reportFiles() {
        ensureOpen();
        File root = facade.paths().reports;
        // Collected in a single pass. Counting first and filling second walked the
        // directory twice, and exporting reports writes into that same directory: a file
        // appearing between the two walks overran the exact-sized array, and one
        // disappearing left trailing nulls for the comparator below to dereference.
        ReportCollector collector = new ReportCollector();
        collectReports(root, root, collector);
        ReportEntry[] entries = collector.toArray();
        GenericSorts.mergeSort(entries, (first, second) -> {
            int modified = Long.compare(second.modifiedMillis(), first.modifiedMillis());
            return modified != 0 ? modified : compareText(first.relativePath(), second.relativePath());
        });
        return entries;
    }

    public synchronized String readReport(File file) throws IOException {
        ensureOpen();
        File report = requireReportFile(file);
        byte[] bytes = readBounded(report);
        return report.getName().toLowerCase().endsWith(".huff")
                ? huffman.decompressText(bytes) : new String(bytes, StandardCharsets.UTF_8);
    }

    public synchronized File compressReport(File source, File destination) throws IOException {
        ensureOpen();
        File input = requireReadableFile(source, "report");
        File output = destination == null
                ? new File(input.getParentFile(), input.getName() + ".huff")
                : destination.getAbsoluteFile();
        byte[] compressed = huffman.compress(readBounded(input));
        atomicWrite(output, compressed);
        facade.logger().info("Compressed report to " + output.getAbsolutePath());
        return output;
    }

    public synchronized File decompressReport(File source, File destination) throws IOException {
        ensureOpen();
        File input = requireReadableFile(source, "compressed report");
        String name = input.getName();
        if (!name.toLowerCase().endsWith(".huff")) {
            throw new IllegalArgumentException("Select a .huff report to decompress");
        }
        String decodedName = name.substring(0, name.length() - 5) + ".decoded.txt";
        File output = destination == null ? new File(input.getParentFile(), decodedName)
                : destination.getAbsoluteFile();
        atomicWrite(output, huffman.decompress(readBounded(input)));
        facade.logger().info("Decompressed report to " + output.getAbsolutePath());
        return output;
    }

    public synchronized String logs(LogSeverity severity, int maximumLines) throws IOException {
        ensureOpen();
        if (maximumLines < 1) throw new IllegalArgumentException("Log line limit must be positive");
        LogSeverity effective = severity == null ? LogSeverity.ALL : severity;
        if (effective == LogSeverity.ACTIVITY) {
            return LogReader.tail(facade.logger().activityFile(), maximumLines);
        }
        if (effective == LogSeverity.ERROR) {
            return LogReader.tail(facade.logger().errorFile(), maximumLines);
        }
        return "ACTIVITY\n--------\n"
                + LogReader.tail(facade.logger().activityFile(), maximumLines)
                + "\nERRORS\n------\n"
                + LogReader.tail(facade.logger().errorFile(), maximumLines);
    }

    public synchronized SettingsSnapshot settings() {
        ensureOpen();
        return SettingsSnapshot.from(facade.settings(), facade.paths());
    }

    /** Atomically saves settings, then swaps in a successfully initialized/reimported facade. */
    public synchronized SettingsSnapshot saveSettings(SettingsSnapshot snapshot)
            throws IOException, ProjectException {
        ensureOpen();
        if (snapshot == null) throw new IllegalArgumentException("settings cannot be null");
        Settings candidate = snapshot.toSettings();
        candidate.validate();
        File settingsFile = facade.paths().settings;
        byte[] previous = settingsFile.isFile() ? Files.readAllBytes(settingsFile.toPath()) : null;
        atomicWrite(settingsFile, serialize(candidate).getBytes(StandardCharsets.UTF_8));

        AcademicIntegritySystem replacement = null;
        try {
            replacement = new AcademicIntegritySystem(projectRoot);
            replacement.reloadCorpus();
            for (int i = 0; i < customSourceCount; i++) {
                SourceRegistration source = customSources[i];
                if (isConfiguredSource(replacement, source)) continue;
                if (source.singleFile) replacement.importFile(source.source, source.type);
                else replacement.importDocuments(source.source, source.type);
            }
            for (int i = 0; i < removedDocumentCount; i++) {
                replacement.removeDocument(removedDocumentIds[i]);
            }
        } catch (RuntimeException | ProjectException exception) {
            if (replacement != null) replacement.shutdown();
            restoreSettings(settingsFile, previous);
            throw exception;
        }

        AcademicIntegritySystem previousFacade = facade;
        facade = replacement;
        previousFacade.shutdown();
        clearDerivedViewState();
        facade.logger().info("Settings saved atomically and the corpus was reloaded");
        return settings();
    }

    public synchronized String runBenchmarks() throws IOException {
        ensureOpen();
        return facade.runBenchmarks();
    }

    public synchronized void logDetailedError(String action, Throwable failure) {
        if (closed || facade == null) return;
        String context = action == null || action.isBlank() ? "UI operation failed" : action;
        facade.logger().error(context, failure);
    }

    public synchronized AnalysisRun lastRun() {
        ensureOpen();
        return lastRun;
    }

    public synchronized File projectRoot() { return projectRoot; }

    @Override
    public synchronized void close() { shutdown(); }

    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        facade.shutdown();
    }

    private void applyRequest(AnalysisRequest request) {
        Settings settings = facade.settings();
        if (request.hasCandidateThreshold()) {
            settings.candidateThreshold = request.candidateThreshold();
        }
        if (request.hasReviewThreshold()) settings.reviewThreshold = request.reviewThreshold();
        if (request.hasGraphEdgeThreshold()) {
            settings.graphEdgeThreshold = request.graphEdgeThreshold();
        }
        settings.enableExact = request.exactEnabled();
        settings.enableShingle = request.shingleEnabled();
        settings.enableFuzzy = request.fuzzyEnabled();
        settings.enableGraph = request.graphEnabled();
        settings.validate();
    }

    private GraphSnapshot createGraphSnapshot(CopyingPath path) {
        Document[] submissions = facade.submissions();
        Document[] references = facade.references();
        Document[] documents = new Document[submissions.length + references.length];
        System.arraycopy(submissions, 0, documents, 0, submissions.length);
        System.arraycopy(references, 0, documents, submissions.length, references.length);
        SimilarityGroup[] groups = facade.similarityGroups();
        AnalysisResult[] cases = facade.rankedResults();
        GraphSnapshot.Node[] nodes = new GraphSnapshot.Node[documents.length];
        for (int i = 0; i < documents.length; i++) {
            nodes[i] = new GraphSnapshot.Node(documents[i].id(), documents[i].title(),
                    documents[i].type(), riskFor(documents[i].id(), cases),
                    componentFor(documents[i].id(), groups));
        }
        GenericSorts.mergeSort(nodes,
                (first, second) -> compareText(first.id(), second.id()));

        RelationshipEdge[] relationships = facade.relationships();
        GraphSnapshot.Edge[] edges = new GraphSnapshot.Edge[relationships.length];
        for (int i = 0; i < relationships.length; i++) {
            RelationshipEdge edge = relationships[i];
            edges[i] = new GraphSnapshot.Edge(edge.firstDocumentId(), edge.secondDocumentId(),
                    edge.similarity(), isSelectedEdge(edge, path));
        }
        GenericSorts.mergeSort(edges, (first, second) -> {
            int firstId = compareText(first.firstDocumentId(), second.firstDocumentId());
            return firstId != 0 ? firstId
                    : compareText(first.secondDocumentId(), second.secondDocumentId());
        });
        return new GraphSnapshot(nodes, edges, groups, path);
    }

    private AssignmentPlan assignmentPlan() {
        Reviewer[] reviewers = facade.reviewers();
        GenericSorts.mergeSort(reviewers,
                (first, second) -> compareText(first.id(), second.id()));
        CaseAssignment[] assignments = facade.assignments();
        AnalysisResult[] unassigned = unassigned(facade.reviewableResults(), assignments);
        AssignmentPlan.ReviewerUtilization[] utilization =
                new AssignmentPlan.ReviewerUtilization[reviewers.length];
        for (int i = 0; i < reviewers.length; i++) {
            int used = 0;
            for (CaseAssignment assignment : assignments) {
                if (assignment.reviewer().id().equals(reviewers[i].id())) used++;
            }
            utilization[i] = new AssignmentPlan.ReviewerUtilization(reviewers[i], used);
        }
        int flow = assignmentSuggestionRun ? lastSuggestedFlowCount : 0;
        return new AssignmentPlan(reviewers, assignments, unassigned, utilization, flow);
    }

    private AnalysisResult[] unassigned(AnalysisResult[] reviewable,
            CaseAssignment[] assignments) {
        int count = 0;
        for (AnalysisResult result : reviewable) {
            if (!isAssigned(result.caseId(), assignments)) count++;
        }
        AnalysisResult[] result = new AnalysisResult[count];
        int output = 0;
        for (AnalysisResult candidate : reviewable) {
            if (!isAssigned(candidate.caseId(), assignments)) result[output++] = candidate;
        }
        return result;
    }

    private boolean isAssigned(String caseId, CaseAssignment[] assignments) {
        for (CaseAssignment assignment : assignments) {
            if (assignment.result().caseId().equals(caseId)) return true;
        }
        return false;
    }

    private boolean matches(AnalysisResult result, RankingQuery query) {
        if (query.filtersRisk()
                && !result.score().riskLabel().equalsIgnoreCase(query.riskLabel())) return false;
        if (!query.filtersDocument()) return true;
        String term = query.documentId().toLowerCase();
        return result.submission().id().toLowerCase().contains(term)
                || result.submission().title().toLowerCase().contains(term);
    }

    private String riskFor(String documentId, AnalysisResult[] cases) {
        double highest = 0.0;
        for (AnalysisResult result : cases) {
            if (result.submission().id().equals(documentId)
                    || result.reference().id().equals(documentId)) {
                highest = Math.max(highest, result.score().total());
            }
        }
        if (highest >= 0.75) return "CRITICAL";
        if (highest >= 0.55) return "HIGH";
        if (highest >= 0.35) return "MEDIUM";
        return "LOW";
    }

    private String componentFor(String documentId, SimilarityGroup[] groups) {
        for (SimilarityGroup group : groups) {
            for (String member : group.documentIds()) {
                if (member.equals(documentId)) return group.id();
            }
        }
        return "";
    }

    private boolean isSelectedEdge(RelationshipEdge edge, CopyingPath path) {
        String[] ids = path == null ? new String[0] : path.documentIds();
        for (int i = 1; i < ids.length; i++) {
            boolean forward = ids[i - 1].equals(edge.firstDocumentId())
                    && ids[i].equals(edge.secondDocumentId());
            boolean reverse = ids[i].equals(edge.firstDocumentId())
                    && ids[i - 1].equals(edge.secondDocumentId());
            if (forward || reverse) return true;
        }
        return false;
    }

    private void collectReports(File root, File directory, ReportCollector collector) {
        if (directory == null || !directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectReports(root, child, collector);
            } else if (isReport(child)) {
                String relative = root.toPath().relativize(child.toPath()).toString();
                collector.add(new ReportEntry(child, relative,
                        child.getName().toLowerCase().endsWith(".huff"),
                        child.length(), child.lastModified()));
            }
        }
    }

    /** Growable buffer so the directory only has to be walked once. */
    private static final class ReportCollector {
        private ReportEntry[] entries = new ReportEntry[16];
        private int size;

        void add(ReportEntry entry) {
            if (size == entries.length) {
                ReportEntry[] replacement = new ReportEntry[entries.length * 2];
                System.arraycopy(entries, 0, replacement, 0, entries.length);
                entries = replacement;
            }
            entries[size++] = entry;
        }

        ReportEntry[] toArray() {
            ReportEntry[] result = new ReportEntry[size];
            System.arraycopy(entries, 0, result, 0, size);
            return result;
        }
    }

    private boolean isReport(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".huff");
    }

    private File requireReportFile(File file) throws IOException {
        File report = requireReadableFile(file, "report").getCanonicalFile();
        File root = facade.paths().reports.getCanonicalFile();
        String rootPath = root.getPath();
        String reportPath = report.getPath();
        if (!reportPath.equals(rootPath)
                && !reportPath.startsWith(rootPath + File.separator)) {
            throw new IllegalArgumentException("The selected file is outside the reports directory");
        }
        return report;
    }

    private byte[] readBounded(File file) throws IOException {
        long maximum = Math.max(MAX_REPORT_BYTES, facade.settings().maxFileBytes);
        if (file.length() > maximum) {
            throw new IllegalArgumentException("The selected report is too large to open safely");
        }
        return Files.readAllBytes(file.toPath());
    }

    private void atomicWrite(File destination, byte[] content) throws IOException {
        File absolute = destination.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent == null) throw new IOException("The destination needs a parent directory");
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create destination directory: " + parent);
        }
        File temporary = new File(parent, absolute.getName() + ".tmp");
        Files.write(temporary.toPath(), content);
        replace(temporary, absolute);
    }

    private void replace(File temporary, File destination) throws IOException {
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restoreSettings(File settingsFile, byte[] previous) throws IOException {
        if (previous == null) {
            Files.deleteIfExists(settingsFile.toPath());
        } else {
            atomicWrite(settingsFile, previous);
        }
    }

    private String serialize(Settings settings) {
        StringBuilder output = new StringBuilder(768);
        append(output, "enableExact", settings.enableExact);
        append(output, "enableShingle", settings.enableShingle);
        append(output, "enableFuzzy", settings.enableFuzzy);
        append(output, "enableGraph", settings.enableGraph);
        append(output, "wordShingleSize", settings.wordShingleSize);
        append(output, "characterShingleSize", settings.characterShingleSize);
        append(output, "minExactPhraseCharacters", settings.minExactPhraseCharacters);
        append(output, "candidateThreshold", settings.candidateThreshold);
        append(output, "reviewThreshold", settings.reviewThreshold);
        append(output, "graphEdgeThreshold", settings.graphEdgeThreshold);
        append(output, "exactWeight", settings.exactWeight);
        append(output, "shingleWeight", settings.shingleWeight);
        append(output, "fuzzyWeight", settings.fuzzyWeight);
        append(output, "graphWeight", settings.graphWeight);
        append(output, "maxEvidence", settings.maxEvidence);
        append(output, "workerCount", settings.workerCount);
        append(output, "maxFileBytes", settings.maxFileBytes);
        append(output, "removeStopwords", settings.removeStopwords);
        append(output, "submissionDirectory", settings.submissionDirectory);
        append(output, "referenceDirectory", settings.referenceDirectory);
        append(output, "reportDirectory", settings.reportDirectory);
        append(output, "stopwordFile", settings.stopwordFile);
        return output.toString();
    }

    private void append(StringBuilder output, String key, Object value) {
        output.append(key).append('=').append(value).append('\n');
    }

    private boolean isConfiguredSource(AcademicIntegritySystem system,
            SourceRegistration source) {
        if (source.singleFile) return false;
        File configured = source.type == DocumentType.SUBMISSION
                ? system.paths().submissions : system.paths().references;
        try {
            return configured.getCanonicalFile().equals(source.source.getCanonicalFile());
        } catch (IOException ignored) {
            return configured.getAbsolutePath().equalsIgnoreCase(source.source.getAbsolutePath());
        }
    }

    private void registerCustomSource(File source, DocumentType type, boolean singleFile) {
        File absolute = source.getAbsoluteFile();
        for (int i = 0; i < customSourceCount; i++) {
            SourceRegistration current = customSources[i];
            if (current.type == type && current.singleFile == singleFile
                    && current.source.getAbsolutePath().equalsIgnoreCase(absolute.getAbsolutePath())) {
                return;
            }
        }
        if (customSourceCount == customSources.length) {
            SourceRegistration[] replacement = new SourceRegistration[customSources.length * 2];
            System.arraycopy(customSources, 0, replacement, 0, customSources.length);
            customSources = replacement;
        }
        customSources[customSourceCount++] = new SourceRegistration(absolute, type, singleFile);
    }

    private void rememberRemoved(String id) {
        for (int i = 0; i < removedDocumentCount; i++) {
            if (removedDocumentIds[i].equals(id)) return;
        }
        if (removedDocumentCount == removedDocumentIds.length) {
            String[] replacement = new String[removedDocumentIds.length * 2];
            System.arraycopy(removedDocumentIds, 0, replacement, 0, removedDocumentIds.length);
            removedDocumentIds = replacement;
        }
        removedDocumentIds[removedDocumentCount++] = id;
    }

    private void forgetRemoved(Document[] imported) {
        for (Document document : imported) forgetRemoved(document.id());
    }

    private void forgetRemoved(String id) {
        for (int i = 0; i < removedDocumentCount; i++) {
            if (removedDocumentIds[i].equals(id)) {
                int moved = removedDocumentCount - i - 1;
                if (moved > 0) {
                    System.arraycopy(removedDocumentIds, i + 1, removedDocumentIds, i, moved);
                }
                removedDocumentIds[--removedDocumentCount] = null;
                return;
            }
        }
    }

    private void clearDerivedViewState() {
        lastRun = null;
        selectedPath = emptyPath();
        lastSuggestedFlowCount = 0;
        assignmentSuggestionRun = false;
    }

    private static CopyingPath emptyPath() {
        return new CopyingPath(new String[0], new double[0], Double.POSITIVE_INFINITY);
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void checkCancellation(CancellationToken token) {
        if (Thread.currentThread().isInterrupted() || token.isCancellationRequested()) {
            throw new CancellationException("Operation cancelled");
        }
    }

    private static int compareText(String first, String second) {
        int insensitive = first.compareToIgnoreCase(second);
        return insensitive != 0 ? insensitive : first.compareTo(second);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static void requireType(DocumentType type) {
        if (type == null) throw new IllegalArgumentException("document type is required");
    }

    private static File requireReadableFile(File file, String description) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Select a readable " + description + " file");
        }
        return file.getAbsoluteFile();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("The application service has been shut down");
    }

    private static final class SourceRegistration {
        private final File source;
        private final DocumentType type;
        private final boolean singleFile;

        private SourceRegistration(File source, DocumentType type, boolean singleFile) {
            this.source = source;
            this.type = type;
            this.singleFile = singleFile;
        }
    }
}
