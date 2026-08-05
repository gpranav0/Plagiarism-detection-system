package edu.academic.integrity.model;

public final class PassageMatch {
    private final MatchType type;
    private final String submissionId;
    private final String referenceId;
    private final int submissionStart;
    private final int submissionEnd;
    private final int referenceStart;
    private final int referenceEnd;
    private final double similarity;
    private final String algorithm;
    private final String excerpt;

    public PassageMatch(MatchType type, String submissionId, String referenceId,
                        int submissionStart, int submissionEnd,
                        int referenceStart, int referenceEnd,
                        double similarity, String algorithm, String excerpt) {
        this.type = type;
        this.submissionId = submissionId;
        this.referenceId = referenceId;
        this.submissionStart = submissionStart;
        this.submissionEnd = submissionEnd;
        this.referenceStart = referenceStart;
        this.referenceEnd = referenceEnd;
        this.similarity = clamp(similarity);
        this.algorithm = algorithm == null ? "" : algorithm;
        this.excerpt = excerpt == null ? "" : excerpt;
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    public MatchType type() { return type; }
    public String submissionId() { return submissionId; }
    public String referenceId() { return referenceId; }
    public int submissionStart() { return submissionStart; }
    public int submissionEnd() { return submissionEnd; }
    public int referenceStart() { return referenceStart; }
    public int referenceEnd() { return referenceEnd; }
    public double similarity() { return similarity; }
    public String algorithm() { return algorithm; }
    public String excerpt() { return excerpt; }
}

