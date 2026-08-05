package edu.academic.integrity.algorithms.graph;

import edu.academic.integrity.structures.DisjointSet;
import edu.academic.integrity.structures.LinkedQueue;
import edu.academic.integrity.structures.LinkedStack;

/**
 * A weighted adjacency-list graph backed only by arrays and custom linked
 * edge nodes. Vertex indexes are stable for the lifetime of the graph.
 *
 * @param <V> the application value stored at each vertex
 */
public final class WeightedGraph<V> {
    private static final int DEFAULT_CAPACITY = 8;

    private Object[] vertices;
    private Edge[] adjacency;
    private int vertexCount;
    private int edgeCount;
    private final boolean directed;

    public WeightedGraph(boolean directed) {
        this(directed, DEFAULT_CAPACITY);
    }

    public WeightedGraph(boolean directed, int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("Initial capacity must be positive");
        }
        this.directed = directed;
        vertices = new Object[initialCapacity];
        adjacency = new Edge[initialCapacity];
    }

    public boolean isDirected() {
        return directed;
    }

    public int vertexCount() {
        return vertexCount;
    }

    /** Returns logical edges; an undirected edge is counted once. */
    public int edgeCount() {
        return edgeCount;
    }

    public int addVertex(V value) {
        if (value == null) {
            throw new IllegalArgumentException("Vertex value cannot be null");
        }
        if (indexOf(value) >= 0) {
            throw new IllegalArgumentException("Duplicate vertex value: " + value);
        }
        ensureVertexCapacity(vertexCount + 1);
        vertices[vertexCount] = value;
        return vertexCount++;
    }

    public boolean containsVertex(V value) {
        return indexOf(value) >= 0;
    }

    public int indexOf(V value) {
        if (value == null) {
            return -1;
        }
        for (int i = 0; i < vertexCount; i++) {
            if (value.equals(vertices[i])) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public V vertexAt(int index) {
        checkVertex(index);
        return (V) vertices[index];
    }

    /**
     * Adds an edge or updates the weight of an existing edge. Weights must be
     * finite; individual algorithms impose any additional sign restrictions.
     */
    public void addEdge(int from, int to, double weight) {
        checkVertex(from);
        checkVertex(to);
        checkFinite(weight);

        Edge existing = findEdge(from, to);
        if (existing != null) {
            existing.weight = weight;
            if (!directed && from != to) {
                Edge reverse = findEdge(to, from);
                if (reverse == null) {
                    throw new IllegalStateException("Undirected edge symmetry was corrupted");
                }
                reverse.weight = weight;
            }
            return;
        }

        adjacency[from] = new Edge(from, to, weight, adjacency[from]);
        if (!directed && from != to) {
            adjacency[to] = new Edge(to, from, weight, adjacency[to]);
        }
        edgeCount++;
    }

    public void addEdge(V from, V to, double weight) {
        int fromIndex = indexOf(from);
        int toIndex = indexOf(to);
        if (fromIndex < 0 || toIndex < 0) {
            throw new IllegalArgumentException("Both vertices must already exist");
        }
        addEdge(fromIndex, toIndex, weight);
    }

    public boolean removeEdge(int from, int to) {
        checkVertex(from);
        checkVertex(to);
        boolean removed = unlink(from, to);
        if (!removed) {
            return false;
        }
        if (!directed && from != to && !unlink(to, from)) {
            throw new IllegalStateException("Undirected edge symmetry was corrupted");
        }
        edgeCount--;
        return true;
    }

    public boolean hasEdge(int from, int to) {
        checkVertex(from);
        checkVertex(to);
        return findEdge(from, to) != null;
    }

    public double weight(int from, int to) {
        checkVertex(from);
        checkVertex(to);
        Edge edge = findEdge(from, to);
        return edge == null ? Double.POSITIVE_INFINITY : edge.weight;
    }

    /** Returns the first immutable-view edge node in a vertex adjacency list. */
    public Edge firstEdge(int vertex) {
        checkVertex(vertex);
        return adjacency[vertex];
    }

    public Traversal<V> breadthFirst(int start) {
        checkVertex(start);
        boolean[] visited = new boolean[vertexCount];
        LinkedQueue<Integer> queue = new LinkedQueue<>();
        int[] order = new int[vertexCount];
        int count = 0;
        visited[start] = true;
        queue.offer(start);

        while (!queue.isEmpty()) {
            int vertex = queue.poll();
            order[count++] = vertex;
            for (Edge edge = adjacency[vertex]; edge != null; edge = edge.next) {
                if (!visited[edge.to]) {
                    visited[edge.to] = true;
                    queue.offer(edge.to);
                }
            }
        }
        return new Traversal<V>(this, trim(order, count));
    }

    public Traversal<V> depthFirst(int start) {
        checkVertex(start);
        boolean[] visited = new boolean[vertexCount];
        LinkedStack<Integer> stack = new LinkedStack<>();
        int[] order = new int[vertexCount];
        int count = 0;
        stack.push(start);
        visited[start] = true;

        while (!stack.isEmpty()) {
            int vertex = stack.pop();
            order[count++] = vertex;
            for (Edge edge = adjacency[vertex]; edge != null; edge = edge.next) {
                if (!visited[edge.to]) {
                    visited[edge.to] = true;
                    stack.push(edge.to);
                }
            }
        }
        return new Traversal<V>(this, trim(order, count));
    }

    /**
     * Finds connected components. For directed graphs these are weakly
     * connected components, which is the useful grouping interpretation for
     * a directed evidence graph.
     */
    public Components connectedComponents() {
        boolean[] visited = new boolean[vertexCount];
        int[][] temporary = new int[vertexCount][];
        int componentCount = 0;
        LinkedQueue<Integer> queue = new LinkedQueue<>();

        for (int start = 0; start < vertexCount; start++) {
            if (visited[start]) {
                continue;
            }
            int[] members = new int[vertexCount];
            int memberCount = 0;
            visited[start] = true;
            queue.offer(start);

            while (!queue.isEmpty()) {
                int vertex = queue.poll();
                members[memberCount++] = vertex;
                for (Edge edge = adjacency[vertex]; edge != null; edge = edge.next) {
                    if (!visited[edge.to]) {
                        visited[edge.to] = true;
                        queue.offer(edge.to);
                    }
                }
                if (directed) {
                    // Incoming arcs are also adjacency in the weak projection.
                    for (int candidate = 0; candidate < vertexCount; candidate++) {
                        if (!visited[candidate] && findEdge(candidate, vertex) != null) {
                            visited[candidate] = true;
                            queue.offer(candidate);
                        }
                    }
                }
            }
            temporary[componentCount++] = trim(members, memberCount);
        }

        int[][] result = new int[componentCount][];
        for (int i = 0; i < componentCount; i++) {
            result[i] = temporary[i];
        }
        return new Components(result);
    }

    /**
     * Groups the weak graph projection with the reusable union-find structure.
     * This is used by the plagiarism network so grouping and compact-link
     * construction share the same disjoint-set semantics.
     */
    public Components unionFindComponents() {
        DisjointSet sets = new DisjointSet(vertexCount);
        for (int from = 0; from < vertexCount; from++) {
            for (Edge edge = adjacency[from]; edge != null; edge = edge.next) {
                sets.union(from, edge.to);
            }
        }

        int[] componentForRoot = new int[vertexCount];
        for (int i = 0; i < componentForRoot.length; i++) componentForRoot[i] = -1;
        int componentCount = 0;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int root = sets.find(vertex);
            if (componentForRoot[root] < 0) componentForRoot[root] = componentCount++;
        }

        int[] sizes = new int[componentCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            sizes[componentForRoot[sets.find(vertex)]]++;
        }
        int[][] components = new int[componentCount][];
        int[] positions = new int[componentCount];
        for (int component = 0; component < componentCount; component++) {
            components[component] = new int[sizes[component]];
        }
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int component = componentForRoot[sets.find(vertex)];
            components[component][positions[component]++] = vertex;
        }
        return new Components(components);
    }

    /** Checks adjacency indexes, finite weights, counts, and undirected symmetry. */
    public boolean validateInvariants() {
        int arcs = 0;
        int selfLoops = 0;
        for (int from = 0; from < vertexCount; from++) {
            for (Edge edge = adjacency[from]; edge != null; edge = edge.next) {
                arcs++;
                if (edge.from != from || edge.to < 0 || edge.to >= vertexCount
                        || !Double.isFinite(edge.weight)) {
                    return false;
                }
                if (!directed) {
                    if (from == edge.to) {
                        selfLoops++;
                    } else {
                        Edge reverse = findEdge(edge.to, from);
                        if (reverse == null || Double.compare(reverse.weight, edge.weight) != 0) {
                            return false;
                        }
                    }
                }
            }
        }
        if (directed) {
            return arcs == edgeCount;
        }
        return ((arcs - selfLoops) / 2) + selfLoops == edgeCount;
    }

    private Edge findEdge(int from, int to) {
        for (Edge edge = adjacency[from]; edge != null; edge = edge.next) {
            if (edge.to == to) {
                return edge;
            }
        }
        return null;
    }

    private boolean unlink(int from, int to) {
        Edge previous = null;
        Edge current = adjacency[from];
        while (current != null) {
            if (current.to == to) {
                if (previous == null) {
                    adjacency[from] = current.next;
                } else {
                    previous.next = current.next;
                }
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    private void ensureVertexCapacity(int required) {
        if (required <= vertices.length) {
            return;
        }
        int newCapacity = vertices.length + (vertices.length >> 1) + 1;
        if (newCapacity < required) {
            newCapacity = required;
        }
        Object[] newVertices = new Object[newCapacity];
        Edge[] newAdjacency = new Edge[newCapacity];
        for (int i = 0; i < vertexCount; i++) {
            newVertices[i] = vertices[i];
            newAdjacency[i] = adjacency[i];
        }
        vertices = newVertices;
        adjacency = newAdjacency;
    }

    private void checkVertex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IndexOutOfBoundsException("Vertex index: " + vertex);
        }
    }

    private static void checkFinite(double weight) {
        if (!Double.isFinite(weight)) {
            throw new IllegalArgumentException("Edge weight must be finite");
        }
    }

    private static int[] trim(int[] source, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    public static final class Edge {
        private final int from;
        private final int to;
        private double weight;
        private Edge next;

        private Edge(int from, int to, double weight, Edge next) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.next = next;
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

        public Edge next() {
            return next;
        }
    }

    public static final class Traversal<V> {
        private final WeightedGraph<V> graph;
        private final int[] order;

        private Traversal(WeightedGraph<V> graph, int[] order) {
            this.graph = graph;
            this.order = order;
        }

        public int size() {
            return order.length;
        }

        public int indexAt(int position) {
            checkPosition(position, order.length);
            return order[position];
        }

        public V valueAt(int position) {
            return graph.vertexAt(indexAt(position));
        }

        public int[] indexes() {
            return copy(order);
        }
    }

    public static final class Components {
        private final int[][] components;

        private Components(int[][] components) {
            this.components = components;
        }

        public int count() {
            return components.length;
        }

        public int sizeOf(int component) {
            checkPosition(component, components.length);
            return components[component].length;
        }

        public int vertexAt(int component, int position) {
            checkPosition(component, components.length);
            checkPosition(position, components[component].length);
            return components[component][position];
        }

        public int[] members(int component) {
            checkPosition(component, components.length);
            return copy(components[component]);
        }
    }

    private static int[] copy(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    private static void checkPosition(int position, int length) {
        if (position < 0 || position >= length) {
            throw new IndexOutOfBoundsException("Position: " + position);
        }
    }
}
