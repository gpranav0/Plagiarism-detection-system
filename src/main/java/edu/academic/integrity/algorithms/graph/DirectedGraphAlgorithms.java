package edu.academic.integrity.algorithms.graph;

/** Topological ordering and strongly connected components for directed graphs. */
public final class DirectedGraphAlgorithms {
    private DirectedGraphAlgorithms() {
    }

    /** Kahn's topological sort using a custom array queue. */
    public static TopologicalResult topologicalSort(WeightedGraph<?> graph) {
        requireDirected(graph);
        int n = graph.vertexCount();
        int[] indegree = new int[n];
        for (int from = 0; from < n; from++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(from);
                    edge != null; edge = edge.next()) {
                indegree[edge.to()]++;
            }
        }

        int[] queue = new int[n];
        int head = 0;
        int tail = 0;
        for (int vertex = 0; vertex < n; vertex++) {
            if (indegree[vertex] == 0) {
                queue[tail++] = vertex;
            }
        }

        int[] order = new int[n];
        int count = 0;
        while (head < tail) {
            int vertex = queue[head++];
            order[count++] = vertex;
            for (WeightedGraph.Edge edge = graph.firstEdge(vertex);
                    edge != null; edge = edge.next()) {
                if (--indegree[edge.to()] == 0) {
                    queue[tail++] = edge.to();
                }
            }
        }

        int blockedCount = n - count;
        int[] blocked = new int[blockedCount];
        int output = 0;
        for (int vertex = 0; vertex < n; vertex++) {
            if (indegree[vertex] > 0) {
                blocked[output++] = vertex;
            }
        }
        return new TopologicalResult(trim(order, count), blocked, count == n);
    }

    /** Kosaraju's SCC algorithm, implemented with custom array stacks. */
    public static StrongComponents stronglyConnectedComponents(WeightedGraph<?> graph) {
        requireDirected(graph);
        int n = graph.vertexCount();
        boolean[] visited = new boolean[n];
        int[] finishOrder = new int[n];
        int finishCount = 0;
        int[] vertexStack = new int[n];
        WeightedGraph.Edge[] cursorStack = new WeightedGraph.Edge[n];

        for (int start = 0; start < n; start++) {
            if (visited[start]) {
                continue;
            }
            int top = 0;
            visited[start] = true;
            vertexStack[top] = start;
            cursorStack[top] = graph.firstEdge(start);
            top++;

            while (top > 0) {
                int level = top - 1;
                WeightedGraph.Edge edge = cursorStack[level];
                while (edge != null && visited[edge.to()]) {
                    edge = edge.next();
                }
                if (edge == null) {
                    finishOrder[finishCount++] = vertexStack[level];
                    top--;
                } else {
                    cursorStack[level] = edge.next();
                    int child = edge.to();
                    visited[child] = true;
                    vertexStack[top] = child;
                    cursorStack[top] = graph.firstEdge(child);
                    top++;
                }
            }
        }

        IntNode[] reverse = new IntNode[n];
        for (int from = 0; from < n; from++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(from);
                    edge != null; edge = edge.next()) {
                reverse[edge.to()] = new IntNode(from, reverse[edge.to()]);
            }
        }

        for (int i = 0; i < n; i++) {
            visited[i] = false;
        }
        int[][] temporaryComponents = new int[n][];
        int[] componentOf = new int[n];
        int componentCount = 0;
        int[] stack = new int[n];

        for (int orderIndex = finishCount - 1; orderIndex >= 0; orderIndex--) {
            int start = finishOrder[orderIndex];
            if (visited[start]) {
                continue;
            }
            int top = 0;
            int memberCount = 0;
            int[] members = new int[n];
            visited[start] = true;
            stack[top++] = start;
            while (top > 0) {
                int vertex = stack[--top];
                members[memberCount++] = vertex;
                componentOf[vertex] = componentCount;
                for (IntNode incoming = reverse[vertex]; incoming != null; incoming = incoming.next) {
                    if (!visited[incoming.value]) {
                        visited[incoming.value] = true;
                        stack[top++] = incoming.value;
                    }
                }
            }
            temporaryComponents[componentCount++] = trim(members, memberCount);
        }

        int[][] components = new int[componentCount][];
        for (int i = 0; i < componentCount; i++) {
            components[i] = temporaryComponents[i];
        }
        return new StrongComponents(components, componentOf);
    }

    private static void requireDirected(WeightedGraph<?> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        if (!graph.isDirected()) {
            throw new IllegalArgumentException("This algorithm requires a directed graph");
        }
    }

    private static int[] trim(int[] source, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    public static final class TopologicalResult {
        private final int[] order;
        private final int[] blockedVertices;
        private final boolean acyclic;

        private TopologicalResult(int[] order, int[] blockedVertices, boolean acyclic) {
            this.order = order;
            this.blockedVertices = blockedVertices;
            this.acyclic = acyclic;
        }

        public boolean isAcyclic() {
            return acyclic;
        }

        /** Full order for a DAG, or the maximal Kahn prefix for a cyclic graph. */
        public int[] order() {
            return trim(order, order.length);
        }

        /**
         * Vertices remaining after Kahn's algorithm. This can include vertices
         * downstream of a cycle as well as the cycle itself.
         */
        public int[] blockedVertices() {
            return trim(blockedVertices, blockedVertices.length);
        }
    }

    public static final class StrongComponents {
        private final int[][] components;
        private final int[] componentOf;

        private StrongComponents(int[][] components, int[] componentOf) {
            this.components = components;
            this.componentOf = componentOf;
        }

        public int count() {
            return components.length;
        }

        public int componentOf(int vertex) {
            if (vertex < 0 || vertex >= componentOf.length) {
                throw new IndexOutOfBoundsException("Vertex index: " + vertex);
            }
            return componentOf[vertex];
        }

        public int sizeOf(int component) {
            checkComponent(component);
            return components[component].length;
        }

        public int[] members(int component) {
            checkComponent(component);
            return trim(components[component], components[component].length);
        }

        private void checkComponent(int component) {
            if (component < 0 || component >= components.length) {
                throw new IndexOutOfBoundsException("Component index: " + component);
            }
        }
    }

    private static final class IntNode {
        private final int value;
        private final IntNode next;

        private IntNode(int value, IntNode next) {
            this.value = value;
            this.next = next;
        }
    }
}
