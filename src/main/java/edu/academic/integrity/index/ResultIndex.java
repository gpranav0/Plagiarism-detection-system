package edu.academic.integrity.index;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.structures.AVLTree;
import edu.academic.integrity.structures.BinarySearchTree;
import edu.academic.integrity.structures.DynamicArray;
import edu.academic.integrity.structures.MaxHeap;

/** Case lookup and deterministic descending-risk ranking for analysis results. */
public final class ResultIndex {
    private final BinarySearchTree<CaseEntry> byCaseId = new BinarySearchTree<>();
    private final AVLTree<RankedEntry> byRisk = new AVLTree<>();
    private final MaxHeap<RankedEntry> highestRisk = new MaxHeap<>();

    public boolean add(AnalysisResult result) {
        requireResult(result);
        if (findEntry(result.caseId()) != null) return false;
        RankedEntry ranked = new RankedEntry(result);
        CaseEntry byCase = new CaseEntry(result.caseId(), result, ranked);
        byCaseId.insert(byCase);
        byRisk.insert(ranked);
        highestRisk.add(ranked);
        return true;
    }

    public AnalysisResult put(AnalysisResult result) {
        requireResult(result);
        AnalysisResult former = get(result.caseId());
        if (former != null) remove(result.caseId());
        add(result);
        return former;
    }

    public AnalysisResult get(String caseId) {
        CaseEntry entry = findEntry(caseId);
        return entry == null ? null : entry.result;
    }

    public boolean containsCase(String caseId) {
        return findEntry(caseId) != null;
    }

    public AnalysisResult remove(String caseId) {
        CaseEntry entry = findEntry(caseId);
        if (entry == null) return null;
        byCaseId.remove(entry);
        byRisk.remove(entry.ranked);
        highestRisk.remove(entry.ranked);
        return entry.result;
    }

    public int size() { return byCaseId.size(); }
    public boolean isEmpty() { return byCaseId.isEmpty(); }

    public void clear() {
        byCaseId.clear();
        byRisk.clear();
        highestRisk.clear();
    }

    public AnalysisResult highestRisk() {
        return highestRisk.isEmpty() ? null : highestRisk.peek().result;
    }

    public AnalysisResult[] resultsByCaseId() {
        DynamicArray<CaseEntry> entries = byCaseId.inOrder();
        AnalysisResult[] results = new AnalysisResult[entries.size()];
        for (int i = 0; i < results.length; i++) results[i] = entries.get(i).result;
        return results;
    }

    /** Returns every result from highest to lowest composite score. */
    public AnalysisResult[] rankedDescending() {
        return topRisks(size());
    }

    /** Returns every result from lowest to highest composite score via the AVL index. */
    public AnalysisResult[] rankedAscending() {
        DynamicArray<RankedEntry> entries = byRisk.inOrder();
        AnalysisResult[] output = new AnalysisResult[entries.size()];
        for (int i = 0; i < output.length; i++) output[i] = entries.get(i).result;
        return output;
    }

    /** Returns at most {@code limit} results from highest to lowest composite score. */
    public AnalysisResult[] topRisks(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        int count = Math.min(limit, size());
        AnalysisResult[] output = new AnalysisResult[count];
        MaxHeap<RankedEntry> copy = new MaxHeap<>();
        DynamicArray<RankedEntry> entries = byRisk.inOrder();
        for (int i = 0; i < entries.size(); i++) copy.add(entries.get(i));
        for (int i = 0; i < count; i++) output[i] = copy.poll().result;
        return output;
    }

    /** Returns matching results in descending score order, with both score bounds inclusive. */
    public AnalysisResult[] rangeByScore(double minimumInclusive, double maximumInclusive) {
        validateScoreBound(minimumInclusive, "minimumInclusive");
        validateScoreBound(maximumInclusive, "maximumInclusive");
        if (minimumInclusive > maximumInclusive) return new AnalysisResult[0];
        DynamicArray<RankedEntry> ordered = byRisk.inOrder();
        int count = 0;
        for (int i = 0; i < ordered.size(); i++) {
            double score = ordered.get(i).result.score().total();
            if (score >= minimumInclusive && score <= maximumInclusive) count++;
        }
        AnalysisResult[] output = new AnalysisResult[count];
        int destination = 0;
        for (int i = ordered.size() - 1; i >= 0; i--) {
            AnalysisResult result = ordered.get(i).result;
            double score = result.score().total();
            if (score >= minimumInclusive && score <= maximumInclusive) {
                output[destination++] = result;
            }
        }
        return output;
    }

