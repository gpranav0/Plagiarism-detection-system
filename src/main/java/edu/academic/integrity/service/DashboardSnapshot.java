package edu.academic.integrity.service;

/** Immutable data required to render the dashboard without exposing engine internals. */
public final class DashboardSnapshot {
    private final int submissionCount;
    private final int referenceCount;
    private final int completedAnalyses;
    private final int highRiskCases;
    private final int pendingAssignments;
    private final String recentActivity;

    public DashboardSnapshot(int submissionCount, int referenceCount, int completedAnalyses,
            int highRiskCases, int pendingAssignments, String recentActivity) {
        this.submissionCount = nonNegative(submissionCount, "submissionCount");
        this.referenceCount = nonNegative(referenceCount, "referenceCount");
        this.completedAnalyses = nonNegative(completedAnalyses, "completedAnalyses");
        this.highRiskCases = nonNegative(highRiskCases, "highRiskCases");
        this.pendingAssignments = nonNegative(pendingAssignments, "pendingAssignments");
        this.recentActivity = recentActivity == null ? "" : recentActivity;
    }

    public int submissionCount() { return submissionCount; }
    public int referenceCount() { return referenceCount; }
    public int totalDocuments() { return submissionCount + referenceCount; }
    public int completedAnalyses() { return completedAnalyses; }
    public int highRiskCases() { return highRiskCases; }
    public int pendingAssignments() { return pendingAssignments; }
    public String recentActivity() { return recentActivity; }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " cannot be negative");
        return value;
    }
}
