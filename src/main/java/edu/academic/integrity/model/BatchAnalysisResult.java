package edu.academic.integrity.model;

/** Immutable summary of candidate selection and deterministic pair analysis. */
public final class BatchAnalysisResult {
    private final long totalPairCount;
    private final int candidateCount;
    private final int comparisonCount;
    private final double candidateReduction;
    private final long elapsedNanos;
    private final boolean parallel;
    private final AnalysisResult[] results;

    public BatchAnalysisResult(long totalPairCount, int candidateCount, int comparisonCount,
            double candidateReduction, long elapsedNanos, boolean parallel,
            AnalysisResult[] results) {
        if (totalPairCount < 0 || candidateCount < 0 || comparisonCount < 0) {
            throw new IllegalArgumentException("batch counts cannot be negative");
        }
        if (candidateCount > totalPairCount || comparisonCount > candidateCount) {
            throw new IllegalArgumentException("batch counts are inconsistent");
        }
        if (Double.isNaN(candidateReduction)
                || candidateReduction < 0.0 || candidateReduction > 1.0) {
            throw new IllegalArgumentException("candidate reduction must be between zero and one");
        }
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("elapsed time cannot be negative");
        }
        if (results == null || results.length != comparisonCount) {
            throw new IllegalArgumentException("results length must equal comparison count");
        }
        for (int index = 0; index < results.length; index++) {
            if (results[index] == null) {
                throw new IllegalArgumentException("results cannot contain null values");
            }
        }
        this.totalPairCount = totalPairCount;
        this.candidateCount = candidateCount;
        this.comparisonCount = comparisonCount;
        this.candidateReduction = candidateReduction;
        this.elapsedNanos = elapsedNanos;
        this.parallel = parallel;
        this.results = copy(results);
    }

    public long totalPairCount() {
        return totalPairCount;
    }

    public long getTotalPairCount() {
        return totalPairCount;
    }

    public int candidateCount() {
        return candidateCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public int comparisonCount() {
        return comparisonCount;
    }

    public int getComparisonCount() {
        return comparisonCount;
    }

    public double candidateReduction() {
        return candidateReduction;
    }

    public double getCandidateReduction() {
        return candidateReduction;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    public boolean parallel() {
        return parallel;
    }

    public boolean isParallel() {
        return parallel;
    }

    public AnalysisResult[] results() {
        return copy(results);
    }

    public AnalysisResult[] getResults() {
        return results();
    }

    private static AnalysisResult[] copy(AnalysisResult[] source) {
        AnalysisResult[] result = new AnalysisResult[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
