package edu.academic.integrity.app;

import edu.academic.integrity.analytics.SimilarityNetwork;
import edu.academic.integrity.benchmark.BenchmarkService;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.config.SettingsLoader;
import edu.academic.integrity.core.BatchAnalyzer;
import edu.academic.integrity.core.DocumentAnalyzer;
import edu.academic.integrity.exception.ProjectException;
import edu.academic.integrity.exception.ValidationException;
import edu.academic.integrity.index.DocumentIndex;
import edu.academic.integrity.index.PersistentDocumentIndex;
import edu.academic.integrity.index.RangeAnalytics;
import edu.academic.integrity.index.ResultIndex;
import edu.academic.integrity.io.ActivityLogger;
import edu.academic.integrity.io.CorpusImporter;
import edu.academic.integrity.io.CorpusStore;
import edu.academic.integrity.io.CorpusValidationService;
import edu.academic.integrity.io.DocumentFileParser;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.ProjectPaths;
import edu.academic.integrity.io.ReviewerLoader;
import edu.academic.integrity.io.StopwordLoader;
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
import edu.academic.integrity.report.ReportWriter;
import edu.academic.integrity.review.ReviewerAssignmentService;
import edu.academic.integrity.structures.HashSet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Stateful application facade used by both the console and deterministic demo. */
public final class AcademicIntegritySystem {
    private final ProjectPaths paths;
    private final ActivityLogger logger;
    private final Settings settings;
    private final String[] stopwords;
    private final CorpusStore corpus = new CorpusStore();
    private final DocumentIndex documentIndex = new DocumentIndex();
    private final PersistentDocumentIndex persistentDocumentIndex;
    private final ResultIndex resultIndex = new ResultIndex();
    private final DocumentFileParser parser;
    private final CorpusImporter importer;
    private final CorpusValidationService validator;
    private final DocumentAnalyzer documentAnalyzer;
    private final BatchAnalyzer batchAnalyzer;
    private final ReviewerAssignmentService assignmentService = new ReviewerAssignmentService();
    private final BenchmarkService benchmarkService;
    private File[] submissionSources = new File[4];
    private File[] referenceSources = new File[4];
    private int submissionSourceCount;
    private int referenceSourceCount;

    private BatchAnalysisResult lastBatch;
    private SimilarityNetwork similarityNetwork;
    private RangeAnalytics rangeAnalytics = new RangeAnalytics(new double[0], 0.0);
    private CaseAssignment[] assignments = new CaseAssignment[0];

    public AcademicIntegritySystem(File projectRoot) throws ProjectException {
        ProjectPaths bootstrapPaths = new ProjectPaths(projectRoot);
        bootstrapPaths.createRequiredDirectories();
        ActivityLogger bootstrapLogger = new ActivityLogger(bootstrapPaths.logs);
        Settings loaded;
        try {
            loaded = SettingsLoader.load(bootstrapPaths.settings);
        } catch (ValidationException exception) {
            bootstrapLogger.error("Settings file is invalid; safe defaults were loaded", exception);
            loaded = new Settings();
            loaded.validate();
        }
        settings = loaded;
        paths = new ProjectPaths(projectRoot, settings);
        paths.createRequiredDirectories();
        logger = bootstrapLogger;
        persistentDocumentIndex = new PersistentDocumentIndex(paths.documentIndexSnapshot, logger);
        stopwords = StopwordLoader.load(paths.stopwords, logger);
        parser = new DocumentFileParser(settings.maxFileBytes);
        importer = new CorpusImporter(parser, logger, paths.quarantine);
        validator = new CorpusValidationService(parser, logger);
        documentAnalyzer = new DocumentAnalyzer(settings, stopwords);
        batchAnalyzer = new BatchAnalyzer(documentAnalyzer, settings);
        benchmarkService = new BenchmarkService(settings, stopwords);
        logger.info("Academic integrity system initialized at " + paths.root());
    }

