package edu.academic.integrity.algorithms.flow;

/**
 * Edmonds-Karp maximum flow using a capacity matrix and a custom array queue.
 * Long capacities make the class useful for both unit assignments and scaled
 * workloads without floating-point rounding.
 */
public final class EdmondsKarpMaxFlow {
    private final long[][] capacity;

    public EdmondsKarpMaxFlow(int vertexCount) {
        if (vertexCount < 2) {
            throw new IllegalArgumentException("A flow network needs at least two vertices");
        }
        capacity = new long[vertexCount][vertexCount];
    }

    public int vertexCount() {
        return capacity.length;
    }

    /** Adds capacity, allowing callers to represent parallel arcs. */
    public void addEdge(int from, int to, long additionalCapacity) {
        checkVertex(from);
        checkVertex(to);
        if (from == to) {
            throw new IllegalArgumentException("Self-loop capacity is not useful in this network");
        }
        if (additionalCapacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }
        if (Long.MAX_VALUE - capacity[from][to] < additionalCapacity) {
            throw new ArithmeticException("Capacity overflow");
        }
        capacity[from][to] += additionalCapacity;
    }

    public void setCapacity(int from, int to, long newCapacity) {
        checkVertex(from);
        checkVertex(to);
        if (from == to) {
            throw new IllegalArgumentException("Self-loop capacity is not useful in this network");
        }
        if (newCapacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }
        capacity[from][to] = newCapacity;
    }

    public long capacity(int from, int to) {
        checkVertex(from);
        checkVertex(to);
        return capacity[from][to];
    }

