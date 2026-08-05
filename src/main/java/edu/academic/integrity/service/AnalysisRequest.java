package edu.academic.integrity.service;

/** Immutable, UI-neutral configuration for one submission analysis. */
public final class AnalysisRequest {
    private final String submissionId;
    private final String referenceId;
    private final boolean parallel;
    private final double candidateThreshold;
    private final double reviewThreshold;
    private final double graphEdgeThreshold;
    private final boolean exactEnabled;
    private final boolean shingleEnabled;
    private final boolean fuzzyEnabled;
    private final boolean graphEnabled;

    public AnalysisRequest(String submissionId, String referenceId, boolean parallel,
            double candidateThreshold, double reviewThreshold, double graphEdgeThreshold,
            boolean exactEnabled, boolean shingleEnabled, boolean fuzzyEnabled,
            boolean graphEnabled) {
        if (submissionId == null || submissionId.isBlank()) {
            throw new IllegalArgumentException("Select a submission before starting analysis");
        }
        validateOptionalThreshold(candidateThreshold, "candidate threshold");
        validateOptionalThreshold(reviewThreshold, "review threshold");
        validateOptionalThreshold(graphEdgeThreshold, "graph threshold");
        if (!exactEnabled && !shingleEnabled && !fuzzyEnabled && !graphEnabled) {
            throw new IllegalArgumentException("Enable at least one analysis family");
        }
        this.submissionId = submissionId.trim();
        this.referenceId = referenceId == null || referenceId.isBlank()
                ? null : referenceId.trim();
        this.parallel = parallel;
        this.candidateThreshold = candidateThreshold;
        this.reviewThreshold = reviewThreshold;
        this.graphEdgeThreshold = graphEdgeThreshold;
        this.exactEnabled = exactEnabled;
        this.shingleEnabled = shingleEnabled;
        this.fuzzyEnabled = fuzzyEnabled;
        this.graphEnabled = graphEnabled;
    }

    /** Uses the active thresholds and enables the complete analysis pipeline. */
    public AnalysisRequest(String submissionId, String referenceId, boolean parallel) {
        this(submissionId, referenceId, parallel, Double.NaN, Double.NaN, Double.NaN,
                true, true, true, true);
    }

    public String submissionId() { return submissionId; }
    public String referenceId() { return referenceId; }
    public boolean allReferences() { return referenceId == null; }
    public boolean parallel() { return parallel; }
    public double candidateThreshold() { return candidateThreshold; }
    public double reviewThreshold() { return reviewThreshold; }
    public double graphEdgeThreshold() { return graphEdgeThreshold; }
    public boolean exactEnabled() { return exactEnabled; }
    public boolean shingleEnabled() { return shingleEnabled; }
    public boolean fuzzyEnabled() { return fuzzyEnabled; }
    public boolean graphEnabled() { return graphEnabled; }

    public boolean hasCandidateThreshold() { return !Double.isNaN(candidateThreshold); }
    public boolean hasReviewThreshold() { return !Double.isNaN(reviewThreshold); }
    public boolean hasGraphEdgeThreshold() { return !Double.isNaN(graphEdgeThreshold); }

    private static void validateOptionalThreshold(double value, String name) {
        if (!Double.isNaN(value) && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
