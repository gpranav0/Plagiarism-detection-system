package edu.academic.integrity.structures;

/**
 * B+ tree key/value index. Values live only in linked leaves; internal keys are separators.
 * The {@code order} is the maximum number of children in an internal node.
 */
public final class BPlusTree<K, V> {
    private final int order;
    private final int maxKeys;
    private final Ordering<? super K> ordering;
    private Node<K, V> root;
    private int size;

    public BPlusTree(int order) {
        this(order, null);
    }

    public BPlusTree(int order, Ordering<? super K> ordering) {
        if (order < 3) throw new IllegalArgumentException("order must be at least 3");
        this.order = order;
        this.maxKeys = order - 1;
        this.ordering = ordering;
    }

    /** Inserts a new key, or replaces the value associated with an existing key. */
    public V put(K key, V value) {
        requireEntry(key, value);
        V old = get(key);
        if (old != null) {
            LeafPosition<K, V> position = locate(key);
            position.leaf.values[position.index] = value;
            return old;
        }
        insertNew(key, value);
        return null;
    }

    /** Returns false and replaces the value when the key already exists. */
    public boolean insert(K key, V value) {
        requireEntry(key, value);
        LeafPosition<K, V> existing = locate(key);
        if (existing != null && existing.index < existing.leaf.keyCount
                && compare(key, key(existing.leaf, existing.index)) == 0) {
            existing.leaf.values[existing.index] = value;
            return false;
        }
        insertNew(key, value);
        return true;
    }

    public V get(K key) {
        requireKey(key);
        LeafPosition<K, V> position = locate(key);
        if (position == null || position.index >= position.leaf.keyCount
                || compare(key, key(position.leaf, position.index)) != 0) return null;
        return value(position.leaf, position.index);
    }

    public boolean containsKey(K key) {
        return get(key) != null;
    }

