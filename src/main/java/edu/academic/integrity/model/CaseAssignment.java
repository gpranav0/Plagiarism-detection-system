package edu.academic.integrity.model;

public final class CaseAssignment {
    private final AnalysisResult result;
    private final Reviewer reviewer;

    public CaseAssignment(AnalysisResult result, Reviewer reviewer) {
        this.result = result;
        this.reviewer = reviewer;
    }

    public AnalysisResult result() { return result; }
    public Reviewer reviewer() { return reviewer; }
}