    public ImportSummary importDocuments(File directory, DocumentType type) {
        requireDocumentType(type);
        registerSource(directory, type);
        ImportSummary summary = importSource(directory, type);
        if (summary.importedCount() > 0) invalidateAnalysisState();
        return summary;
    }

    /** Imports one selected text file and registers it as a reloadable corpus source. */
    public ImportSummary importFile(File file, DocumentType type) {
        requireDocumentType(type);
        registerSource(file, type);
        ImportSummary summary = importer.importFile(file, type, corpus);
        indexImportedDocuments(summary);
        if (summary.importedCount() > 0) invalidateAnalysisState();
        return summary;
    }

    /**
     * Clears the in-memory and persistent catalogs, then reimports every source
     * registered during this session (or the configured standard directories).
     */
    public ImportSummary reloadCorpus() {
        if (submissionSourceCount == 0) {
            registerSource(paths.submissions, DocumentType.SUBMISSION);
        }
        if (referenceSourceCount == 0) {
            registerSource(paths.references, DocumentType.REFERENCE);
        }
        File[] submissionReloadSources = sourceCopy(submissionSources, submissionSourceCount);
        File[] referenceReloadSources = sourceCopy(referenceSources, referenceSourceCount);

        persistentDocumentIndex.clear();
        corpus.clear();
        documentIndex.clear();
        invalidateAnalysisState();

        ImportSummary combined = new ImportSummary();
        for (File source : submissionReloadSources) {
            combined.include(importSource(source, DocumentType.SUBMISSION));
        }
        for (File source : referenceReloadSources) {
            combined.include(importSource(source, DocumentType.REFERENCE));
        }
        logger.info("Reloaded corpus with " + combined.importedCount()
                + " document(s) and " + combined.errorCount() + " error(s)");
        return combined;
    }

    /** Removes one loaded document from every catalog without deleting its source file. */
    public Document removeDocument(String documentId) {
        Document document = documentIndex.get(documentId);
        if (document == null) return null;
        if (corpus.find(documentId) == null) {
            throw new IllegalStateException("Document catalogs are inconsistent before removal");
        }
        persistentDocumentIndex.remove(documentId);
        Document removedFromCorpus = corpus.remove(documentId);
        Document removedFromIndex = documentIndex.remove(documentId);
        if (removedFromCorpus == null || removedFromIndex == null) {
            logger.error("Document catalog mismatch while removing " + documentId, null);
            throw new IllegalStateException("Document catalogs became inconsistent during removal");
        }
        invalidateAnalysisState();
        logger.info("Removed loaded " + document.type() + " " + documentId
                + " without deleting its source file");
        return document;
    }

    /** Looks up a loaded document through the custom document index. */
    public Document findDocument(String documentId) {
        return documentIndex.get(documentId);
    }

    public ImportSummary importStandardSubmissions() {
        return importDocuments(paths.submissions, DocumentType.SUBMISSION);
    }

    public ImportSummary importStandardReferences() {
        return importDocuments(paths.references, DocumentType.REFERENCE);
    }

    public ValidationSummary validateStandardCorpus() {
        return validator.validate(paths.submissions, paths.references);
    }

    public ValidationSummary validateImportedSources() {
        File[] submissions = submissionSourceCount == 0
                ? new File[]{paths.submissions} : sourceCopy(submissionSources, submissionSourceCount);
        File[] references = referenceSourceCount == 0
                ? new File[]{paths.references} : sourceCopy(referenceSources, referenceSourceCount);
        return validator.validate(submissions, references);
    }

    public BatchAnalysisResult analyzeOneSubmission(String submissionId) {
        return analyzeSubmissionInternal(submissionId, null, false, false);
    }

    /**
     * Exhaustively analyzes one submission against one selected reference, or
     * all loaded references when {@code referenceId} is null or blank.
     */
    public BatchAnalysisResult analyzeSubmission(String submissionId, String referenceId,
                                                  boolean parallel) {
        return analyzeSubmissionInternal(submissionId, referenceId, parallel, true);
    }

