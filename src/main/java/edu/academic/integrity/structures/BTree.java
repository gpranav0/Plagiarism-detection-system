package edu.academic.integrity.structures;

/** In-memory B-tree with unique keys and complete CLRS-style deletion. */
public final class BTree<T> {
    private final int minimumDegree;
    private final Ordering<? super T> ordering;
    private Node<T> root;
    private int size;

    public BTree(int minimumDegree) {
        this(minimumDegree, null);
    }

    public BTree(int minimumDegree, Ordering<? super T> ordering) {
        if (minimumDegree < 2) {
            throw new IllegalArgumentException("minimumDegree must be at least 2");
        }
        this.minimumDegree = minimumDegree;
        this.ordering = ordering;
    }

    public boolean insert(T value) {
        requireValue(value);
        if (contains(value)) return false;
        if (root == null) {
            root = new Node<>(minimumDegree, true);
            root.keys[0] = value;
            root.keyCount = 1;
            size = 1;
            return true;
        }
        if (root.keyCount == maxKeys()) {
            Node<T> newRoot = new Node<>(minimumDegree, false);
            newRoot.children[0] = root;
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, value);
        size++;
        return true;
    }

    public boolean contains(T value) {
        return find(value) != null;
    }

    public T find(T probe) {
        requireValue(probe);
        Node<T> node = root;
        while (node != null) {
            int index = firstNotLess(node, probe);
            if (index < node.keyCount && compare(probe, key(node, index)) == 0) {
                return key(node, index);
            }
            if (node.leaf) return null;
            node = node.children[index];
        }
        return null;
    }

