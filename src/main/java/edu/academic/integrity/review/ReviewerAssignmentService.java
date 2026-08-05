package edu.academic.integrity.review;

import edu.academic.integrity.algorithms.flow.EdmondsKarpMaxFlow;
import edu.academic.integrity.algorithms.sort.GenericSorts;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.CaseAssignment;
import edu.academic.integrity.model.Reviewer;

/** Assigns the highest-risk reviewable cases while respecting reviewer capacities. */
public final class ReviewerAssignmentService {
    public CaseAssignment[] assign(AnalysisResult[] results, Reviewer[] reviewers,
                                   double reviewThreshold) {
        if (results == null || reviewers == null) {
            throw new IllegalArgumentException("Results and reviewers cannot be null");
        }
        AnalysisResult[] reviewable = filterAndRank(results, reviewThreshold);
        if (reviewable.length == 0 || reviewers.length == 0) return new CaseAssignment[0];

        boolean[][] eligible = new boolean[reviewable.length][reviewers.length];
        int[] capacities = new int[reviewers.length];
        for (int reviewer = 0; reviewer < reviewers.length; reviewer++) {
            capacities[reviewer] = reviewers[reviewer].capacity();
            for (int item = 0; item < reviewable.length; item++) eligible[item][reviewer] = true;
        }
        EdmondsKarpMaxFlow.AssignmentResult flow =
                EdmondsKarpMaxFlow.assignCases(eligible, capacities);
        CaseAssignment[] assignments = new CaseAssignment[flow.assignedCount()];
        int output = 0;
        for (int item = 0; item < reviewable.length; item++) {
            int reviewer = flow.reviewerForCase(item);
            if (reviewer >= 0) assignments[output++] = new CaseAssignment(reviewable[item], reviewers[reviewer]);
        }
        return assignments;
    }

    public AnalysisResult[] filterAndRank(AnalysisResult[] results, double reviewThreshold) {
        int count = 0;
        for (AnalysisResult result : results) {
            if (result != null && result.score().total() >= reviewThreshold) count++;
        }
        AnalysisResult[] reviewable = new AnalysisResult[count];
        int output = 0;
        for (AnalysisResult result : results) {
            if (result != null && result.score().total() >= reviewThreshold) {
                reviewable[output++] = result;
            }
        }
        GenericSorts.mergeSort(reviewable, (first, second) -> {
            int score = Double.compare(second.score().total(), first.score().total());
            return score != 0 ? score : first.caseId().compareTo(second.caseId());
        });
        return reviewable;
    }
}