    /**
     * Removes a key and returns its value. Rebuilding after deletion keeps separator and leaf-link
     * repair simple and deterministic; deletion is O(n log n), while lookup/insertion remain O(log n).
     */
    public V remove(K key) {
        requireKey(key);
        V removed = get(key);
        if (removed == null) return null;
        DynamicArray<Entry<K, V>> retained = entries();
        root = null;
        size = 0;
        for (int i = 0; i < retained.size(); i++) {
            Entry<K, V> entry = retained.get(i);
            if (compare(entry.key, key) != 0) insertNew(entry.key, entry.value);
        }
        return removed;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int order() { return order; }

    public int height() {
        int height = 0;
        Node<K, V> node = root;
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

    public DynamicArray<Entry<K, V>> range(K fromInclusive, K toInclusive) {
        requireKey(fromInclusive);
        requireKey(toInclusive);
        DynamicArray<Entry<K, V>> result = new DynamicArray<>();
        if (root == null || compare(fromInclusive, toInclusive) > 0) return result;
        LeafPosition<K, V> position = locate(fromInclusive);
        Node<K, V> leaf = position.leaf;
        int index = position.index;
        while (leaf != null) {
            while (index < leaf.keyCount) {
                K current = key(leaf, index);
                if (compare(current, toInclusive) > 0) return result;
                result.add(new Entry<>(current, value(leaf, index)));
                index++;
            }
            leaf = leaf.next;
            index = 0;
        }
        return result;
    }

    public DynamicArray<Entry<K, V>> entries() {
        DynamicArray<Entry<K, V>> result = new DynamicArray<>(size);
        Node<K, V> leaf = leftmostLeaf();
        while (leaf != null) {
            for (int i = 0; i < leaf.keyCount; i++) {
                result.add(new Entry<>(key(leaf, i), value(leaf, i)));
            }
            leaf = leaf.next;
        }
        return result;
    }

    /** Validates occupancy, separator semantics, sorted keys, leaf depth/linkage, and count. */
    public boolean validateInvariant() {
        if (root == null) return size == 0;
        Validation validation = new Validation();
        if (!validateNode(root, true, null, null, 0, validation)) return false;
        Node<K, V> leaf = leftmostLeaf();
        K previous = null;
        int linkedCount = 0;
        while (leaf != null) {
            if (!leaf.leaf) return false;
            for (int i = 0; i < leaf.keyCount; i++) {
                K current = key(leaf, i);
                if (previous != null && compare(previous, current) >= 0) return false;
                previous = current;
                linkedCount++;
            }
            leaf = leaf.next;
        }
        return linkedCount == size && validation.keyCount == size;
    }

    private void insertNew(K key, V value) {
        if (root == null) {
            root = new Node<>(order, true);
            root.keys[0] = key;
            root.values[0] = value;
            root.keyCount = 1;
            size = 1;
            return;
        }
        Split<K, V> split = insertRecursive(root, key, value);
        if (split != null) {
            Node<K, V> replacement = new Node<>(order, false);
            replacement.keys[0] = split.separator;
            replacement.children[0] = root;
            replacement.children[1] = split.right;
            replacement.keyCount = 1;
            root = replacement;
        }
        size++;
    }

    private Split<K, V> insertRecursive(Node<K, V> node, K insertedKey, V insertedValue) {
        if (node.leaf) {
            int index = lowerBound(node, insertedKey);
            for (int i = node.keyCount; i > index; i--) {
                node.keys[i] = node.keys[i - 1];
                node.values[i] = node.values[i - 1];
            }
            node.keys[index] = insertedKey;
            node.values[index] = insertedValue;
            node.keyCount++;
            return node.keyCount <= maxKeys ? null : splitLeaf(node);
        }

        int childIndex = upperBound(node, insertedKey);
        Split<K, V> childSplit = insertRecursive(node.children[childIndex], insertedKey, insertedValue);
        if (childSplit == null) return null;
        for (int i = node.keyCount; i > childIndex; i--) {
            node.keys[i] = node.keys[i - 1];
            node.children[i + 1] = node.children[i];
        }
        node.keys[childIndex] = childSplit.separator;
        node.children[childIndex + 1] = childSplit.right;
        node.keyCount++;
        return node.keyCount <= maxKeys ? null : splitInternal(node);
    }

    private Split<K, V> splitLeaf(Node<K, V> leaf) {
        int total = leaf.keyCount;
        int leftCount = (total + 1) / 2;
        Node<K, V> right = new Node<>(order, true);
        right.keyCount = total - leftCount;
        for (int i = 0; i < right.keyCount; i++) {
            right.keys[i] = leaf.keys[leftCount + i];
            right.values[i] = leaf.values[leftCount + i];
            leaf.keys[leftCount + i] = null;
            leaf.values[leftCount + i] = null;
        }
        leaf.keyCount = leftCount;
        right.next = leaf.next;
        leaf.next = right;
        return new Split<>(key(right, 0), right);
    }

    private Split<K, V> splitInternal(Node<K, V> node) {
        int total = node.keyCount;
        int middle = total / 2;
        K promoted = key(node, middle);
        Node<K, V> right = new Node<>(order, false);
        right.keyCount = total - middle - 1;
        for (int i = 0; i < right.keyCount; i++) {
            right.keys[i] = node.keys[middle + 1 + i];
            node.keys[middle + 1 + i] = null;
        }
        for (int i = 0; i <= right.keyCount; i++) {
            right.children[i] = node.children[middle + 1 + i];
            node.children[middle + 1 + i] = null;
        }
        node.keys[middle] = null;
        node.keyCount = middle;
        return new Split<>(promoted, right);
    }

    private LeafPosition<K, V> locate(K searchKey) {
        if (root == null) return null;
        Node<K, V> node = root;
        while (!node.leaf) node = node.children[upperBound(node, searchKey)];
        return new LeafPosition<>(node, lowerBound(node, searchKey));
    }

    private int lowerBound(Node<K, V> node, K searchKey) {
        int low = 0;
        int high = node.keyCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (compare(key(node, middle), searchKey) < 0) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private int upperBound(Node<K, V> node, K searchKey) {
        int low = 0;
        int high = node.keyCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (compare(searchKey, key(node, middle)) >= 0) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private boolean validateNode(Node<K, V> node, boolean isRoot, K lowerInclusive,
            K upperExclusive, int depth, Validation validation) {
        int minimumKeys;
        if (isRoot) minimumKeys = 1;
        else if (node.leaf) minimumKeys = (maxKeys + 1) / 2;
        else minimumKeys = ((order + 1) / 2) - 1;
        if (node.keyCount < minimumKeys || node.keyCount > maxKeys) return false;
        for (int i = 0; i < node.keyCount; i++) {
            K current = key(node, i);
            if (i > 0 && compare(key(node, i - 1), current) >= 0) return false;
            if ((lowerInclusive != null && compare(current, lowerInclusive) < 0)
                    || (upperExclusive != null && compare(current, upperExclusive) >= 0)) return false;
        }
        if (node.leaf) {
            if (validation.leafDepth < 0) validation.leafDepth = depth;
            if (validation.leafDepth != depth) return false;
            validation.keyCount += node.keyCount;
            for (int i = 0; i < node.keyCount; i++) if (node.values[i] == null) return false;
            return true;
        }
        for (int i = 0; i <= node.keyCount; i++) {
            if (node.children[i] == null) return false;
            K childLower = i == 0 ? lowerInclusive : key(node, i - 1);
            K childUpper = i == node.keyCount ? upperExclusive : key(node, i);
            if (!validateNode(node.children[i], false, childLower, childUpper,
                    depth + 1, validation)) return false;
            if (i > 0 && compare(minimumKey(node.children[i]), key(node, i - 1)) != 0) return false;
        }
        return true;
    }

    private K minimumKey(Node<K, V> node) {
        while (!node.leaf) node = node.children[0];
        return key(node, 0);
    }

    private Node<K, V> leftmostLeaf() {
        Node<K, V> node = root;
        while (node != null && !node.leaf) node = node.children[0];
        return node;
    }

    @SuppressWarnings("unchecked")
    private static <K, V> K key(Node<K, V> node, int index) { return (K) node.keys[index]; }

    @SuppressWarnings("unchecked")
    private static <K, V> V value(Node<K, V> node, int index) { return (V) node.values[index]; }

    @SuppressWarnings("unchecked")
    private int compare(K left, K right) {
        if (ordering != null) return ordering.compare(left, right);
        if (!(left instanceof Comparable<?>)) {
            throw new IllegalStateException("keys must implement Comparable when no Ordering is supplied");
        }
        return ((Comparable<? super K>) left).compareTo(right);
    }

    private static void requireKey(Object key) {
        if (key == null) throw new IllegalArgumentException("index does not accept null keys");
    }

    private static void requireEntry(Object key, Object value) {
        requireKey(key);
        if (value == null) throw new IllegalArgumentException("index does not accept null values");
    }

    public static final class Entry<K, V> {
        private final K key;
        private final V value;
        private Entry(K key, V value) { this.key = key; this.value = value; }
        public K key() { return key; }
        public V value() { return value; }
    }

    private static final class Node<K, V> {
        private final Object[] keys;
        private final Object[] values;
        private final Node<K, V>[] children;
        private final boolean leaf;
        private int keyCount;
        private Node<K, V> next;

        @SuppressWarnings("unchecked")
        private Node(int order, boolean leaf) {
            keys = new Object[order]; // one overflow slot
            values = new Object[order];
            children = (Node<K, V>[]) new Node[order + 1]; // one overflow child
            this.leaf = leaf;
        }
    }

    private static final class Split<K, V> {
        private final K separator;
        private final Node<K, V> right;
        private Split(K separator, Node<K, V> right) {
            this.separator = separator;
            this.right = right;
        }
    }

    private static final class LeafPosition<K, V> {
        private final Node<K, V> leaf;
        private final int index;
        private LeafPosition(Node<K, V> leaf, int index) {
            this.leaf = leaf;
            this.index = index;
        }
    }

    private static final class Validation {
        private int keyCount;
        private int leafDepth = -1;
    }
}
