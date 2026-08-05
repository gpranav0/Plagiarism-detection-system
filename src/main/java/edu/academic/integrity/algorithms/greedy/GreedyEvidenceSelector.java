package edu.academic.integrity.algorithms.greedy;

/**
 * Greedy approximation for a compact evidence summary. At every step it picks
 * the item with greatest (newly covered units * relevance) / cost.
 */
public final class GreedyEvidenceSelector {
    private GreedyEvidenceSelector() {
    }

    public static SelectionResult select(EvidenceCandidate[] candidates,
            int universeSize, int maximumItems) {
        if (candidates == null) {
            throw new IllegalArgumentException("Candidates cannot be null");
        }
        if (universeSize < 0) {
            throw new IllegalArgumentException("Universe size cannot be negative");
        }
        if (maximumItems < 0) {
            throw new IllegalArgumentException("Maximum items cannot be negative");
        }
        validateCandidates(candidates, universeSize);

        int limit = maximumItems < candidates.length ? maximumItems : candidates.length;
        int[] selectedIndexes = new int[limit];
        int[] marginalCoverage = new int[limit];
        boolean[] selected = new boolean[candidates.length];
        boolean[] covered = new boolean[universeSize];
        boolean[] countedThisCandidate = new boolean[universeSize];
        int selectedCount = 0;
        int coveredCount = 0;
        double totalCost = 0.0;
        double totalRelevance = 0.0;

        while (selectedCount < limit && coveredCount < universeSize) {
            int best = -1;
            int bestNewCoverage = 0;
            double bestUtility = -1.0;
            for (int candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
                if (selected[candidateIndex]) {
                    continue;
                }
                EvidenceCandidate candidate = candidates[candidateIndex];
                int newCoverage = countNewCoverage(candidate.coveredUnits, covered,
                        countedThisCandidate);
                double utility = newCoverage * candidate.relevance / candidate.cost;
                if (best < 0 || utility > bestUtility
                        || (Double.compare(utility, bestUtility) == 0
                            && (newCoverage > bestNewCoverage
                                || (newCoverage == bestNewCoverage
                                    && (candidate.relevance > candidates[best].relevance
                                        || (Double.compare(candidate.relevance,
                                                candidates[best].relevance) == 0
                                            && candidateIndex < best)))))) {
                    best = candidateIndex;
                    bestUtility = utility;
                    bestNewCoverage = newCoverage;
                }
            }
            if (best < 0 || bestNewCoverage == 0 || bestUtility <= 0.0) {
                break;
            }

            selected[best] = true;
            selectedIndexes[selectedCount] = best;
            marginalCoverage[selectedCount] = bestNewCoverage;
            selectedCount++;
            EvidenceCandidate chosen = candidates[best];
            for (int i = 0; i < chosen.coveredUnits.length; i++) {
                int unit = chosen.coveredUnits[i];
                if (!covered[unit]) {
                    covered[unit] = true;
                    coveredCount++;
                }
            }
            totalCost += chosen.cost;
            totalRelevance += chosen.relevance;
        }

        int[] uncovered = new int[universeSize - coveredCount];
        int output = 0;
        for (int unit = 0; unit < universeSize; unit++) {
            if (!covered[unit]) {
                uncovered[output++] = unit;
            }
        }
        return new SelectionResult(trim(selectedIndexes, selectedCount),
                trim(marginalCoverage, selectedCount), uncovered, coveredCount,
                universeSize, totalCost, totalRelevance);
    }

    private static int countNewCoverage(int[] units, boolean[] covered,
            boolean[] countedThisCandidate) {
        int count = 0;
        // Clear only positions touched by this candidate after counting them.
        for (int i = 0; i < units.length; i++) {
            int unit = units[i];
            if (!covered[unit] && !countedThisCandidate[unit]) {
                countedThisCandidate[unit] = true;
                count++;
            }
        }
        for (int i = 0; i < units.length; i++) {
            countedThisCandidate[units[i]] = false;
        }
        return count;
    }

    private static void validateCandidates(EvidenceCandidate[] candidates, int universeSize) {
        for (int i = 0; i < candidates.length; i++) {
            EvidenceCandidate candidate = candidates[i];
            if (candidate == null) {
                throw new IllegalArgumentException("Candidate " + i + " is null");
            }
            for (int position = 0; position < candidate.coveredUnits.length; position++) {
                int unit = candidate.coveredUnits[position];
                if (unit < 0 || unit >= universeSize) {
                    throw new IllegalArgumentException("Candidate coverage is outside the universe: " + unit);
                }
            }
        }
    }

    private static int[] trim(int[] source, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    public static final class EvidenceCandidate {
        private final String id;
        private final int[] coveredUnits;
        private final double relevance;
        private final double cost;

        public EvidenceCandidate(String id, int[] coveredUnits,
                double relevance, double cost) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Evidence id cannot be empty");
            }
            if (coveredUnits == null) {
                throw new IllegalArgumentException("Covered units cannot be null");
            }
            if (!Double.isFinite(relevance) || relevance < 0.0) {
                throw new IllegalArgumentException("Relevance must be finite and non-negative");
            }
            if (!Double.isFinite(cost) || cost <= 0.0) {
                throw new IllegalArgumentException("Cost must be finite and positive");
            }
            this.id = id;
            this.coveredUnits = trim(coveredUnits, coveredUnits.length);
            this.relevance = relevance;
            this.cost = cost;
        }

        public String id() {
            return id;
        }

        public int[] coveredUnits() {
            return trim(coveredUnits, coveredUnits.length);
        }

        public double relevance() {
            return relevance;
        }

        public double cost() {
            return cost;
        }
    }

    public static final class SelectionResult {
        private final int[] selectedIndexes;
        private final int[] marginalCoverage;
        private final int[] uncoveredUnits;
        private final int coveredCount;
        private final int universeSize;
        private final double totalCost;
        private final double totalRelevance;

        private SelectionResult(int[] selectedIndexes, int[] marginalCoverage,
                int[] uncoveredUnits, int coveredCount, int universeSize,
                double totalCost, double totalRelevance) {
            this.selectedIndexes = selectedIndexes;
            this.marginalCoverage = marginalCoverage;
            this.uncoveredUnits = uncoveredUnits;
            this.coveredCount = coveredCount;
            this.universeSize = universeSize;
            this.totalCost = totalCost;
            this.totalRelevance = totalRelevance;
        }

        public int selectedCount() {
            return selectedIndexes.length;
        }

        public int selectedIndexAt(int position) {
            checkPosition(position, selectedIndexes.length);
            return selectedIndexes[position];
        }

        public int marginalCoverageAt(int position) {
            checkPosition(position, marginalCoverage.length);
            return marginalCoverage[position];
        }

        public int[] selectedIndexes() {
            return trim(selectedIndexes, selectedIndexes.length);
        }

        public int coveredCount() {
            return coveredCount;
        }

        public int universeSize() {
            return universeSize;
        }

        public double coverageRatio() {
            return universeSize == 0 ? 1.0 : (double) coveredCount / universeSize;
        }

        public boolean hasFullCoverage() {
            return coveredCount == universeSize;
        }

        public int[] uncoveredUnits() {
            return trim(uncoveredUnits, uncoveredUnits.length);
        }

        public double totalCost() {
            return totalCost;
        }

        public double totalRelevance() {
            return totalRelevance;
        }

        private static void checkPosition(int position, int length) {
            if (position < 0 || position >= length) {
                throw new IndexOutOfBoundsException("Position: " + position);
            }
        }
    }
}