    private BatchAnalysisResult analyzeSubmissionInternal(String submissionId, String referenceId,
            boolean parallel, boolean shortlistCompleteCorpus) {
        Document submission = documentIndex.get(submissionId);
        if (submission == null || submission.type() != DocumentType.SUBMISSION) {
            throw new IllegalArgumentException("Unknown submission ID: " + submissionId);
        }
        Document[] selectedReferences;
        if (referenceId == null || referenceId.isBlank()) {
            selectedReferences = references();
        } else {
            Document reference = documentIndex.get(referenceId);
            if (reference == null || reference.type() != DocumentType.REFERENCE) {
                throw new IllegalArgumentException("Unknown reference ID: " + referenceId);
            }
            selectedReferences = new Document[]{reference};
        }
        if (selectedReferences.length == 0) {
            throw new IllegalStateException("No reference documents are loaded");
        }
        boolean completeCorpus = referenceId == null || referenceId.isBlank();
        BatchAnalysisResult base = completeCorpus && shortlistCompleteCorpus
                ? batchAnalyzer.analyze("TARGET", new Document[]{submission},
                        selectedReferences, parallel)
                : batchAnalyzer.analyzeAll("TARGET", new Document[]{submission},
                        selectedReferences, parallel);
        AnalysisResult[] enriched = enrichAndStore(base.results());
        lastBatch = new BatchAnalysisResult(base.totalPairCount(), base.candidateCount(),
                base.comparisonCount(), base.candidateReduction(), base.elapsedNanos(),
                base.parallel(), enriched);
        logger.info("Analyzed submission " + submissionId + " against "
                + selectedReferences.length + " reference(s) in "
                + (parallel ? "parallel" : "sequential") + " mode");
        return lastBatch;
    }

    public BatchAnalysisResult analyzeBatch() {
        return analyzeBatch(true);
    }

    public BatchAnalysisResult analyzeBatch(boolean parallel) {
        Document[] submissions = submissions();
        Document[] references = references();
        if (submissions.length == 0) throw new IllegalStateException("No submissions are loaded");
        if (references.length == 0) throw new IllegalStateException("No reference documents are loaded");
        BatchAnalysisResult base = batchAnalyzer.analyze("PD", submissions, references, parallel);
        AnalysisResult[] enriched = enrichAndStore(base.results());
        lastBatch = new BatchAnalysisResult(base.totalPairCount(), base.candidateCount(),
                base.comparisonCount(), base.candidateReduction(), base.elapsedNanos(),
                base.parallel(), enriched);
        logger.info("Batch analysis compared " + base.comparisonCount() + " candidate pairs in "
                + (parallel ? "parallel" : "sequential") + " mode");
        return lastBatch;
    }

    private AnalysisResult[] enrichAndStore(AnalysisResult[] base) {
        Document[] allDocuments = corpus.all();
        AnalysisResult[] enriched;
        if (settings.enableGraph) {
            SimilarityNetwork provisional = new SimilarityNetwork(allDocuments, base,
                    settings.graphEdgeThreshold);
            enriched = provisional.enrichGraphSignals(base, settings);
            similarityNetwork = new SimilarityNetwork(allDocuments, enriched,
                    settings.graphEdgeThreshold);
        } else {
            enriched = copy(base);
            similarityNetwork = new SimilarityNetwork(allDocuments, new AnalysisResult[0],
                    settings.graphEdgeThreshold);
        }
        resultIndex.clear();
        for (AnalysisResult result : enriched) resultIndex.put(result);
        rangeAnalytics = new RangeAnalytics(resultIndex.rankedDescending(), settings.reviewThreshold);
        assignments = new CaseAssignment[0];
        return enriched;
    }

