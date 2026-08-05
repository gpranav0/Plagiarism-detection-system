package edu.academic.integrity.algorithms.graph;

import edu.academic.integrity.structures.MinHeap;

/** Shortest-path algorithms with array-backed result objects. */
public final class ShortestPaths {
    private static final double POSITIVE_INFINITY = Double.POSITIVE_INFINITY;
    private static final double NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY;

    private ShortestPaths() {
    }

    /** Dijkstra's O((V+E) log E) implementation using the custom minimum heap. */
    public static SingleSourceResult dijkstra(WeightedGraph<?> graph, int source) {
        validateGraphAndSource(graph, source);
        rejectNegativeEdges(graph);
        int n = graph.vertexCount();
        double[] distance = new double[n];
        int[] predecessor = new int[n];
        boolean[] settled = new boolean[n];
        initialize(distance, predecessor);
        distance[source] = 0.0;
        MinHeap<DijkstraEntry> frontier = new MinHeap<>();
        frontier.add(new DijkstraEntry(source, 0.0));

        while (!frontier.isEmpty()) {
            DijkstraEntry entry = frontier.poll();
            int current = entry.vertex;
            if (settled[current] || Double.compare(entry.distance, distance[current]) != 0) continue;
            settled[current] = true;
            for (WeightedGraph.Edge edge = graph.firstEdge(current);
                    edge != null; edge = edge.next()) {
                if (settled[edge.to()]) {
                    continue;
                }
                double candidate = distance[current] + edge.weight();
                if (candidate < distance[edge.to()]) {
                    distance[edge.to()] = candidate;
                    predecessor[edge.to()] = current;
                    frontier.add(new DijkstraEntry(edge.to(), candidate));
                }
            }
        }
        return new SingleSourceResult(source, distance, predecessor, new boolean[n]);
    }

