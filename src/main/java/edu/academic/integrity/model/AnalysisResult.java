package edu.academic.integrity.model;

public final class AnalysisResult implements Comparable<AnalysisResult> {
    private final String caseId;
    private final Document submission;
    private final Document reference;
    private final ScoreBreakdown score;
    private final PassageMatch[] evidence;
    private final long elapsedNanos;

    public AnalysisResult(String caseId, Document submission, Document reference,
                          ScoreBreakdown score, PassageMatch[] evidence, long elapsedNanos) {
        this.caseId = caseId;
        this.submission = submission;
        this.reference = reference;
        this.score = score;
        this.evidence = evidence == null ? new PassageMatch[0] : evidence;
        this.elapsedNanos = elapsedNanos;
    }

    public String caseId() { return caseId; }
    public Document submission() { return submission; }
    public Document reference() { return reference; }
    public ScoreBreakdown score() { return score; }
    public PassageMatch[] evidence() { return evidence; }
    public long elapsedNanos() { return elapsedNanos; }

    @Override
    public int compareTo(AnalysisResult other) {
        int scoreComparison = Double.compare(score.total(), other.score.total());
        if (scoreComparison != 0) return scoreComparison;
        return caseId.compareTo(other.caseId);
    }
}