    public CaseAssignment[] assignReviewers() {
        if (lastBatch == null) throw new IllegalStateException("Run an analysis before assigning cases");
        if (resultIndex.isEmpty()) {
            assignments = new CaseAssignment[0];
            logger.info("Reviewer assignment completed with no shortlisted cases");
            return new CaseAssignment[0];
        }
        Reviewer[] available = reviewers();
        if (available.length == 0) throw new IllegalStateException("No valid reviewers are configured");
        assignments = assignmentService.assign(resultIndex.rankedDescending(), available,
                settings.reviewThreshold);
        logger.info("Maximum-flow assignment routed " + assignments.length + " cases");
        return copy(assignments);
    }

    /** Returns the currently configured valid reviewers. */
    public Reviewer[] reviewers() {
        return ReviewerLoader.load(paths.reviewers, logger);
    }

    /**
     * Creates or replaces one manual assignment while enforcing the selected
     * reviewer's configured capacity across all committed assignments.
     */
    public CaseAssignment overrideReviewerAssignment(String caseId, String reviewerId) {
        if (lastBatch == null) throw new IllegalStateException("Run an analysis before assigning cases");
        AnalysisResult result = resultIndex.get(caseId);
        if (result == null) throw new IllegalArgumentException("Unknown case ID: " + caseId);
        Reviewer reviewer = findReviewer(reviewers(), reviewerId);
        if (reviewer == null) throw new IllegalArgumentException("Unknown reviewer ID: " + reviewerId);

        int existingIndex = assignmentIndex(caseId);
        int reviewerLoad = 0;
        for (int i = 0; i < assignments.length; i++) {
            if (i != existingIndex && assignments[i].reviewer().id().equals(reviewer.id())) {
                reviewerLoad++;
            }
        }
        if (reviewerLoad >= reviewer.capacity()) {
            throw new IllegalStateException("Reviewer " + reviewer.id()
                    + " has reached capacity " + reviewer.capacity());
        }

        CaseAssignment replacement = new CaseAssignment(result, reviewer);
        if (existingIndex >= 0) {
            assignments[existingIndex] = replacement;
        } else {
            CaseAssignment[] expanded = new CaseAssignment[assignments.length + 1];
            System.arraycopy(assignments, 0, expanded, 0, assignments.length);
            expanded[assignments.length] = replacement;
            assignments = expanded;
        }
        logger.info("Manual reviewer assignment routed " + caseId + " to " + reviewer.id());
        return replacement;
    }

    /** Removes and returns a committed assignment without removing the analysis case. */
    public CaseAssignment removeReviewerAssignment(String caseId) {
        int index = assignmentIndex(caseId);
        if (index < 0) return null;
        CaseAssignment removed = assignments[index];
        CaseAssignment[] reduced = new CaseAssignment[assignments.length - 1];
        if (index > 0) System.arraycopy(assignments, 0, reduced, 0, index);
        if (index + 1 < assignments.length) {
            System.arraycopy(assignments, index + 1, reduced, index,
                    assignments.length - index - 1);
        }
        assignments = reduced;
        logger.info("Removed reviewer assignment for " + caseId);
        return removed;
    }