    /** Bellman-Ford with detection and propagation of reachable negative cycles. */
    public static SingleSourceResult bellmanFord(WeightedGraph<?> graph, int source) {
        validateGraphAndSource(graph, source);
        int n = graph.vertexCount();
        Arc[] arcs = extractArcs(graph);
        double[] distance = new double[n];
        int[] predecessor = new int[n];
        initialize(distance, predecessor);
        distance[source] = 0.0;

        for (int pass = 1; pass < n; pass++) {
            boolean changed = false;
            for (int i = 0; i < arcs.length; i++) {
                Arc arc = arcs[i];
                if (distance[arc.from] != POSITIVE_INFINITY
                        && distance[arc.from] + arc.weight < distance[arc.to]) {
                    distance[arc.to] = distance[arc.from] + arc.weight;
                    predecessor[arc.to] = arc.from;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        boolean[] negative = new boolean[n];
        for (int i = 0; i < arcs.length; i++) {
            Arc arc = arcs[i];
            if (distance[arc.from] != POSITIVE_INFINITY
                    && distance[arc.from] + arc.weight < distance[arc.to]) {
                negative[arc.to] = true;
            }
        }
        // Any vertex reachable from an affected vertex also has no finite minimum.
        for (int pass = 0; pass < n; pass++) {
            boolean changed = false;
            for (int i = 0; i < arcs.length; i++) {
                Arc arc = arcs[i];
                if (negative[arc.from] && !negative[arc.to]) {
                    negative[arc.to] = true;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        for (int vertex = 0; vertex < n; vertex++) {
            if (negative[vertex]) {
                distance[vertex] = NEGATIVE_INFINITY;
                predecessor[vertex] = -1;
            }
        }
        return new SingleSourceResult(source, distance, predecessor, negative);
    }

    /** Floyd-Warshall all-pairs shortest paths in O(V^3) time and O(V^2) space. */
    public static AllPairsResult floydWarshall(WeightedGraph<?> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        int n = graph.vertexCount();
        double[][] distance = new double[n][n];
        int[][] next = new int[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                distance[from][to] = from == to ? 0.0 : POSITIVE_INFINITY;
                next[from][to] = -1;
            }
            next[from][from] = from;
            for (WeightedGraph.Edge edge = graph.firstEdge(from);
                    edge != null; edge = edge.next()) {
                if (edge.weight() < distance[from][edge.to()]) {
                    distance[from][edge.to()] = edge.weight();
                    next[from][edge.to()] = edge.to();
                }
            }
        }

        for (int via = 0; via < n; via++) {
            for (int from = 0; from < n; from++) {
                if (distance[from][via] == POSITIVE_INFINITY) {
                    continue;
                }
                for (int to = 0; to < n; to++) {
                    if (distance[via][to] == POSITIVE_INFINITY) {
                        continue;
                    }
                    double candidate = distance[from][via] + distance[via][to];
                    if (candidate < distance[from][to]) {
                        distance[from][to] = candidate;
                        next[from][to] = next[from][via];
                    }
                }
            }
        }

        boolean[][] negativelyUnbounded = new boolean[n][n];
        boolean hasNegativeCycle = false;
        for (int cycle = 0; cycle < n; cycle++) {
            if (distance[cycle][cycle] < 0.0) {
                hasNegativeCycle = true;
                for (int from = 0; from < n; from++) {
                    if (distance[from][cycle] == POSITIVE_INFINITY) {
                        continue;
                    }
                    for (int to = 0; to < n; to++) {
                        if (distance[cycle][to] != POSITIVE_INFINITY) {
                            negativelyUnbounded[from][to] = true;
                        }
                    }
                }
            }
        }
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                if (negativelyUnbounded[from][to]) {
                    distance[from][to] = NEGATIVE_INFINITY;
                    next[from][to] = -1;
                }
            }
        }
        return new AllPairsResult(distance, next, negativelyUnbounded, hasNegativeCycle);
    }

    private static void rejectNegativeEdges(WeightedGraph<?> graph) {
        for (int vertex = 0; vertex < graph.vertexCount(); vertex++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(vertex);
                    edge != null; edge = edge.next()) {
                if (edge.weight() < 0.0) {
                    throw new IllegalArgumentException("Dijkstra's algorithm requires non-negative weights");
                }
            }
        }
    }

    private static Arc[] extractArcs(WeightedGraph<?> graph) {
        int count = 0;
        for (int vertex = 0; vertex < graph.vertexCount(); vertex++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(vertex);
                    edge != null; edge = edge.next()) {
                count++;
            }
        }
        Arc[] result = new Arc[count];
        int output = 0;
        for (int vertex = 0; vertex < graph.vertexCount(); vertex++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(vertex);
                    edge != null; edge = edge.next()) {
                result[output++] = new Arc(edge.from(), edge.to(), edge.weight());
            }
        }
        return result;
    }

    private static void initialize(double[] distance, int[] predecessor) {
        for (int i = 0; i < distance.length; i++) {
            distance[i] = POSITIVE_INFINITY;
            predecessor[i] = -1;
        }
    }

    private static void validateGraphAndSource(WeightedGraph<?> graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (source < 0 || source >= graph.vertexCount()) {
            throw new IndexOutOfBoundsException("Source vertex: " + source);
        }
    }

    public static final class SingleSourceResult {
        private final int source;
        private final double[] distance;
        private final int[] predecessor;
        private final boolean[] negativelyUnbounded;

        private SingleSourceResult(int source, double[] distance, int[] predecessor,
                boolean[] negativelyUnbounded) {
            this.source = source;
            this.distance = distance;
            this.predecessor = predecessor;
            this.negativelyUnbounded = negativelyUnbounded;
        }

        public int source() {
            return source;
        }

        public int vertexCount() {
            return distance.length;
        }

        public double distanceTo(int vertex) {
            checkVertex(vertex, distance.length);
            return distance[vertex];
        }

