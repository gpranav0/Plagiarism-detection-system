package edu.academic.integrity.core;

import edu.academic.integrity.algorithms.text.MinHash;
import edu.academic.integrity.algorithms.text.ShingleGenerator;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.Document;
import java.util.concurrent.ExecutionException; // Required only to translate worker failures at the concurrency boundary.
import java.util.concurrent.ExecutorService; // Required only to bound parallel document comparisons.
import java.util.concurrent.Executors; // Required only to construct the fixed-size worker pool.
import java.util.concurrent.Future; // Required only to retain deterministic ordinal slots for worker results.

/** Bounded, deterministic batch adapter around the single-pair analyzer. */
public final class BatchAnalyzer {
    private static final int MINHASH_SIGNATURE_SIZE = 128;
    private static final String DEFAULT_CASE_PREFIX = "BATCH";

    private final Settings settings;
    private final DocumentAnalyzer documentAnalyzer;

    public BatchAnalyzer(Settings settings, String[] stopwords) {
        this(new DocumentAnalyzer(requireSettings(settings), stopwords), settings);
    }

    public BatchAnalyzer(DocumentAnalyzer documentAnalyzer, Settings settings) {
        this.settings = requireSettings(settings);
        if (documentAnalyzer == null) {
            throw new IllegalArgumentException("documentAnalyzer cannot be null");
        }
        this.documentAnalyzer = documentAnalyzer;
    }

    /** Runs with the bounded parallel mode configured by {@link Settings#workerCount}. */
    public BatchAnalysisResult analyze(Document[] submissions, Document[] references) {
        return analyzeParallel(DEFAULT_CASE_PREFIX, submissions, references);
    }

    public BatchAnalysisResult analyzeParallel(Document[] submissions, Document[] references) {
        return analyzeParallel(DEFAULT_CASE_PREFIX, submissions, references);
    }

    public BatchAnalysisResult analyzeSequential(Document[] submissions, Document[] references) {
        return analyzeSequential(DEFAULT_CASE_PREFIX, submissions, references);
    }

    public BatchAnalysisResult analyze(Document[] submissions, Document[] references,
            boolean parallel) {
        return analyze(DEFAULT_CASE_PREFIX, submissions, references, parallel);
    }

    public BatchAnalysisResult analyzeParallel(String casePrefix,
            Document[] submissions, Document[] references) {
        return analyze(casePrefix, submissions, references, true);
    }

    public BatchAnalysisResult analyzeSequential(String casePrefix,
            Document[] submissions, Document[] references) {
        return analyze(casePrefix, submissions, references, false);
    }

    /**
     * Prepares all documents, performs deterministic MinHash selection, and
     * compares candidates either sequentially or through a fixed worker pool.
     */
    public BatchAnalysisResult analyze(String casePrefix, Document[] submissions,
            Document[] references, boolean parallel) {
        return analyzeInternal(casePrefix, submissions, references, parallel, true);
    }

    /** Compares every selected pair without MinHash candidate reduction. */
    public BatchAnalysisResult analyzeAll(Document[] submissions, Document[] references,
            boolean parallel) {
        return analyzeAll(DEFAULT_CASE_PREFIX, submissions, references, parallel);
    }

    /** Compares every selected pair without MinHash candidate reduction. */
    public BatchAnalysisResult analyzeAll(String casePrefix, Document[] submissions,
            Document[] references, boolean parallel) {
        return analyzeInternal(casePrefix, submissions, references, parallel, false);
    }

    private BatchAnalysisResult analyzeInternal(String casePrefix, Document[] submissions,
            Document[] references, boolean parallel, boolean shortlistCandidates) {
        long started = System.nanoTime();
        checkInterrupted();
        settings.validate();
        validateCasePrefix(casePrefix);
        validateDocuments(submissions, "submissions");
        validateDocuments(references, "references");

        long totalPairCount = (long) submissions.length * references.length;
        if (totalPairCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "batch contains too many pairs for an array-backed candidate plan");
        }

