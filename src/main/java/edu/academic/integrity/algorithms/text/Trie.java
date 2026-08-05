package edu.academic.integrity.algorithms.text;

/**
 * Array-backed generic trie mapping strings to values. Child arrays are kept in
 * character order, so prefix enumeration is deterministic.
 */
public final class Trie<V> {
    private final Node<V> root = new Node<V>();
    private int size;

    /** Inserts or replaces a key and returns the previous value. */
    public V put(String key, V value) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        Node<V> node = root;
        for (int index = 0; index < key.length(); index++) {
            node = node.getOrCreate(key.charAt(index));
        }
        V previous = node.terminal ? node.value : null;
        if (!node.terminal) {
            size++;
        }
        node.terminal = true;
        node.value = value;
        return previous;
    }

    /** Returns the value associated with a key, or {@code null}. */
    public V get(String key) {
        Node<V> node = findNode(key);
        return node != null && node.terminal ? node.value : null;
    }

    public boolean containsKey(String key) {
        Node<V> node = findNode(key);
        return node != null && node.terminal;
    }

    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    /** Removes a key and returns its old value, or {@code null}. */
    public V remove(String key) {
        validateKey(key);
        Object[] path = new Object[key.length() + 1];
        int[] childIndexes = new int[key.length()];
        Node<V> node = root;
        path[0] = root;
        for (int index = 0; index < key.length(); index++) {
            int childIndex = node.childIndex(key.charAt(index));
            if (childIndex < 0) {
                return null;
            }
            childIndexes[index] = childIndex;
            node = node.childAt(childIndex);
            path[index + 1] = node;
        }
        if (!node.terminal) {
            return null;
        }

        V oldValue = node.value;
        node.value = null;
        node.terminal = false;
        size--;
        for (int depth = key.length(); depth > 0; depth--) {
            Node<V> current = nodeAt(path, depth);
            if (current.terminal || current.childCount > 0) {
                break;
            }
            Node<V> parent = nodeAt(path, depth - 1);
            parent.removeChild(childIndexes[depth - 1]);
        }
        return oldValue;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        root.clearChildren();
        root.terminal = false;
        root.value = null;
        size = 0;
    }

    /** Returns all stored keys beginning with a prefix in character order. */
    public String[] keysWithPrefix(String prefix) {
        Node<V> prefixNode = findNode(prefix);
        if (prefixNode == null) {
            return new String[0];
        }
        int count = countTerminals(prefixNode);
        String[] keys = new String[count];
        StringBuilder builder = new StringBuilder(prefix);
        collect(prefixNode, builder, keys, new int[1]);
        return keys;
    }

    public String[] keys() {
        return keysWithPrefix("");
    }

    private Node<V> findNode(String key) {
        validateKey(key);
        Node<V> node = root;
        for (int index = 0; index < key.length(); index++) {
            node = node.child(key.charAt(index));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private int countTerminals(Node<V> node) {
        int count = node.terminal ? 1 : 0;
        for (int index = 0; index < node.childCount; index++) {
            count += countTerminals(node.childAt(index));
        }
        return count;
    }

    private void collect(Node<V> node, StringBuilder prefix, String[] output, int[] position) {
        if (node.terminal) {
            output[position[0]++] = prefix.toString();
        }
        for (int index = 0; index < node.childCount; index++) {
            prefix.append(node.labels[index]);
            collect(node.childAt(index), prefix, output, position);
            prefix.setLength(prefix.length() - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private Node<V> nodeAt(Object[] nodes, int index) {
        return (Node<V>) nodes[index];
    }

    private void validateKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
    }

    private static final class Node<V> {
        private char[] labels = new char[4];
        private Object[] children = new Object[4];
        private int childCount;
        private boolean terminal;
        private V value;

        Node<V> child(char label) {
            int index = childIndex(label);
            return index < 0 ? null : childAt(index);
        }

        Node<V> getOrCreate(char label) {
            int index = insertionPoint(label);
            if (index < childCount && labels[index] == label) {
                return childAt(index);
            }
            ensureCapacity();
            for (int move = childCount; move > index; move--) {
                labels[move] = labels[move - 1];
                children[move] = children[move - 1];
            }
            Node<V> created = new Node<V>();
            labels[index] = label;
            children[index] = created;
            childCount++;
            return created;
        }

        int childIndex(char label) {
            int index = insertionPoint(label);
            return index < childCount && labels[index] == label ? index : -1;
        }

        int insertionPoint(char label) {
            int low = 0;
            int high = childCount;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (labels[middle] < label) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }

        @SuppressWarnings("unchecked")
        Node<V> childAt(int index) {
            return (Node<V>) children[index];
        }

        void removeChild(int index) {
            for (int move = index; move < childCount - 1; move++) {
                labels[move] = labels[move + 1];
                children[move] = children[move + 1];
            }
            childCount--;
            labels[childCount] = 0;
            children[childCount] = null;
        }

        void clearChildren() {
            labels = new char[4];
            children = new Object[4];
            childCount = 0;
        }

        private void ensureCapacity() {
            if (childCount < labels.length) {
                return;
            }
            char[] grownLabels = new char[labels.length * 2];
            Object[] grownChildren = new Object[children.length * 2];
            for (int index = 0; index < childCount; index++) {
                grownLabels[index] = labels[index];
                grownChildren[index] = children[index];
            }
            labels = grownLabels;
            children = grownChildren;
        }
    }
}
