package edu.academic.integrity.benchmark;

import edu.academic.integrity.algorithms.benchmark.AlgorithmBenchmark;
import edu.academic.integrity.algorithms.benchmark.BenchmarkResult;
import edu.academic.integrity.algorithms.text.FuzzyAlignment;
import edu.academic.integrity.algorithms.text.KMP;
import edu.academic.integrity.algorithms.text.TextNormalizer;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.core.BatchAnalyzer;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.structures.AVLTree;
import edu.academic.integrity.structures.BPlusTree;
import edu.academic.integrity.structures.BTree;
import edu.academic.integrity.structures.BinarySearchTree;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/** Produces measurable sorting, tree, candidate, and concurrency evidence. */
public final class BenchmarkService {
    private static volatile long matcherChecksumSink;
    private final Settings settings;
    private final String[] stopwords;

    public BenchmarkService(Settings settings, String[] stopwords) {
        this.settings = settings;
        this.stopwords = stopwords;
    }

    public String run(Document[] submissions, Document[] references) {
        StringBuilder report = new StringBuilder(2048);
        report.append("ALGORITHM BENCHMARKS\n");
        report.append("====================\n");

        BenchmarkResult[] sorts = AlgorithmBenchmark.benchmarkGenerated(4_000, 3,
                0x25C51305EL);
        report.append("\nSorting algorithms (4,000 integer scores from 0 to 10,000; 3 repetitions)\n");
        for (BenchmarkResult result : sorts) report.append("  ").append(result.toReportLine()).append('\n');

        int itemCount = 2_047;
        BinarySearchTree<Integer> bst = new BinarySearchTree<>();
        AVLTree<Integer> avl = new AVLTree<>();
        long bstStarted = System.nanoTime();
        for (int i = 0; i < itemCount; i++) bst.insert(i);
        long bstNanos = System.nanoTime() - bstStarted;
        long avlStarted = System.nanoTime();
        for (int i = 0; i < itemCount; i++) avl.insert(i);
        long avlNanos = System.nanoTime() - avlStarted;
        report.append("\nOrdered tree insertion (ascending IDs)\n");
        report.append("  BST: height=").append(bst.height()).append(", time=")
                .append(formatMillis(bstNanos)).append(" ms, invariant=").append(bst.validateInvariant()).append('\n');
        report.append("  AVL: height=").append(avl.height()).append(", time=")
                .append(formatMillis(avlNanos)).append(" ms, invariant=").append(avl.validateInvariant()).append('\n');

        BTree<Integer> bTree = new BTree<>(4);
        BPlusTree<Integer, Integer> bPlusTree = new BPlusTree<>(4);
        for (int i = 0; i < itemCount; i++) {
            int key = (i * 977) % itemCount;
            bTree.insert(key);
            bPlusTree.put(key, i);
        }
        report.append("\nMulti-way indexes\n");
        report.append("  B-tree: size=").append(bTree.size()).append(", height=")
                .append(bTree.height()).append(", invariant=").append(bTree.validateInvariant()).append('\n');
        report.append("  B+ tree: size=").append(bPlusTree.size()).append(", height=")
                .append(bPlusTree.height()).append(", invariant=").append(bPlusTree.validateInvariant()).append('\n');

        if (submissions.length > 0 && references.length > 0) {
            Document[] submissionSample = first(submissions, 12);
            Document[] referenceSample = first(references, 12);
            BatchAnalyzer analyzer = new BatchAnalyzer(settings, stopwords);
            long memoryBefore = usedMemory();
            BatchAnalysisResult sequential = analyzer.analyzeSequential(
                    "CONSISTENCY", submissionSample, referenceSample);
            BatchAnalysisResult parallel = analyzer.analyzeParallel(
                    "CONSISTENCY", submissionSample, referenceSample);
            long memoryAfter = usedMemory();
            report.append("\nCandidate selection and bounded parallel comparison\n");
            report.append("  Total pairs: ").append(parallel.totalPairCount()).append('\n');
            report.append("  MinHash candidates: ").append(parallel.candidateCount()).append('\n');
            report.append("  Candidate reduction: ")
                    .append(String.format("%.2f%%", parallel.candidateReduction() * 100.0)).append('\n');
            report.append("  Sequential time: ").append(formatMillis(sequential.elapsedNanos())).append(" ms\n");
            report.append("  Parallel time: ").append(formatMillis(parallel.elapsedNanos())).append(" ms\n");
            report.append("  Deterministic equality: ").append(equivalent(sequential, parallel)).append('\n');
            report.append("  Approximate memory delta: ").append(memoryAfter - memoryBefore).append(" bytes\n");
            appendMatcherComparison(report, submissionSample[0], referenceSample[0]);
        } else {
            report.append("\nCorpus comparison benchmark skipped: import submissions and references first.\n");
        }
        return report.toString();
    }

