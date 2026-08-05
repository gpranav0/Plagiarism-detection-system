package edu.academic.integrity.service;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.Reviewer;

/** Suggested/current reviewer routing, utilization, and cases still awaiting capacity. */
public final class AssignmentPlan {
    private final Reviewer[] reviewers;
    private final CaseAssignment[] assignments;
    private final AnalysisResult[] unassigned;
    private final ReviewerUtilization[] utilization;
    private final int maxFlowCount;

    public AssignmentPlan(Reviewer[] reviewers, CaseAssignment[] assignments,
            AnalysisResult[] unassigned, ReviewerUtilization[] utilization, int maxFlowCount) {
        this.reviewers = copy(reviewers == null ? new Reviewer[0] : reviewers);
        this.assignments = copy(assignments == null ? new CaseAssignment[0] : assignments);
        this.unassigned = copy(unassigned == null ? new AnalysisResult[0] : unassigned);
        this.utilization = copy(utilization == null ? new ReviewerUtilization[0] : utilization);
        this.maxFlowCount = Math.max(0, maxFlowCount);
    }

    public Reviewer[] reviewers() { return copy(reviewers); }
    public CaseAssignment[] assignments() { return copy(assignments); }
    public AnalysisResult[] unassigned() { return copy(unassigned); }
    public ReviewerUtilization[] utilization() { return copy(utilization); }
    public int maxFlowCount() { return maxFlowCount; }
    public int assignedCount() { return assignments.length; }
    public int unassignedCount() { return unassigned.length; }

    public static final class ReviewerUtilization {
        private final Reviewer reviewer;
        private final int assignedCases;

        public ReviewerUtilization(Reviewer reviewer, int assignedCases) {
            if (reviewer == null || assignedCases < 0) {
                throw new IllegalArgumentException("Reviewer utilization is invalid");
            }
            this.reviewer = reviewer;
            this.assignedCases = assignedCases;
        }

        public Reviewer reviewer() { return reviewer; }
        public int assignedCases() { return assignedCases; }
        public int capacity() { return reviewer.capacity(); }
        public int remainingCapacity() {
            return Math.max(0, reviewer.capacity() - assignedCases);
        }
        public double utilizationRatio() {
            return reviewer.capacity() == 0 ? 0.0
                    : (double) assignedCases / reviewer.capacity();
        }
    }

    private static Reviewer[] copy(Reviewer[] source) {
        Reviewer[] result = new Reviewer[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static CaseAssignment[] copy(CaseAssignment[] source) {
        CaseAssignment[] result = new CaseAssignment[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static AnalysisResult[] copy(AnalysisResult[] source) {
        AnalysisResult[] result = new AnalysisResult[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static ReviewerUtilization[] copy(ReviewerUtilization[] source) {
        ReviewerUtilization[] result = new ReviewerUtilization[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