        public int predecessorOf(int vertex) {
            checkVertex(vertex, distance.length);
            return predecessor[vertex];
        }

        public boolean isReachable(int vertex) {
            checkVertex(vertex, distance.length);
            return distance[vertex] != POSITIVE_INFINITY;
        }

        public boolean isNegativelyUnbounded(int vertex) {
            checkVertex(vertex, distance.length);
            return negativelyUnbounded[vertex];
        }

        public boolean hasReachableNegativeCycle() {
            for (int i = 0; i < negativelyUnbounded.length; i++) {
                if (negativelyUnbounded[i]) {
                    return true;
                }
            }
            return false;
        }

        public int[] pathTo(int target) {
            checkVertex(target, distance.length);
            if (negativelyUnbounded[target]) {
                throw new IllegalStateException("No finite shortest path: target is affected by a negative cycle");
            }
            if (distance[target] == POSITIVE_INFINITY) {
                return new int[0];
            }
            int[] reverse = new int[distance.length + 1];
            int count = 0;
            int current = target;
            while (current >= 0 && count <= distance.length) {
                reverse[count++] = current;
                if (current == source) {
                    break;
                }
                current = predecessor[current];
            }
            if (count > distance.length || reverse[count - 1] != source) {
                throw new IllegalStateException("Predecessor chain is inconsistent");
            }
            int[] path = new int[count];
            for (int i = 0; i < count; i++) {
                path[i] = reverse[count - 1 - i];
            }
            return path;
        }
    }

    public static final class AllPairsResult {
        private final double[][] distance;
        private final int[][] next;
        private final boolean[][] negativelyUnbounded;
        private final boolean hasNegativeCycle;

        private AllPairsResult(double[][] distance, int[][] next,
                boolean[][] negativelyUnbounded, boolean hasNegativeCycle) {
            this.distance = distance;
            this.next = next;
            this.negativelyUnbounded = negativelyUnbounded;
            this.hasNegativeCycle = hasNegativeCycle;
        }

        public int vertexCount() {
            return distance.length;
        }

        public double distance(int from, int to) {
            checkVertex(from, distance.length);
            checkVertex(to, distance.length);
            return distance[from][to];
        }

        public boolean hasNegativeCycle() {
            return hasNegativeCycle;
        }

        public boolean isNegativelyUnbounded(int from, int to) {
            checkVertex(from, distance.length);
            checkVertex(to, distance.length);
            return negativelyUnbounded[from][to];
        }

        public int[] path(int from, int to) {
            checkVertex(from, distance.length);
            checkVertex(to, distance.length);
            if (negativelyUnbounded[from][to]) {
                throw new IllegalStateException("No finite shortest path: pair is affected by a negative cycle");
            }
            if (next[from][to] < 0) {
                return new int[0];
            }
            int[] temporary = new int[distance.length + 1];
            int count = 0;
            int current = from;
            temporary[count++] = current;
            while (current != to && count <= distance.length) {
                current = next[current][to];
                if (current < 0) {
                    throw new IllegalStateException("Path matrix is inconsistent");
                }
                temporary[count++] = current;
            }
            if (current != to) {
                throw new IllegalStateException("Path matrix contains a cycle");
            }
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = temporary[i];
            }
            return result;
        }
    }

    private static void checkVertex(int vertex, int count) {
        if (vertex < 0 || vertex >= count) {
            throw new IndexOutOfBoundsException("Vertex index: " + vertex);
        }
    }

    private static final class Arc {
        private final int from;
        private final int to;
        private final double weight;

        private Arc(int from, int to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    private static final class DijkstraEntry implements Comparable<DijkstraEntry> {
        private final int vertex;
        private final double distance;

        private DijkstraEntry(int vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        @Override
        public int compareTo(DijkstraEntry other) {
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0) return byDistance;
            return Integer.compare(vertex, other.vertex);
        }
    }
}