    public ValidationSummary validationSummary() {
        boolean binarySearchTreeValid = byCaseId.validateInvariant();
        boolean avlTreeValid = byRisk.validateInvariant();
        boolean maxHeapValid = highestRisk.validateInvariant();
        boolean sizesMatch = byCaseId.size() == byRisk.size() && byRisk.size() == highestRisk.size();
        boolean crossReferencesMatch = sizesMatch;
        if (crossReferencesMatch) {
            DynamicArray<CaseEntry> cases = byCaseId.inOrder();
            for (int i = 0; i < cases.size(); i++) {
                CaseEntry entry = cases.get(i);
                if (!byRisk.contains(entry.ranked)) {
                    crossReferencesMatch = false;
                    break;
                }
            }
        }
        return new ValidationSummary(binarySearchTreeValid, avlTreeValid, maxHeapValid,
                sizesMatch, crossReferencesMatch, size());
    }

    public boolean validateInvariant() {
        return validationSummary().valid();
    }

    private CaseEntry findEntry(String caseId) {
        requireCaseId(caseId);
        return byCaseId.find(new CaseEntry(caseId, null, null));
    }

    private static void requireResult(AnalysisResult result) {
        if (result == null) throw new IllegalArgumentException("result cannot be null");
        requireCaseId(result.caseId());
        if (result.score() == null) throw new IllegalArgumentException("result score cannot be null");
    }

    private static void requireCaseId(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId cannot be blank");
        }
    }

    private static void validateScoreBound(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private static final class CaseEntry implements Comparable<CaseEntry> {
        private final String caseId;
        private final AnalysisResult result;
        private final RankedEntry ranked;

        private CaseEntry(String caseId, AnalysisResult result, RankedEntry ranked) {
            this.caseId = caseId;
            this.result = result;
            this.ranked = ranked;
        }

        @Override
        public int compareTo(CaseEntry other) {
            return caseId.compareTo(other.caseId);
        }
    }

    private static final class RankedEntry implements Comparable<RankedEntry> {
        private final AnalysisResult result;
        private RankedEntry(AnalysisResult result) { this.result = result; }

        @Override
        public int compareTo(RankedEntry other) {
            int scoreComparison = Double.compare(result.score().total(), other.result.score().total());
            if (scoreComparison != 0) return scoreComparison;
            return result.caseId().compareTo(other.result.caseId());
        }
    }

    public static final class ValidationSummary {
        private final boolean binarySearchTreeValid;
        private final boolean avlTreeValid;
        private final boolean maxHeapValid;
        private final boolean sizesMatch;
        private final boolean crossReferencesMatch;
        private final int indexedResults;

        private ValidationSummary(boolean binarySearchTreeValid, boolean avlTreeValid,
                boolean maxHeapValid, boolean sizesMatch, boolean crossReferencesMatch,
                int indexedResults) {
            this.binarySearchTreeValid = binarySearchTreeValid;
            this.avlTreeValid = avlTreeValid;
            this.maxHeapValid = maxHeapValid;
            this.sizesMatch = sizesMatch;
            this.crossReferencesMatch = crossReferencesMatch;
            this.indexedResults = indexedResults;
        }

        public boolean binarySearchTreeValid() { return binarySearchTreeValid; }
        public boolean avlTreeValid() { return avlTreeValid; }
        public boolean maxHeapValid() { return maxHeapValid; }
        public boolean sizesMatch() { return sizesMatch; }
        public boolean crossReferencesMatch() { return crossReferencesMatch; }
        public int indexedResults() { return indexedResults; }
        public boolean valid() {
            return binarySearchTreeValid && avlTreeValid && maxHeapValid && sizesMatch
                    && crossReferencesMatch;
        }
    }
}
