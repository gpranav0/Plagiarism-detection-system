package edu.academic.integrity.algorithms.greedy;

/** Cost-aware greedy set-cover approximation over integer universe elements. */
public final class SetCoverSolver {
    private SetCoverSolver() {
    }

    public static Result cover(int universeSize, CandidateSet[] sets) {
        if (sets == null) {
            throw new IllegalArgumentException("Sets cannot be null");
        }
        GreedyEvidenceSelector.EvidenceCandidate[] adapted =
                new GreedyEvidenceSelector.EvidenceCandidate[sets.length];
        for (int i = 0; i < sets.length; i++) {
            if (sets[i] == null) {
                throw new IllegalArgumentException("Set " + i + " is null");
            }
            adapted[i] = new GreedyEvidenceSelector.EvidenceCandidate(
                    sets[i].id(), sets[i].elements(), 1.0, sets[i].cost());
        }
        GreedyEvidenceSelector.SelectionResult selected =
                GreedyEvidenceSelector.select(adapted, universeSize, sets.length);
        return new Result(selected.selectedIndexes(), selected.uncoveredUnits(),
                selected.totalCost(), selected.hasFullCoverage());
    }

    public static final class CandidateSet {
        private final String id;
        private final int[] elements;
        private final double cost;

        public CandidateSet(String id, int[] elements, double cost) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Set id cannot be empty");
            }
            if (elements == null) {
                throw new IllegalArgumentException("Elements cannot be null");
            }
            if (!Double.isFinite(cost) || cost <= 0.0) {
                throw new IllegalArgumentException("Cost must be finite and positive");
            }
            this.id = id;
            this.elements = copy(elements);
            this.cost = cost;
        }

        public String id() {
            return id;
        }

        public int[] elements() {
            return copy(elements);
        }

        public double cost() {
            return cost;
        }
    }

    public static final class Result {
        private final int[] selectedSetIndexes;
        private final int[] uncoveredElements;
        private final double totalCost;
        private final boolean complete;

        private Result(int[] selectedSetIndexes, int[] uncoveredElements,
                double totalCost, boolean complete) {
            this.selectedSetIndexes = selectedSetIndexes;
            this.uncoveredElements = uncoveredElements;
            this.totalCost = totalCost;
            this.complete = complete;
        }

        public int[] selectedSetIndexes() {
            return copy(selectedSetIndexes);
        }

        public int[] uncoveredElements() {
            return copy(uncoveredElements);
        }

        public double totalCost() {
            return totalCost;
        }

        public boolean isComplete() {
            return complete;
        }
    }

    private static int[] copy(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }
}