    /** Exports committed reviewer assignments as UTF-8 CSV using atomic replacement. */
    public File exportAssignments(File destinationFile) throws IOException {
        if (destinationFile == null) {
            throw new IllegalArgumentException("destinationFile cannot be null");
        }
        File destination = destinationFile.getAbsoluteFile();
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create assignment export directory: " + parent);
        }
        if (destination.exists() && destination.isDirectory()) {
            destination = new File(destination, "reviewer-assignments.csv");
        }
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(temporary, StandardCharsets.UTF_8))) {
            writer.write("caseId,submissionId,referenceId,score,risk,reviewerId,reviewerName");
            writer.newLine();
            for (CaseAssignment assignment : assignments) {
                AnalysisResult result = assignment.result();
                writer.write(csv(result.caseId()));
                writer.write(',');
                writer.write(csv(result.submission().id()));
                writer.write(',');
                writer.write(csv(result.reference().id()));
                writer.write(',');
                writer.write(Double.toString(result.score().total()));
                writer.write(',');
                writer.write(csv(result.score().riskLabel()));
                writer.write(',');
                writer.write(csv(assignment.reviewer().id()));
                writer.write(',');
                writer.write(csv(assignment.reviewer().name()));
                writer.newLine();
            }
        }
        replace(temporary, destination);
        logger.info("Exported " + assignments.length + " reviewer assignment(s) to " + destination);
        return destination;
    }

    public ReportExportSummary exportReports() throws IOException {
        AnalysisResult[] results = resultIndex.rankedDescending();
        if (lastBatch == null) throw new IllegalStateException("Run an analysis before exporting reports");
        return new ReportWriter(paths.reports, logger, settings.reviewThreshold)
                .export(results, assignments, lastBatch);
    }

    /** Exports reports into an explicitly selected directory. */
    public ReportExportSummary exportReports(File outputDirectory) throws IOException {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory cannot be null");
        }
        if (lastBatch == null) throw new IllegalStateException("Run an analysis before exporting reports");
        ReportWriter selectedWriter = new ReportWriter(outputDirectory, logger,
                settings.reviewThreshold);
        return selectedWriter.export(resultIndex.rankedDescending(), assignments, lastBatch);
    }

    /** Formats one current case without writing a file. */
    public String previewReport(String caseId) {
        AnalysisResult result = resultIndex.get(caseId);
        if (result == null) throw new IllegalArgumentException("Unknown case ID: " + caseId);
        return new ReportWriter(paths.reports, logger, settings.reviewThreshold)
                .format(result, findAssignment(caseId));
    }

    public String runBenchmarks() throws IOException {
        File file = benchmarkService.runAndSave(submissions(), references(), paths.benchmarks);
        logger.info("Algorithm benchmarks written to " + file);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
        }
        return content.toString();
    }

    public Document[] submissions() { return filterDocuments(DocumentType.SUBMISSION); }
    public Document[] references() { return filterDocuments(DocumentType.REFERENCE); }

    private Document[] filterDocuments(DocumentType type) {
        Document[] all = corpus.all();
        int count = 0;
        for (Document document : all) if (document.type() == type) count++;
        Document[] result = new Document[count];
        int output = 0;
        for (Document document : all) if (document.type() == type) result[output++] = document;
        return result;
    }

    public AnalysisResult[] rankedResults() { return resultIndex.rankedDescending(); }
    public AnalysisResult[] rankedResults(boolean ascending) {
        return ascending ? resultIndex.rankedAscending() : resultIndex.rankedDescending();
    }

    public AnalysisResult[] reviewableResults() {
        return assignmentService.filterAndRank(resultIndex.rankedDescending(), settings.reviewThreshold);
    }

    public AnalysisResult[] rankedSubmissions() {
        return rankedSubmissions(false);
    }

    /** Returns each submission's strongest case, ordered through the custom risk index. */
    public AnalysisResult[] rankedSubmissions(boolean ascending) {
        AnalysisResult[] rankedCases = resultIndex.rankedDescending();
        AnalysisResult[] temporary = new AnalysisResult[rankedCases.length];
        HashSet<String> seen = new HashSet<>();
        int size = 0;
        for (AnalysisResult result : rankedCases) {
            if (seen.add(result.submission().id())) temporary[size++] = result;
        }
        AnalysisResult[] result = new AnalysisResult[size];
        System.arraycopy(temporary, 0, result, 0, size);
        if (ascending) reverse(result);
        return result;
    }

    /** Returns all cases for one submission in custom-index descending risk order. */
    public AnalysisResult[] resultsForSubmission(String submissionId) {
        Document submission = documentIndex.get(submissionId);
        if (submission == null || submission.type() != DocumentType.SUBMISSION) {
            return new AnalysisResult[0];
        }
        AnalysisResult[] ranked = resultIndex.rankedDescending();
        int count = 0;
        for (AnalysisResult result : ranked) {
            if (result.submission().id().equals(submissionId)) count++;
        }
        AnalysisResult[] matches = new AnalysisResult[count];
        int output = 0;
        for (AnalysisResult result : ranked) {
            if (result.submission().id().equals(submissionId)) matches[output++] = result;
        }
        return matches;
    }

    public RangeAnalytics submissionRangeAnalytics() {
        return new RangeAnalytics(rankedSubmissions(), settings.reviewThreshold);
    }

    public AnalysisResult findResult(String caseId) { return resultIndex.get(caseId); }
    public AnalysisResult highestRisk() { return resultIndex.highestRisk(); }
    public BatchAnalysisResult lastBatch() { return lastBatch; }
    public CaseAssignment[] assignments() { return copy(assignments); }
    public Settings settings() { return settings; }
    public ProjectPaths paths() { return paths; }
    public ActivityLogger logger() { return logger; }
    public RangeAnalytics rangeAnalytics() { return rangeAnalytics; }
    public PersistentDocumentIndex persistentDocumentIndex() { return persistentDocumentIndex; }
    public boolean hasAnalysis() { return lastBatch != null; }

    public SimilarityGroup[] similarityGroups() {
        return similarityNetwork == null ? new SimilarityGroup[0] : similarityNetwork.groups();
    }

    public CopyingPath copyingPath(String firstDocumentId, String secondDocumentId) {
        if (similarityNetwork == null) {
            return new CopyingPath(new String[0], new double[0], Double.POSITIVE_INFINITY);
        }
        return similarityNetwork.shortestRelationshipPath(firstDocumentId, secondDocumentId);
    }

    public RelationshipEdge[] compactRelationships() {
        return similarityNetwork == null ? new RelationshipEdge[0]
                : similarityNetwork.compactRelationships();
    }

    public RelationshipEdge[] relationships() {
        return similarityNetwork == null ? new RelationshipEdge[0]
                : similarityNetwork.relationships();
    }

    public String[] breadthFirstRelationships(String startDocumentId) {
        return similarityNetwork == null ? new String[0]
                : similarityNetwork.breadthFirst(startDocumentId);
    }

    public String[] depthFirstRelationships(String startDocumentId) {
        return similarityNetwork == null ? new String[0]
                : similarityNetwork.depthFirst(startDocumentId);
    }

    public String invariantSummary() {
        var documents = documentIndex.validationSummary();
        var results = resultIndex.validationSummary();
        var ranges = rangeAnalytics.validationSummary();
        boolean persistentValid = persistentIndexCoversLoadedCorpus();
        return "Document indexes: " + documents.valid() + " (" + documents.indexedDocuments()
                + ") | Result indexes: " + results.valid() + " (" + results.indexedResults()
                + ") | Range structures: " + ranges.valid() + " (" + ranges.valuesChecked()
                + ") | Persistent index: " + persistentValid + " ("
                + persistentDocumentIndex.size() + ")";
    }

    public void shutdown() {
        logger.info("Application exited safely");
    }

    private void invalidateAnalysisState() {
        lastBatch = null;
        similarityNetwork = null;
        resultIndex.clear();
        rangeAnalytics = new RangeAnalytics(new double[0], settings.reviewThreshold);
        assignments = new CaseAssignment[0];
        logger.info("Imported corpus changed; derived analysis state was invalidated");
    }

    private ImportSummary importSource(File source, DocumentType type) {
        boolean fileSource = source != null && (source.isFile()
                || source.getName().toLowerCase().endsWith(".txt"));
        ImportSummary summary = fileSource
                ? importer.importFile(source, type, corpus)
                : importer.importDirectory(source, type, corpus);
        indexImportedDocuments(summary);
        return summary;
    }

    private void indexImportedDocuments(ImportSummary summary) {
        for (Document document : summary.documents()) {
            if (!documentIndex.add(document)) {
                throw new IllegalStateException("Imported document was not added to its custom index: "
                        + document.id());
            }
            try {
                persistentDocumentIndex.put(document);
            } catch (RuntimeException exception) {
                logger.error("Document remains loaded, but its persistent index record failed: "
                        + document.id(), exception);
            }
        }
    }

    private void requireDocumentType(DocumentType type) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
    }

    private Reviewer findReviewer(Reviewer[] available, String reviewerId) {
        if (reviewerId == null || reviewerId.isBlank()) return null;
        for (Reviewer reviewer : available) {
            if (reviewer.id().equals(reviewerId)) return reviewer;
        }
        return null;
    }

    private int assignmentIndex(String caseId) {
        if (caseId == null) return -1;
        for (int i = 0; i < assignments.length; i++) {
            if (assignments[i].result().caseId().equals(caseId)) return i;
        }
        return -1;
    }

    private CaseAssignment findAssignment(String caseId) {
        int index = assignmentIndex(caseId);
        return index < 0 ? null : assignments[index];
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        boolean quote = false;
        for (int i = 0; i < safe.length(); i++) {
            char current = safe.charAt(i);
            if (current == ',' || current == '"' || current == '\n' || current == '\r') {
                quote = true;
                break;
            }
        }
        if (!quote) return safe;
        StringBuilder escaped = new StringBuilder(safe.length() + 2);
        escaped.append('"');
        for (int i = 0; i < safe.length(); i++) {
            char current = safe.charAt(i);
            if (current == '"') escaped.append('"');
            escaped.append(current);
        }
        return escaped.append('"').toString();
    }

    private void replace(File temporary, File destination) throws IOException {
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void reverse(AnalysisResult[] values) {
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            AnalysisResult temporary = values[left];
            values[left] = values[right];
            values[right] = temporary;
        }
    }

    private boolean persistentIndexCoversLoadedCorpus() {
        if (!persistentDocumentIndex.validateInvariant()
                || !persistentDocumentIndex.backingFilesAccessible()) return false;
        for (Document document : corpus.all()) {
            PersistentDocumentIndex.Record record = persistentDocumentIndex.get(document.id());
            if (record == null || record.type() != document.type()
                    || !record.sourcePath().equals(document.filePath())) return false;
        }
        return true;
    }

    private void registerSource(File directory, DocumentType type) {
        if (directory == null) return;
        File absolute = directory.getAbsoluteFile();
        if (type == DocumentType.SUBMISSION) {
            if (containsSource(submissionSources, submissionSourceCount, absolute)) return;
            if (submissionSourceCount == submissionSources.length) submissionSources = grow(submissionSources);
            submissionSources[submissionSourceCount++] = absolute;
        } else {
            if (containsSource(referenceSources, referenceSourceCount, absolute)) return;
            if (referenceSourceCount == referenceSources.length) referenceSources = grow(referenceSources);
            referenceSources[referenceSourceCount++] = absolute;
        }
    }

    private boolean containsSource(File[] sources, int size, File target) {
        String path = target.getAbsolutePath();
        for (int i = 0; i < size; i++) {
            if (sources[i].getAbsolutePath().equalsIgnoreCase(path)) return true;
        }
        return false;
    }

    private File[] grow(File[] source) {
        File[] result = new File[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private File[] sourceCopy(File[] source, int size) {
        File[] result = new File[size];
        System.arraycopy(source, 0, result, 0, size);
        return result;
    }

    private CaseAssignment[] copy(CaseAssignment[] source) {
        CaseAssignment[] result = new CaseAssignment[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private AnalysisResult[] copy(AnalysisResult[] source) {
        AnalysisResult[] result = new AnalysisResult[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
