package edu.academic.integrity.tests;

import edu.academic.integrity.algorithms.compression.HuffmanCodec;
import edu.academic.integrity.app.AcademicIntegritySystem;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.core.BatchAnalyzer;
import edu.academic.integrity.core.DocumentAnalyzer;
import edu.academic.integrity.io.ActivityLogger;
import edu.academic.integrity.io.CorpusImporter;
import edu.academic.integrity.io.CorpusStore;
import edu.academic.integrity.io.DocumentFileParser;
import edu.academic.integrity.io.ImportSummary;
import edu.academic.integrity.io.ReviewerLoader;
import edu.academic.integrity.index.PersistentDocumentIndex;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.model.MatchType;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.Reviewer;
import edu.academic.integrity.model.ScoreBreakdown;
import edu.academic.integrity.report.ReportExportSummary;
import edu.academic.integrity.report.ReportWriter;
import edu.academic.integrity.review.ReviewerAssignmentService;
import edu.academic.integrity.ui.DesktopUiSelfTests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
// Files.createTempDirectory supplies an isolated test workspace; no collection API is used.
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Dependency-free integrated test runner for structures, algorithms, I/O, and workflow. */
public final class AllTests {
    private static int integrationAssertions;

    private AllTests() { }

    public static void main(String[] args) throws Exception {
        long started = System.nanoTime();
        int structureAssertions = StructureSelfTests.runAll();
        int textAssertions = TextAlgorithmSelfTests.runAll();
        int advancedAssertions = AdvancedAlgorithmSelfTests.runAll();
        int serviceAssertions = ServiceControllerSelfTests.runAll();
        int desktopAssertions = DesktopUiSelfTests.runAll();
        testFileValidationAndRecovery();
        testSettingsValidation();
        testEvidenceLocationMapping();
        testReviewerCapacityAndReporting();
        testSequentialParallelConsistency();
        testZeroCandidateWorkflow();
        testEndToEndSample();
        int total = structureAssertions + textAssertions + advancedAssertions + serviceAssertions
                + desktopAssertions + integrationAssertions;
        System.out.println("Structure assertions: " + structureAssertions);
        System.out.println("Text assertions: " + textAssertions);
        System.out.println("Advanced assertions: " + advancedAssertions);
        System.out.println("Service/controller assertions: " + serviceAssertions);
        System.out.println("Desktop UI assertions: " + desktopAssertions);
        System.out.println("Integration assertions: " + integrationAssertions);
        System.out.printf("ALL_TESTS_PASSED: %d assertions in %.3f ms%n", total,
                (System.nanoTime() - started) / 1_000_000.0);
    }

