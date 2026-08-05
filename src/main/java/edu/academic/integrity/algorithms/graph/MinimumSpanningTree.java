package edu.academic.integrity.algorithms.graph;

import edu.academic.integrity.structures.DisjointSet;

/** Minimum-spanning-tree and forest algorithms for undirected graphs. */
public final class MinimumSpanningTree {
    private MinimumSpanningTree() {
    }

    /** Kruskal's algorithm: O(E log E) time and O(V + E) auxiliary space. */
    public static Result kruskal(WeightedGraph<?> graph) {
        requireUndirected(graph);
        int vertices = graph.vertexCount();
        WeightedEdge[] edges = extractUndirectedEdges(graph);
        mergeSort(edges);
        DisjointSet sets = new DisjointSet(vertices);
        WeightedEdge[] selected = new WeightedEdge[vertices == 0 ? 0 : vertices - 1];
        int selectedCount = 0;
        double totalWeight = 0.0;

        for (int i = 0; i < edges.length; i++) {
            WeightedEdge edge = edges[i];
            if (sets.union(edge.from, edge.to)) {
                selected[selectedCount++] = edge;
                totalWeight += edge.weight;
            }
        }
        return new Result(trim(selected, selectedCount), totalWeight, sets.componentCount());
    }

    /**
     * Prim's algorithm using an array minimum selection. It deliberately does
     * not delegate to a library priority queue. Complexity is O(V^2 + E).
     */
    public static Result prim(WeightedGraph<?> graph) {
        requireUndirected(graph);
        int n = graph.vertexCount();
        if (n == 0) {
            return new Result(new WeightedEdge[0], 0.0, 0);
        }

        double[] key = new double[n];
        int[] parent = new int[n];
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            key[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }

        WeightedEdge[] selected = new WeightedEdge[n - 1];
        int selectedCount = 0;
        int componentCount = 0;
        double totalWeight = 0.0;

        for (int completed = 0; completed < n; completed++) {
            int next = minimumUnused(key, used);
            if (next < 0 || key[next] == Double.POSITIVE_INFINITY) {
                next = firstUnused(used);
                componentCount++;
                key[next] = 0.0;
            }

            used[next] = true;
            if (parent[next] >= 0) {
                selected[selectedCount++] = new WeightedEdge(parent[next], next, key[next]);
                totalWeight += key[next];
            }

            for (WeightedGraph.Edge edge = graph.firstEdge(next);
                    edge != null; edge = edge.next()) {
                int to = edge.to();
                double weight = edge.weight();
                if (!used[to] && (weight < key[to]
                        || (Double.compare(weight, key[to]) == 0 && next < parent[to]))) {
                    key[to] = weight;
                    parent[to] = next;
                }
            }
        }
        return new Result(trim(selected, selectedCount), totalWeight, componentCount);
    }

    private static WeightedEdge[] extractUndirectedEdges(WeightedGraph<?> graph) {
        WeightedEdge[] temporary = new WeightedEdge[graph.edgeCount()];
        int count = 0;
        for (int from = 0; from < graph.vertexCount(); from++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(from);
                    edge != null; edge = edge.next()) {
                if (edge.from() < edge.to()) {
                    temporary[count++] = new WeightedEdge(edge.from(), edge.to(), edge.weight());
                }
            }
        }
        return trim(temporary, count);
    }

    private static int minimumUnused(double[] key, boolean[] used) {
        int best = -1;
        for (int i = 0; i < key.length; i++) {
            if (!used[i] && (best < 0 || key[i] < key[best]
                    || (Double.compare(key[i], key[best]) == 0 && i < best))) {
                best = i;
            }
        }
        return best;
    }

    private static int firstUnused(boolean[] used) {
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
    }

    private static void requireUndirected(WeightedGraph<?> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (graph.isDirected()) {
            throw new IllegalArgumentException("A minimum spanning tree requires an undirected graph");
        }
    }

    private static void mergeSort(WeightedEdge[] values) {
        if (values.length < 2) {
            return;
        }
        WeightedEdge[] work = new WeightedEdge[values.length];
        for (int width = 1; width < values.length; width = safeDouble(width, values.length)) {
            for (int left = 0; left < values.length; left += width << 1) {
                int middle = minimum(left + width, values.length);
                int right = minimum(left + (width << 1), values.length);
                merge(values, work, left, middle, right);
            }
            for (int i = 0; i < values.length; i++) {
                values[i] = work[i];
            }
            if (width > values.length / 2) {
                break;
            }
        }
    }

    private static int safeDouble(int width, int length) {
        return width > length / 2 ? length : width << 1;
    }

    private static void merge(WeightedEdge[] source, WeightedEdge[] target,
            int left, int middle, int right) {
        int a = left;
        int b = middle;
        int output = left;
        while (a < middle && b < right) {
            if (compare(source[a], source[b]) <= 0) {
                target[output++] = source[a++];
            } else {
                target[output++] = source[b++];
            }
        }
        while (a < middle) {
            target[output++] = source[a++];
        }
        while (b < right) {
            target[output++] = source[b++];
        }
    }

    private static int compare(WeightedEdge first, WeightedEdge second) {
        int byWeight = Double.compare(first.weight, second.weight);
        if (byWeight != 0) {
            return byWeight;
        }
        if (first.from != second.from) {
            return first.from < second.from ? -1 : 1;
        }
        return first.to == second.to ? 0 : (first.to < second.to ? -1 : 1);
    }

    private static int minimum(int first, int second) {
        return first < second ? first : second;
    }

    private static WeightedEdge[] trim(WeightedEdge[] source, int length) {
        WeightedEdge[] result = new WeightedEdge[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    public static final class WeightedEdge {
        private final int from;
        private final int to;
        private final double weight;

        public WeightedEdge(int from, int to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public int from() {
            return from;
        }

        public int to() {
            return to;
        }

        public double weight() {
            return weight;
        }
    }

    public static final class Result {
        private final WeightedEdge[] edges;
        private final double totalWeight;
        private final int componentCount;

        private Result(WeightedEdge[] edges, double totalWeight, int componentCount) {
            this.edges = edges;
            this.totalWeight = totalWeight;
            this.componentCount = componentCount;
        }

        public int edgeCount() {
            return edges.length;
        }

        public WeightedEdge edgeAt(int index) {
            if (index < 0 || index >= edges.length) {
                throw new IndexOutOfBoundsException("Edge index: " + index);
            }
            return edges[index];
        }

        public WeightedEdge[] edges() {
            return trim(edges, edges.length);
        }

        public double totalWeight() {
            return totalWeight;
        }

        public int componentCount() {
            return componentCount;
        }

        public boolean isSpanningTree() {
            return componentCount <= 1;
        }
    }

}