        prepareAll(submissions);
        prepareAll(references);
        checkInterrupted();
        CandidatePlan plan = shortlistCandidates
                ? createCandidatePlan(submissions, references, (int) totalPairCount)
                : allPairsPlan((int) totalPairCount);
        checkInterrupted();
        AnalysisResult[] results = parallel
                ? compareParallel(casePrefix, submissions, references, plan)
                : compareSequential(casePrefix, submissions, references, plan);
        double reduction = totalPairCount == 0L ? 0.0
                : (double) (totalPairCount - plan.candidateCount) / totalPairCount;
        return new BatchAnalysisResult(totalPairCount, plan.candidateCount, results.length,
                reduction, System.nanoTime() - started, parallel, results);
    }

    private void prepareAll(Document[] documents) {
        for (int index = 0; index < documents.length; index++) {
            checkInterrupted();
            documentAnalyzer.prepare(documents[index]);
        }
    }

    private CandidatePlan createCandidatePlan(Document[] submissions, Document[] references,
            int totalPairCount) {
        if (!settings.enableShingle) return allPairsPlan(totalPairCount);
        checkInterrupted();
        String[][] submissionShingles = shingles(submissions);
        String[][] referenceShingles = shingles(references);
        MinHash minHash = new MinHash(MINHASH_SIGNATURE_SIZE);
        long[][] submissionSignatures = signatures(minHash, submissionShingles);
        long[][] referenceSignatures = signatures(minHash, referenceShingles);

        int[] candidateOrdinals = new int[initialCandidateCapacity(totalPairCount)];
        int candidateCount = 0;
        int ordinal = 0;
        for (int submission = 0; submission < submissions.length; submission++) {
            for (int reference = 0; reference < references.length; reference++) {
                checkInterrupted();
                boolean shortDocument = submissionShingles[submission].length == 0
                        || referenceShingles[reference].length == 0;
                boolean accepted = shortDocument || MinHash.signatureSimilarity(
                        submissionSignatures[submission], referenceSignatures[reference])
                        >= settings.candidateThreshold;
                if (accepted) {
                    if (candidateCount == candidateOrdinals.length) {
                        candidateOrdinals = grow(candidateOrdinals, totalPairCount);
                    }
                    candidateOrdinals[candidateCount++] = ordinal;
                }
                ordinal++;
            }
        }
        int[] exactOrdinals = new int[candidateCount];
        System.arraycopy(candidateOrdinals, 0, exactOrdinals, 0, candidateCount);
        return new CandidatePlan(exactOrdinals, candidateCount);
    }

    private CandidatePlan allPairsPlan(int totalPairCount) {
        int[] ordinals = new int[totalPairCount];
        for (int ordinal = 0; ordinal < totalPairCount; ordinal++) {
            checkInterrupted();
            ordinals[ordinal] = ordinal;
        }
        return new CandidatePlan(ordinals, totalPairCount);
    }

    private String[][] shingles(Document[] documents) {
        String[][] result = new String[documents.length][];
        for (int index = 0; index < documents.length; index++) {
            checkInterrupted();
            result[index] = ShingleGenerator.wordShingles(
                    documents[index].tokens(), settings.wordShingleSize);
        }
        return result;
    }

    private long[][] signatures(MinHash minHash, String[][] shingles) {
        long[][] result = new long[shingles.length][];
        for (int index = 0; index < shingles.length; index++) {
            checkInterrupted();
            result[index] = minHash.signature(shingles[index]);
        }
        return result;
    }

    private AnalysisResult[] compareSequential(String casePrefix, Document[] submissions,
            Document[] references, CandidatePlan plan) {
        AnalysisResult[] results = new AnalysisResult[plan.candidateCount];
        for (int candidate = 0; candidate < plan.candidateCount; candidate++) {
            checkInterrupted();
            results[candidate] = compareOrdinal(casePrefix, submissions, references,
                    plan.ordinals[candidate]);
        }
        return results;
    }

    private AnalysisResult[] compareParallel(String casePrefix, Document[] submissions,
            Document[] references, CandidatePlan plan) {
        if (plan.candidateCount == 0) {
            return new AnalysisResult[0];
        }
        int workerCount = settings.workerCount < plan.candidateCount
                ? settings.workerCount : plan.candidateCount;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        Future<?>[] futures = new Future<?>[plan.candidateCount];
        boolean completed = false;
        try {
            for (int candidate = 0; candidate < plan.candidateCount; candidate++) {
                checkInterrupted();
                final int ordinal = plan.ordinals[candidate];
                futures[candidate] = executor.submit(
                        () -> compareOrdinal(casePrefix, submissions, references, ordinal));
            }
            AnalysisResult[] results = new AnalysisResult[plan.candidateCount];
            for (int candidate = 0; candidate < futures.length; candidate++) {
                checkInterrupted();
                results[candidate] = (AnalysisResult) futures[candidate].get();
            }
            completed = true;
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(futures);
            throw new IllegalStateException("batch analysis was interrupted", exception);
        } catch (ExecutionException exception) {
            cancel(futures);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("a batch comparison failed", cause);
        } finally {
            if (completed) {
                executor.shutdown();
            } else {
                executor.shutdownNow();
            }
        }
    }

    private AnalysisResult compareOrdinal(String casePrefix, Document[] submissions,
            Document[] references, int ordinal) {
        checkInterrupted();
        int submissionIndex = ordinal / references.length;
        int referenceIndex = ordinal % references.length;
        return documentAnalyzer.analyze(caseId(casePrefix, ordinal,
                submissions[submissionIndex], references[referenceIndex]),
                submissions[submissionIndex], references[referenceIndex]);
    }

    private String caseId(String prefix, int ordinal, Document submission, Document reference) {
        return prefix + "-" + (ordinal + 1) + "-" + submission.id() + "-" + reference.id();
    }

    private void cancel(Future<?>[] futures) {
        for (int index = 0; index < futures.length; index++) {
            if (futures[index] != null) {
                futures[index].cancel(true);
            }
        }
    }

    private int initialCandidateCapacity(int totalPairCount) {
        if (totalPairCount == 0) {
            return 1;
        }
        return totalPairCount < 16 ? totalPairCount : 16;
    }

    private int[] grow(int[] source, int maximum) {
        int capacity = source.length * 2;
        if (capacity < 0 || capacity > maximum) {
            capacity = maximum;
        }
        int[] result = new int[capacity];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static Settings requireSettings(Settings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        settings.validate();
        return settings;
    }

    private static void validateCasePrefix(String casePrefix) {
        if (casePrefix == null || casePrefix.isBlank()) {
            throw new IllegalArgumentException("casePrefix cannot be blank");
        }
    }

    private static void validateDocuments(Document[] documents, String name) {
        if (documents == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        for (int index = 0; index < documents.length; index++) {
            if (documents[index] == null) {
                throw new IllegalArgumentException(name + " cannot contain null documents");
            }
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("batch analysis was interrupted");
        }
    }

    private static final class CandidatePlan {
        private final int[] ordinals;
        private final int candidateCount;

        private CandidatePlan(int[] ordinals, int candidateCount) {
            this.ordinals = ordinals;
            this.candidateCount = candidateCount;
        }
    }
}