    /** Computes a fresh flow, leaving the configured capacities unchanged. */
    public Result maximumFlow(int source, int sink) {
        checkVertex(source);
        checkVertex(sink);
        if (source == sink) {
            throw new IllegalArgumentException("Source and sink must differ");
        }
        int n = capacity.length;
        long[][] residual = copy(capacity);
        int[] parent = new int[n];
        int[] queue = new int[n];
        long maximum = 0L;

        while (findAugmentingPath(residual, source, sink, parent, queue)) {
            long bottleneck = Long.MAX_VALUE;
            int current = sink;
            while (current != source) {
                int previous = parent[current];
                if (residual[previous][current] < bottleneck) {
                    bottleneck = residual[previous][current];
                }
                current = previous;
            }
            if (Long.MAX_VALUE - maximum < bottleneck) {
                throw new ArithmeticException("Maximum-flow value overflow");
            }
            maximum += bottleneck;
            current = sink;
            while (current != source) {
                int previous = parent[current];
                residual[previous][current] -= bottleneck;
                if (Long.MAX_VALUE - residual[current][previous] < bottleneck) {
                    throw new ArithmeticException("Residual-capacity overflow");
                }
                residual[current][previous] += bottleneck;
                current = previous;
            }
        }

        boolean[] sourceSide = reachableInResidual(residual, source, queue);
        long[][] netFlow = new long[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                netFlow[from][to] = capacity[from][to] - residual[from][to];
            }
        }
        return new Result(maximum, netFlow, residual, sourceSide, copy(capacity));
    }

    /**
     * Builds the standard source-cases-reviewers-sink network. Each case is
     * assigned at most once and each reviewer observes the supplied capacity.
     */
    public static AssignmentResult assignCases(boolean[][] eligible,
            int[] reviewerCapacities) {
        if (eligible == null || reviewerCapacities == null) {
            throw new IllegalArgumentException("Eligibility and capacities cannot be null");
        }
        int caseCount = eligible.length;
        int reviewerCount = reviewerCapacities.length;
        for (int reviewer = 0; reviewer < reviewerCount; reviewer++) {
            if (reviewerCapacities[reviewer] < 0) {
                throw new IllegalArgumentException("Reviewer capacity cannot be negative");
            }
        }
        for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
            if (eligible[caseIndex] == null || eligible[caseIndex].length != reviewerCount) {
                throw new IllegalArgumentException("Eligibility matrix must be rectangular");
            }
        }

        int source = 0;
        int firstCase = 1;
        int firstReviewer = firstCase + caseCount;
        int sink = firstReviewer + reviewerCount;
        EdmondsKarpMaxFlow network = new EdmondsKarpMaxFlow(sink + 1);
        for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
            network.addEdge(source, firstCase + caseIndex, 1L);
            for (int reviewer = 0; reviewer < reviewerCount; reviewer++) {
                if (eligible[caseIndex][reviewer]) {
                    network.addEdge(firstCase + caseIndex, firstReviewer + reviewer, 1L);
                }
            }
        }
        for (int reviewer = 0; reviewer < reviewerCount; reviewer++) {
            network.addEdge(firstReviewer + reviewer, sink, reviewerCapacities[reviewer]);
        }

        Result flow = network.maximumFlow(source, sink);
        int[] reviewerForCase = new int[caseCount];
        int[] reviewerLoads = new int[reviewerCount];
        for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
            reviewerForCase[caseIndex] = -1;
            for (int reviewer = 0; reviewer < reviewerCount; reviewer++) {
                if (flow.netFlow(firstCase + caseIndex, firstReviewer + reviewer) > 0L) {
                    reviewerForCase[caseIndex] = reviewer;
                    reviewerLoads[reviewer]++;
                    break;
                }
            }
        }
        return new AssignmentResult(reviewerForCase, reviewerLoads, (int) flow.value());
    }

    private static boolean findAugmentingPath(long[][] residual, int source, int sink,
            int[] parent, int[] queue) {
        for (int i = 0; i < parent.length; i++) {
            parent[i] = -1;
        }
        int head = 0;
        int tail = 0;
        parent[source] = source;
        queue[tail++] = source;
        while (head < tail) {
            int from = queue[head++];
            for (int to = 0; to < residual.length; to++) {
                if (parent[to] < 0 && residual[from][to] > 0L) {
                    parent[to] = from;
                    if (to == sink) {
                        return true;
                    }
                    queue[tail++] = to;
                }
            }
        }
        return false;
    }

    private static boolean[] reachableInResidual(long[][] residual, int source, int[] queue) {
        boolean[] reachable = new boolean[residual.length];
        int head = 0;
        int tail = 0;
        reachable[source] = true;
        queue[tail++] = source;
        while (head < tail) {
            int from = queue[head++];
            for (int to = 0; to < residual.length; to++) {
                if (!reachable[to] && residual[from][to] > 0L) {
                    reachable[to] = true;
                    queue[tail++] = to;
                }
            }
        }
        return reachable;
    }

    private void checkVertex(int vertex) {
        if (vertex < 0 || vertex >= capacity.length) {
            throw new IndexOutOfBoundsException("Vertex index: " + vertex);
        }
    }

    private static long[][] copy(long[][] source) {
        long[][] result = new long[source.length][source.length];
        for (int row = 0; row < source.length; row++) {
            for (int column = 0; column < source.length; column++) {
                result[row][column] = source[row][column];
            }
        }
        return result;
    }

    public static final class Result {
        private final long value;
        private final long[][] netFlow;
        private final long[][] residual;
        private final boolean[] sourceSide;
        private final long[][] originalCapacity;

        private Result(long value, long[][] netFlow, long[][] residual,
                boolean[] sourceSide, long[][] originalCapacity) {
            this.value = value;
            this.netFlow = netFlow;
            this.residual = residual;
            this.sourceSide = sourceSide;
            this.originalCapacity = originalCapacity;
        }

        public long value() {
            return value;
        }

        /**
         * Net flow on an ordered pair. It may be negative when the realized
         * flow is in the opposite direction.
         */
        public long netFlow(int from, int to) {
            check(from);
            check(to);
            return netFlow[from][to];
        }

        public long residualCapacity(int from, int to) {
            check(from);
            check(to);
            return residual[from][to];
        }

        public boolean isOnSourceSideOfMinCut(int vertex) {
            check(vertex);
            return sourceSide[vertex];
        }

        public long minCutCapacity() {
            long sum = 0L;
            for (int from = 0; from < sourceSide.length; from++) {
                if (!sourceSide[from]) {
                    continue;
                }
                for (int to = 0; to < sourceSide.length; to++) {
                    if (!sourceSide[to]) {
                        if (Long.MAX_VALUE - sum < originalCapacity[from][to]) {
                            throw new ArithmeticException("Cut-capacity overflow");
                        }
                        sum += originalCapacity[from][to];
                    }
                }
            }
            return sum;
        }

        private void check(int vertex) {
            if (vertex < 0 || vertex >= sourceSide.length) {
                throw new IndexOutOfBoundsException("Vertex index: " + vertex);
            }
        }
    }

    public static final class AssignmentResult {
        private final int[] reviewerForCase;
        private final int[] reviewerLoads;
        private final int assignedCount;

        private AssignmentResult(int[] reviewerForCase, int[] reviewerLoads, int assignedCount) {
            this.reviewerForCase = reviewerForCase;
            this.reviewerLoads = reviewerLoads;
            this.assignedCount = assignedCount;
        }

        public int caseCount() {
            return reviewerForCase.length;
        }

        public int assignedCount() {
            return assignedCount;
        }

        /** Returns -1 when the case could not be assigned. */
        public int reviewerForCase(int caseIndex) {
            if (caseIndex < 0 || caseIndex >= reviewerForCase.length) {
                throw new IndexOutOfBoundsException("Case index: " + caseIndex);
            }
            return reviewerForCase[caseIndex];
        }

        public int reviewerLoad(int reviewer) {
            if (reviewer < 0 || reviewer >= reviewerLoads.length) {
                throw new IndexOutOfBoundsException("Reviewer index: " + reviewer);
            }
            return reviewerLoads[reviewer];
        }

        public int[] assignments() {
            int[] result = new int[reviewerForCase.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = reviewerForCase[i];
            }
            return result;
        }
    }
}