    private static void testFileValidationAndRecovery() throws Exception {
        File root = Files.createTempDirectory("academic-integrity-tests-").toFile();
        try {
            File importDirectory = new File(root, "input");
            File logs = new File(root, "logs");
            File quarantine = new File(root, "quarantine");
            check(importDirectory.mkdirs(), "temporary input directory created");
            check(logs.mkdirs(), "temporary log directory created");

            writeText(new File(importDirectory, "01-valid.txt"),
                    "\uFEFFID: DOC-1\nTITLE: Valid\nAUTHOR: Tester\n\n  A complete valid document body.\n");
            writeText(new File(importDirectory, "02-duplicate.txt"),
                    "ID: DOC-1\nTITLE: Duplicate\n\nAnother nonempty body.");
            writeText(new File(importDirectory, "empty.txt"), "");
            writeText(new File(importDirectory, "punctuation-only.txt"), "... --- !!!");
            writeText(new File(importDirectory, "unsupported.pdf"), "not actually a PDF");
            try (FileOutputStream output = new FileOutputStream(new File(importDirectory, "corrupt.txt"))) {
                output.write(new byte[]{(byte) 0xC3, 0x28});
            }

            ActivityLogger logger = new ActivityLogger(logs);
            CorpusImporter importer = new CorpusImporter(new DocumentFileParser(10_000),
                    logger, quarantine);
            CorpusStore store = new CorpusStore();
            ImportSummary summary = importer.importDirectory(importDirectory,
                    DocumentType.SUBMISSION, store);
            check(summary.importedCount() == 1, "valid file survives mixed invalid batch");
            check(summary.errorCount() == 5,
                    "empty, non-searchable, duplicate, corrupt, and unsupported files rejected");
            check(store.size() == 1 && store.find("DOC-1") != null, "duplicate ID isolated");
            check(store.find("DOC-1").locate(2).line() == 5
                            && store.find("DOC-1").locate(2).column() == 3,
                    "metadata line and leading-column offsets are preserved");
            File[] quarantined = quarantine.listFiles();
            check(quarantined != null && quarantined.length == 5, "failed files copied to quarantine");
            ImportSummary repeated = importer.importDirectory(importDirectory,
                    DocumentType.SUBMISSION, store);
            check(repeated.errorCount() == 6,
                    "re-import rejects every duplicate or invalid source file");
            quarantined = quarantine.listFiles();
            check(quarantined != null && quarantined.length == 11,
                    "repeated failures receive unique quarantine copies without overwrite");
            check(logger.errorFile().isFile() && logger.errorFile().length() > 0,
                    "file errors persisted to log");

            File reviewerFile = new File(root, "reviewers.txt");
            writeText(reviewerFile, "R-1,First Reviewer,1\n"
                    + "R-1,Duplicate Reviewer,5\n"
                    + "R-2,Second Reviewer,2\n");
            Reviewer[] reviewers = ReviewerLoader.load(reviewerFile, logger);
            check(reviewers.length == 2, "duplicate reviewer IDs are skipped");
            check(reviewers[0].id().equals("R-1") && reviewers[0].capacity() == 1
                            && reviewers[1].id().equals("R-2"),
                    "first reviewer identity and declared capacity are retained");

            File supplementaryUnicode = new File(root, "supplementary-unicode.txt");
            writeText(supplementaryUnicode, "ID: UNICODE\n\n\uD801\uDC00");
            Document parsedUnicode = new DocumentFileParser(10_000)
                    .parse(supplementaryUnicode, DocumentType.SUBMISSION);
            check(parsedUnicode.content().codePointAt(0) == 0x10400,
                    "supplementary-plane Unicode letters are valid searchable UTF-8 content");

            File persistentDirectory = new File(root, "persistent-index");
            check(persistentDirectory.mkdirs(), "persistent-index test directory created");
            File corruptSnapshot = new File(persistentDirectory, "document-index.tsv");
            try (FileOutputStream output = new FileOutputStream(corruptSnapshot)) {
                output.write(new byte[]{(byte) 0xC3, 0x28});
            }
            PersistentDocumentIndex recovered = new PersistentDocumentIndex(corruptSnapshot, logger);
            check(recovered.size() == 0, "malformed persistent index recovers as an empty catalog");
            recovered.put(parsedUnicode);
            check(recovered.get("UNICODE") != null && recovered.validateInvariant()
                            && recovered.backingFilesAccessible()
                            && corruptSnapshot.isFile(),
                    "persistent index accepts writes after isolating malformed input");
            File[] isolatedSnapshots = persistentDirectory.listFiles(
                    (directory, name) -> name.contains("invalid-copy"));
            check(isolatedSnapshots != null && isolatedSnapshots.length == 1,
                    "malformed persistent snapshot is preserved as a recovery copy");

            File interruptedSnapshot = new File(persistentDirectory, "interrupted-index.tsv");
            PersistentDocumentIndex interrupted = new PersistentDocumentIndex(
                    interruptedSnapshot, logger);
            interrupted.put(parsedUnicode);
            File pendingSnapshot = new File(persistentDirectory, "interrupted-index.tsv.tmp");
            check(interruptedSnapshot.renameTo(pendingSnapshot),
                    "complete snapshot staged as an interrupted temporary write");
            PersistentDocumentIndex resumed = new PersistentDocumentIndex(
                    interruptedSnapshot, logger);
            check(resumed.get("UNICODE") != null && interruptedSnapshot.isFile()
                            && !pendingSnapshot.exists() && resumed.validateInvariant(),
                    "valid interrupted temporary snapshot is recovered atomically");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testSequentialParallelConsistency() {
        Settings settings = new Settings();
        settings.candidateThreshold = 0.0;
        settings.workerCount = 2;
        Document[] submissions = {
                document("S1", "balanced trees use rotations and preserve logarithmic search height", DocumentType.SUBMISSION),
                document("S2", "water samples were collected beside the coastal marsh at sunrise", DocumentType.SUBMISSION)
        };
        Document[] references = {
                document("R1", "balanced trees use rotations and preserve logarithmic search height", DocumentType.REFERENCE),
                document("R2", "pattern matching locates phrases inside a reference corpus", DocumentType.REFERENCE)
        };
        BatchAnalyzer analyzer = new BatchAnalyzer(settings, new String[0]);
        BatchAnalysisResult sequential = analyzer.analyzeSequential("CONSISTENCY", submissions, references);
        BatchAnalysisResult parallel = analyzer.analyzeParallel("CONSISTENCY", submissions, references);
        check(sequential.totalPairCount() == 4 && sequential.comparisonCount() == 4,
                "all zero-threshold pairs compared");
        AnalysisResult[] left = sequential.results();
        AnalysisResult[] right = parallel.results();
        check(left.length == right.length, "parallel result count");
        for (int i = 0; i < left.length; i++) {
            check(left[i].caseId().equals(right[i].caseId()), "deterministic case ordering " + i);
            check(Math.abs(left[i].score().total() - right[i].score().total()) < 1e-12,
                    "parallel/sequential score equality " + i);
        }
    }

    private static void testSettingsValidation() {
        Settings notANumber = new Settings();
        notANumber.exactWeight = Double.NaN;
        expectIllegalArgument(notANumber::validate, "NaN setting weight rejected");
        Settings infinite = new Settings();
        infinite.graphWeight = Double.POSITIVE_INFINITY;
        expectIllegalArgument(infinite::validate, "infinite setting weight rejected");
        expectIllegalArgument(() -> new ScoreBreakdown(0.5, 0.5, 0.5, 0.5,
                1.0, 1.0, Double.NaN, 1.0), "non-finite score weight rejected");
    }

    private static void testEvidenceLocationMapping() {
        Settings settings = new Settings();
        settings.wordShingleSize = 4;
        settings.minExactPhraseCharacters = 10;
        String stoppedContent = "the alpha a beta the gamma delta closes the passage";
        Document stoppedSubmission = document("S-STOP", stoppedContent, DocumentType.SUBMISSION);
        Document stoppedReference = document("R-STOP", stoppedContent, DocumentType.REFERENCE);
        AnalysisResult stopped = new DocumentAnalyzer(settings, new String[]{"the", "a"})
                .analyze("LOC-STOP", stoppedSubmission, stoppedReference);
        PassageMatch shingle = findEvidence(stopped, MatchType.SHINGLE);
        check(shingle != null, "stopword-filtered shingle evidence exists");
        check(shingle.submissionStart() == stoppedContent.indexOf("alpha"),
                "filtered shingle maps to its original token span");

        Settings repeatedSettings = new Settings();
        repeatedSettings.removeStopwords = false;
        repeatedSettings.wordShingleSize = 4;
        repeatedSettings.minExactPhraseCharacters = 10;
        String repeatedContent = "target noise target noise extra extra target phrase appears here";
        Document repeatedSubmission = document("S-REPEAT", repeatedContent, DocumentType.SUBMISSION);
        Document repeatedReference = document("R-REPEAT", "target phrase appears here",
                DocumentType.REFERENCE);
        AnalysisResult repeated = new DocumentAnalyzer(repeatedSettings, new String[0])
                .analyze("LOC-REPEAT", repeatedSubmission, repeatedReference);
        PassageMatch fuzzy = findEvidence(repeated, MatchType.FUZZY);
        check(fuzzy != null, "repeated-token fuzzy evidence exists");
        check(fuzzy.submissionStart() == repeatedContent.lastIndexOf("target"),
                "fuzzy coordinates select the aligned repeated-token occurrence");

        String supplementary = "\uD801\uDC00 sample";
        Document unicode = document("S-UNICODE", supplementary, DocumentType.SUBMISSION);
        new DocumentAnalyzer(repeatedSettings, new String[0]).prepare(unicode);
        check(unicode.normalizedText().codePointAt(0) == 0x10428,
                "text preparation normalizes supplementary-plane letters by code point");
        check(unicode.originalOffsetForNormalized(1) == 1,
                "supplementary-plane normalized characters retain UTF-16 source offsets");

        Document mixedNewlines = document("S-LINES", "first\rsecond\r\nthird\nfourth",
                DocumentType.SUBMISSION);
        check(mixedNewlines.locate(mixedNewlines.content().indexOf("second")).line() == 2
                        && mixedNewlines.locate(mixedNewlines.content().indexOf("third")).line() == 3
                        && mixedNewlines.locate(mixedNewlines.content().indexOf("fourth")).line() == 4,
                "source locations handle CR, CRLF, and LF line endings");
    }

    private static void testReviewerCapacityAndReporting() throws Exception {
        File root = Files.createTempDirectory("review-capacity-tests-").toFile();
        try {
            ActivityLogger logger = new ActivityLogger(new File(root, "logs"));
            Document reference = document("R-CAP", "shared reference passage", DocumentType.REFERENCE);
            AnalysisResult first = new AnalysisResult("CAP-1",
                    document("S-CAP-1", "shared reference passage", DocumentType.SUBMISSION),
                    reference, new ScoreBreakdown(0.9, 0.8, 0.7, 0.0,
                    1.0, 1.0, 1.0, 1.0), new PassageMatch[0], 1L);
            AnalysisResult second = new AnalysisResult("CAP-2",
                    document("S-CAP-2", "shared reference passage", DocumentType.SUBMISSION),
                    reference, new ScoreBreakdown(0.8, 0.7, 0.6, 0.0,
                    1.0, 1.0, 1.0, 1.0), new PassageMatch[0], 1L);
            AnalysisResult[] results = {first, second};
            Reviewer[] reviewers = {new Reviewer("R-ONLY", "Only Reviewer", 1)};
            CaseAssignment[] assignments = new ReviewerAssignmentService()
                    .assign(results, reviewers, 0.30);
            check(assignments.length == 1, "reviewer capacity limits assignments");

            ReportExportSummary exported = new ReportWriter(new File(root, "reports"), logger, 0.30)
                    .export(results, assignments);
            String summary = readText(new File(exported.summaryPath()));
            check(summary.contains("AWAITING_ASSIGNMENT"),
                    "report summary exposes reviewable cases awaiting capacity");
            File runDirectory = new File(exported.summaryPath()).getParentFile();
            File waitingReport = new File(runDirectory,
                    (assignments[0].result().caseId().equals("CAP-1") ? "CAP-2" : "CAP-1") + ".txt");
            check(readText(waitingReport).contains("AWAITING ASSIGNMENT"),
                    "case report distinguishes capacity shortage from below-threshold status");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testEndToEndSample() throws Exception {
        File root = new File(System.getProperty("user.dir"));
        AcademicIntegritySystem system = new AcademicIntegritySystem(root);
        try {
            ImportSummary submissions = system.importStandardSubmissions();
            ImportSummary references = system.importStandardReferences();
            check(submissions.importedCount() >= 4 && submissions.errorCount() == 0,
                    "sample submissions imported");
            check(references.importedCount() >= 3 && references.errorCount() == 0,
                    "sample references imported");
            check(system.validateStandardCorpus().invalidFiles() == 0, "sample corpus validates");

            BatchAnalysisResult batch = system.analyzeBatch();
            check(batch.totalPairCount() >= 12, "sample pair count");
            check(batch.comparisonCount() >= 3, "MinHash retained exact and modified-copy candidates");
            check(system.highestRisk() != null, "ranking produced a highest-risk case");
            check(system.highestRisk().evidence().length > 0, "reportable evidence recovered");
            check(system.highestRisk().score().exactMatch() > 0.5, "known copied fixture detected");
            AnalysisResult modified = findPair(system.rankedResults(), "SUB-BOB", "REF-MATCHING");
            check(modified != null && modified.score().fuzzyAlignment() > 0.70,
                    "lightly modified fixture detected by fuzzy alignment");
            check(system.highestRisk().submission().locate(
                    system.highestRisk().evidence()[0].submissionStart()).line() >= 5,
                    "evidence location retains metadata line offset");
            check(system.similarityGroups().length > 0, "similarity component discovered");
            check(system.breadthFirstRelationships("SUB-ALICE").length >= 2
                            && system.depthFirstRelationships("SUB-ALICE").length >= 2,
                    "menu graph exposes BFS and DFS reachability");
            check(system.breadthFirstRelationships("SUB-CARA").length == 1,
                    "documents with no shortlisted relationship remain isolated graph vertices");
            check(system.assignReviewers().length > 0, "maximum-flow reviewer assignment produced");
            ReportExportSummary exported = system.exportReports();
            check(new File(exported.summaryPath()).isFile(), "batch summary exported");

            File firstRunDirectory = new File(exported.summaryPath()).getParentFile();
            File[] compressed = firstRunDirectory.listFiles((directory, name) -> name.endsWith(".huff"));
            check(compressed != null && compressed.length > 0, "Huffman reports exported");
            byte[] bytes = Files.readAllBytes(compressed[0].toPath());
            String decoded = new HuffmanCodec().decompressText(bytes);
            check(decoded.contains("EXPLAINABLE SCORE COMPONENTS"), "Huffman report round-trip");
            check(system.invariantSummary().contains("true"), "project index invariants hold");

            system.analyzeOneSubmission("SUB-ALICE");
            check(system.rankedResults().length == system.references().length,
                    "targeted analysis retains pair-level cases");
            check(system.rankedSubmissions().length == 1,
                    "submission ranking aggregates multiple reference cases");
            ReportExportSummary secondExport = system.exportReports();
            check(!new File(secondExport.summaryPath()).getParentFile().equals(firstRunDirectory),
                    "each export uses an isolated run directory");

        } finally {
            system.shutdown();
        }
    }

    private static void testZeroCandidateWorkflow() throws Exception {
        File root = Files.createTempDirectory("zero-candidate-project-").toFile();
        AcademicIntegritySystem system = null;
        try {
            system = new AcademicIntegritySystem(root);
            writeText(new File(system.paths().submissions, "unrelated-submission.txt"),
                    "ID: S-ZERO\n\norchard sunlight apples branches harvest baskets");
            writeText(new File(system.paths().references, "unrelated-reference.txt"),
                    "ID: R-ZERO\n\nquantum circuits photons electrons matrices tensors");
            check(system.importStandardSubmissions().importedCount() == 1,
                    "zero-candidate submission imported");
            check(system.importStandardReferences().importedCount() == 1,
                    "zero-candidate reference imported");
            BatchAnalysisResult batch = system.analyzeBatch();
            check(batch.totalPairCount() == 1 && batch.comparisonCount() == 0,
                    "valid batch may complete with no MinHash candidates");
            check(system.hasAnalysis() && system.rankedResults().length == 0,
                    "zero-candidate completion remains an analysis state");
            check(system.assignReviewers().length == 0,
                    "zero-candidate assignment completes without reviewer configuration");
            ReportExportSummary exported = system.exportReports();
            File summary = new File(exported.summaryPath());
            check(summary.isFile() && readText(summary).contains("Verified comparisons: 0")
                            && readText(summary).contains("Cases emitted: 0"),
                    "zero-candidate analysis exports a valid summary");

            check(system.paths().documentIndexSnapshot.isFile()
                            && system.persistentDocumentIndex().size() == 2
                            && system.persistentDocumentIndex().validateInvariant(),
                    "B-tree/B+ persistent document catalog is written and valid");
            AcademicIntegritySystem reloaded = new AcademicIntegritySystem(root);
            try {
                check(reloaded.persistentDocumentIndex().size() == 2
                                && reloaded.persistentDocumentIndex().get("S-ZERO") != null,
                        "persistent document catalog reloads across application instances");
            } finally {
                reloaded.shutdown();
            }

            File customDirectory = new File(root, "custom-submission-source");
            check(customDirectory.mkdirs(), "custom source directory created");
            writeText(new File(customDirectory, "extra.txt"),
                    "ID: SUB-EXTRA\nTITLE: Extra\n\nA newly imported valid submission body.");
            check(system.importDocuments(customDirectory, DocumentType.SUBMISSION).importedCount() == 1,
                    "custom source imports successfully");
            check(!system.hasAnalysis() && system.rankedResults().length == 0,
                    "corpus changes invalidate stale analysis state");
            check(system.validateImportedSources().validFiles() >= 3,
                    "validation follows registered custom and standard sources");
            check(system.persistentDocumentIndex().get("SUB-EXTRA") != null,
                    "custom imported source is added to the persistent index");
        } finally {
            if (system != null) system.shutdown();
            deleteRecursively(root);
        }
    }

    private static Document document(String id, String content, DocumentType type) {
        return new Document(id, id, "Test", id + ".txt", content, type);
    }

    private static AnalysisResult findPair(AnalysisResult[] results, String submissionId,
                                           String referenceId) {
        for (AnalysisResult result : results) {
            if (result.submission().id().equals(submissionId)
                    && result.reference().id().equals(referenceId)) return result;
        }
        return null;
    }

    private static PassageMatch findEvidence(AnalysisResult result, MatchType type) {
        for (PassageMatch evidence : result.evidence()) if (evidence.type() == type) return evidence;
        return null;
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        boolean thrown = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, message);
    }

    private static void writeText(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file,
                StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    private static String readText(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!target.delete()) target.deleteOnExit();
    }

    private static void check(boolean condition, String message) {
        integrationAssertions++;
        if (!condition) throw new AssertionError(message);
    }
}