    public boolean remove(T value) {
        requireValue(value);
        if (root == null || !contains(value)) return false;
        remove(root, value);
        size--;
        if (root.keyCount == 0) {
            root = root.leaf ? null : root.children[0];
        }
        return true;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int minimumDegree() { return minimumDegree; }

    public int height() {
        int height = 0;
        Node<T> node = root;
        while (node != null) {
            height++;
            node = node.leaf ? null : node.children[0];
        }
        return height;
    }

    public void clear() {
        root = null;
        size = 0;
    }

    public DynamicArray<T> inOrder() {
        DynamicArray<T> output = new DynamicArray<>(size);
        traverse(root, output);
        return output;
    }

    /** Validates occupancy, ordering, equal leaf depth, child layout, and total key count. */
    public boolean validateInvariant() {
        if (root == null) return size == 0;
        Validation state = new Validation();
        return validate(root, true, null, null, 0, state) && state.keyCount == size;
    }

    private void insertNonFull(Node<T> node, T value) {
        int index = node.keyCount - 1;
        if (node.leaf) {
            while (index >= 0 && compare(value, key(node, index)) < 0) {
                node.keys[index + 1] = node.keys[index];
                index--;
            }
            node.keys[index + 1] = value;
            node.keyCount++;
            return;
        }
        while (index >= 0 && compare(value, key(node, index)) < 0) index--;
        index++;
        if (node.children[index].keyCount == maxKeys()) {
            splitChild(node, index);
            if (compare(value, key(node, index)) > 0) index++;
        }
        insertNonFull(node.children[index], value);
    }

    private void splitChild(Node<T> parent, int childIndex) {
        Node<T> full = parent.children[childIndex];
        Node<T> right = new Node<>(minimumDegree, full.leaf);
        right.keyCount = minimumDegree - 1;
        for (int i = 0; i < minimumDegree - 1; i++) {
            right.keys[i] = full.keys[i + minimumDegree];
            full.keys[i + minimumDegree] = null;
        }
        if (!full.leaf) {
            for (int i = 0; i < minimumDegree; i++) {
                right.children[i] = full.children[i + minimumDegree];
                full.children[i + minimumDegree] = null;
            }
        }
        Object promoted = full.keys[minimumDegree - 1];
        full.keys[minimumDegree - 1] = null;
        full.keyCount = minimumDegree - 1;

        for (int i = parent.keyCount; i >= childIndex + 1; i--) {
            parent.children[i + 1] = parent.children[i];
        }
        parent.children[childIndex + 1] = right;
        for (int i = parent.keyCount - 1; i >= childIndex; i--) {
            parent.keys[i + 1] = parent.keys[i];
        }
        parent.keys[childIndex] = promoted;
        parent.keyCount++;
    }

    private void remove(Node<T> node, T value) {
        int index = firstNotLess(node, value);
        if (index < node.keyCount && compare(value, key(node, index)) == 0) {
            if (node.leaf) removeFromLeaf(node, index);
            else removeFromInternal(node, index);
            return;
        }
        if (node.leaf) return;
        boolean wasLastChild = index == node.keyCount;
        if (node.children[index].keyCount < minimumDegree) fill(node, index);
        if (wasLastChild && index > node.keyCount) remove(node.children[index - 1], value);
        else remove(node.children[index], value);
    }

    private void removeFromLeaf(Node<T> node, int index) {
        for (int i = index + 1; i < node.keyCount; i++) node.keys[i - 1] = node.keys[i];
        node.keys[--node.keyCount] = null;
    }

    private void removeFromInternal(Node<T> node, int index) {
        T target = key(node, index);
        if (node.children[index].keyCount >= minimumDegree) {
            T predecessor = greatest(node.children[index]);
            node.keys[index] = predecessor;
            remove(node.children[index], predecessor);
        } else if (node.children[index + 1].keyCount >= minimumDegree) {
            T successor = least(node.children[index + 1]);
            node.keys[index] = successor;
            remove(node.children[index + 1], successor);
        } else {
            merge(node, index);
            remove(node.children[index], target);
        }
    }

    private void fill(Node<T> parent, int childIndex) {
        if (childIndex > 0 && parent.children[childIndex - 1].keyCount >= minimumDegree) {
            borrowFromPrevious(parent, childIndex);
        } else if (childIndex < parent.keyCount
                && parent.children[childIndex + 1].keyCount >= minimumDegree) {
            borrowFromNext(parent, childIndex);
        } else if (childIndex < parent.keyCount) {
            merge(parent, childIndex);
        } else {
            merge(parent, childIndex - 1);
        }
    }

    private void borrowFromPrevious(Node<T> parent, int childIndex) {
        Node<T> child = parent.children[childIndex];
        Node<T> sibling = parent.children[childIndex - 1];
        for (int i = child.keyCount - 1; i >= 0; i--) child.keys[i + 1] = child.keys[i];
        if (!child.leaf) {
            for (int i = child.keyCount; i >= 0; i--) child.children[i + 1] = child.children[i];
        }
        child.keys[0] = parent.keys[childIndex - 1];
        if (!child.leaf) {
            child.children[0] = sibling.children[sibling.keyCount];
            sibling.children[sibling.keyCount] = null;
        }
        parent.keys[childIndex - 1] = sibling.keys[sibling.keyCount - 1];
        sibling.keys[sibling.keyCount - 1] = null;
        sibling.keyCount--;
        child.keyCount++;
    }

    private void borrowFromNext(Node<T> parent, int childIndex) {
        Node<T> child = parent.children[childIndex];
        Node<T> sibling = parent.children[childIndex + 1];
        child.keys[child.keyCount] = parent.keys[childIndex];
        if (!child.leaf) child.children[child.keyCount + 1] = sibling.children[0];
        parent.keys[childIndex] = sibling.keys[0];
        for (int i = 1; i < sibling.keyCount; i++) sibling.keys[i - 1] = sibling.keys[i];
        sibling.keys[sibling.keyCount - 1] = null;
        if (!sibling.leaf) {
            for (int i = 1; i <= sibling.keyCount; i++) sibling.children[i - 1] = sibling.children[i];
            sibling.children[sibling.keyCount] = null;
        }
        sibling.keyCount--;
        child.keyCount++;
    }

    private void merge(Node<T> parent, int index) {
        Node<T> left = parent.children[index];
        Node<T> right = parent.children[index + 1];
        left.keys[minimumDegree - 1] = parent.keys[index];
        for (int i = 0; i < right.keyCount; i++) {
            left.keys[i + minimumDegree] = right.keys[i];
        }
        if (!left.leaf) {
            for (int i = 0; i <= right.keyCount; i++) {
                left.children[i + minimumDegree] = right.children[i];
            }
        }
        left.keyCount += right.keyCount + 1;
        for (int i = index + 1; i < parent.keyCount; i++) parent.keys[i - 1] = parent.keys[i];
        parent.keys[parent.keyCount - 1] = null;
        for (int i = index + 2; i <= parent.keyCount; i++) parent.children[i - 1] = parent.children[i];
        parent.children[parent.keyCount] = null;
        parent.keyCount--;
    }

    private T greatest(Node<T> node) {
        while (!node.leaf) node = node.children[node.keyCount];
        return key(node, node.keyCount - 1);
    }

    private T least(Node<T> node) {
        while (!node.leaf) node = node.children[0];
        return key(node, 0);
    }

    private int firstNotLess(Node<T> node, T value) {
        int index = 0;
        while (index < node.keyCount && compare(key(node, index), value) < 0) index++;
        return index;
    }

    private void traverse(Node<T> node, DynamicArray<T> output) {
        if (node == null) return;
        for (int i = 0; i < node.keyCount; i++) {
            if (!node.leaf) traverse(node.children[i], output);
            output.add(key(node, i));
        }
        if (!node.leaf) traverse(node.children[node.keyCount], output);
    }

    private boolean validate(Node<T> node, boolean isRoot, T lower, T upper,
            int depth, Validation state) {
        int minimum = isRoot ? 1 : minimumDegree - 1;
        if (node.keyCount < minimum || node.keyCount > maxKeys()) return false;
        for (int i = 0; i < node.keyCount; i++) {
            T current = key(node, i);
            if (i > 0 && compare(key(node, i - 1), current) >= 0) return false;
            if ((lower != null && compare(current, lower) <= 0)
                    || (upper != null && compare(current, upper) >= 0)) return false;
        }
        state.keyCount += node.keyCount;
        if (node.leaf) {
            if (state.leafDepth < 0) state.leafDepth = depth;
            return state.leafDepth == depth;
        }
        for (int i = 0; i <= node.keyCount; i++) {
            if (node.children[i] == null) return false;
            T childLower = i == 0 ? lower : key(node, i - 1);
            T childUpper = i == node.keyCount ? upper : key(node, i);
            if (!validate(node.children[i], false, childLower, childUpper, depth + 1, state)) return false;
        }
        return true;
    }

    private int maxKeys() { return minimumDegree * 2 - 1; }

    @SuppressWarnings("unchecked")
    private static <T> T key(Node<T> node, int index) { return (T) node.keys[index]; }

    @SuppressWarnings("unchecked")
    private int compare(T left, T right) {
        if (ordering != null) return ordering.compare(left, right);
        if (!(left instanceof Comparable<?>)) {
            throw new IllegalStateException("values must implement Comparable when no Ordering is supplied");
        }
        return ((Comparable<? super T>) left).compareTo(right);
    }

    private static void requireValue(Object value) {
        if (value == null) throw new IllegalArgumentException("tree does not accept null values");
    }

    private static final class Node<T> {
        private final Object[] keys;
        private final Node<T>[] children;
        private final boolean leaf;
        private int keyCount;

        @SuppressWarnings("unchecked")
        private Node(int degree, boolean leaf) {
            keys = new Object[degree * 2 - 1];
            children = (Node<T>[]) new Node[degree * 2];
            this.leaf = leaf;
        }
    }

    private static final class Validation {
        private int keyCount;
        private int leafDepth = -1;
    }
}
