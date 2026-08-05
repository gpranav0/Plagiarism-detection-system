package edu.academic.integrity.structures;

/** Union-find with path compression and union by component size. */
public final class DisjointSet {
    private final int[] parents;
    private final int[] sizes;
    private int componentCount;

    public DisjointSet(int elementCount) {
        if (elementCount < 0) throw new IllegalArgumentException("elementCount must be non-negative");
        parents = new int[elementCount];
        sizes = new int[elementCount];
        reset();
    }

    public int size() { return parents.length; }
    public int componentCount() { return componentCount; }

    public int find(int element) {
        checkElement(element);
        int root = element;
        while (root != parents[root]) root = parents[root];
        while (element != root) {
            int next = parents[element];
            parents[element] = root;
            element = next;
        }
        return root;
    }

    public boolean union(int first, int second) {
        int firstRoot = find(first);
        int secondRoot = find(second);
        if (firstRoot == secondRoot) return false;
        if (sizes[firstRoot] < sizes[secondRoot]) {
            int temporary = firstRoot;
            firstRoot = secondRoot;
            secondRoot = temporary;
        }
        parents[secondRoot] = firstRoot;
        sizes[firstRoot] += sizes[secondRoot];
        sizes[secondRoot] = 0;
        componentCount--;
        return true;
    }

    public boolean connected(int first, int second) {
        return find(first) == find(second);
    }

    public int componentSize(int element) {
        return sizes[find(element)];
    }

    public void reset() {
        componentCount = parents.length;
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
            sizes[i] = 1;
        }
    }

    public boolean validateInvariant() {
        int roots = 0;
        int total = 0;
        for (int i = 0; i < parents.length; i++) {
            if (parents[i] < 0 || parents[i] >= parents.length) return false;
            int cursor = i;
            int steps = 0;
            while (parents[cursor] != cursor) {
                cursor = parents[cursor];
                if (++steps > parents.length) return false;
            }
            if (parents[i] == i) {
                if (sizes[i] <= 0) return false;
                roots++;
                total += sizes[i];
            } else if (sizes[i] != 0) {
                return false;
            }
        }
        if (roots != componentCount || total != parents.length) return false;
        for (int root = 0; root < parents.length; root++) {
            if (parents[root] != root) continue;
            int counted = 0;
            for (int i = 0; i < parents.length; i++) if (findWithoutCompression(i) == root) counted++;
            if (counted != sizes[root]) return false;
        }
        return true;
    }

    private int findWithoutCompression(int element) {
        while (parents[element] != element) element = parents[element];
        return element;
    }

    private void checkElement(int element) {
        if (element < 0 || element >= parents.length) {
            throw new IndexOutOfBoundsException("element=" + element + ", size=" + parents.length);
        }
    }
}