    public File runAndSave(Document[] submissions, Document[] references, File directory)
            throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create benchmark directory: " + directory);
        }
        File file = new File(directory, "algorithm-benchmarks.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(run(submissions, references));
        }
        return file;
    }

    private boolean equivalent(BatchAnalysisResult first, BatchAnalysisResult second) {
        AnalysisResult[] left = first.results();
        AnalysisResult[] right = second.results();
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (!left[i].caseId().equals(right[i].caseId())) return false;
            if (Math.abs(left[i].score().total() - right[i].score().total()) > 1e-12) return false;
        }
        return true;
    }

    private void appendMatcherComparison(StringBuilder report, Document submission,
                                         Document reference) {
        String submissionText = TextNormalizer.normalize(submission.content());
        String referenceText = TextNormalizer.normalize(reference.content());
        int phraseLength = Math.min(120, submissionText.length());
        String phrase = submissionText.substring(0, phraseLength);
        String[] submissionTokens = first(TextNormalizer.tokenize(submissionText), 250);
        String[] referenceTokens = first(TextNormalizer.tokenize(referenceText), 250);

        final int exactRepetitions = 50;
        final int fuzzyRepetitions = 3;
        KMP.indexOf(referenceText, phrase);
        FuzzyAlignment.align(submissionTokens, referenceTokens);

        long exactStarted = System.nanoTime();
        int exactChecksum = 0;
        for (int run = 0; run < exactRepetitions; run++) {
            exactChecksum ^= KMP.indexOf(referenceText, phrase) + run;
        }
        long exactNanos = System.nanoTime() - exactStarted;

        long fuzzyStarted = System.nanoTime();
        int fuzzyChecksum = 0;
        for (int run = 0; run < fuzzyRepetitions; run++) {
            fuzzyChecksum ^= FuzzyAlignment.align(submissionTokens, referenceTokens).score() + run;
        }
        long fuzzyNanos = System.nanoTime() - fuzzyStarted;
        matcherChecksumSink ^= exactChecksum ^ fuzzyChecksum;

        report.append("\nExact versus fuzzy matching (actual first imported document pair)\n");
        report.append("  Exact KMP: patternChars=").append(phraseLength)
                .append(", runs=").append(exactRepetitions).append(", average=")
                .append(formatMillis(exactNanos / exactRepetitions)).append(" ms\n");
        report.append("  Fuzzy Smith-Waterman: submissionTokens=")
                .append(submissionTokens.length).append(", referenceTokens=")
                .append(referenceTokens.length).append(", runs=").append(fuzzyRepetitions)
                .append(", average=").append(formatMillis(fuzzyNanos / fuzzyRepetitions))
                .append(" ms\n");
    }

    private Document[] first(Document[] documents, int maximum) {
        int length = Math.min(documents.length, maximum);
        Document[] result = new Document[length];
        System.arraycopy(documents, 0, result, 0, length);
        return result;
    }

    private String[] first(String[] values, int maximum) {
        int length = Math.min(values.length, maximum);
        String[] result = new String[length];
        System.arraycopy(values, 0, result, 0, length);
        return result;
    }

    private long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private String formatMillis(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }
}
