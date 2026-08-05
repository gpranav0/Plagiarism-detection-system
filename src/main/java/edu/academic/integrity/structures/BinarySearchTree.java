package edu.academic.integrity.structures;

/** Unbalanced binary search tree that stores unique values. */
public final class BinarySearchTree<T> {
    private Node<T> root;
    private int size;
    private final Ordering<? super T> ordering;

    public BinarySearchTree() {
        this(null);
    }

    public BinarySearchTree(Ordering<? super T> ordering) {
        this.ordering = ordering;
    }

    public boolean insert(T value) {
        requireValue(value);
        if (root == null) {
            root = new Node<>(value);
            size = 1;
            return true;
        }
        Node<T> current = root;
        while (true) {
            int comparison = compare(value, current.value);
            if (comparison == 0) {
                return false;
            }
            if (comparison < 0) {
                if (current.left == null) {
                    current.left = new Node<>(value);
                    size++;
                    return true;
                }
                current = current.left;
            } else {
                if (current.right == null) {
                    current.right = new Node<>(value);
                    size++;
                    return true;
                }
                current = current.right;
            }
        }
    }

    public boolean contains(T value) {
        return find(value) != null;
    }

    /** Returns the stored value equal to the probe, or {@code null} when absent. */
    public T find(T probe) {
        requireValue(probe);
        Node<T> current = root;
        while (current != null) {
            int comparison = compare(probe, current.value);
            if (comparison == 0) {
                return current.value;
            }
            current = comparison < 0 ? current.left : current.right;
        }
        return null;
    }

    public boolean remove(T value) {
        requireValue(value);
        RemovalFlag flag = new RemovalFlag();
        root = remove(root, value, flag);
        if (flag.removed) {
            size--;
        }
        return flag.removed;
    }

    public T minimum() {
        ensureNotEmpty();
        return minimumNode(root).value;
    }

    public T maximum() {
        ensureNotEmpty();
        Node<T> node = root;
        while (node.right != null) {
            node = node.right;
        }
        return node.value;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return root == null;
    }

    public int height() {
        return height(root);
    }

    public void clear() {
        root = null;
        size = 0;
    }

    public DynamicArray<T> inOrder() {
        DynamicArray<T> values = new DynamicArray<>(size);
        inOrder(root, values);
        return values;
    }

    public DynamicArray<T> preOrder() {
        DynamicArray<T> values = new DynamicArray<>(size);
        preOrder(root, values);
        return values;
    }

    public DynamicArray<T> postOrder() {
        DynamicArray<T> values = new DynamicArray<>(size);
        postOrder(root, values);
        return values;
    }

    public boolean validateInvariant() {
        Count count = new Count();
        return validate(root, null, null, count) && count.value == size;
    }

    private Node<T> remove(Node<T> node, T value, RemovalFlag flag) {
        if (node == null) {
            return null;
        }
        int comparison = compare(value, node.value);
        if (comparison < 0) {
            node.left = remove(node.left, value, flag);
        } else if (comparison > 0) {
            node.right = remove(node.right, value, flag);
        } else {
            flag.removed = true;
            if (node.left == null) {
                return node.right;
            }
            if (node.right == null) {
                return node.left;
            }
            Node<T> successor = minimumNode(node.right);
            node.value = successor.value;
            node.right = removeSuccessor(node.right);
        }
        return node;
    }

    private Node<T> removeSuccessor(Node<T> node) {
        if (node.left == null) {
            return node.right;
        }
        node.left = removeSuccessor(node.left);
        return node;
    }

    private static <T> Node<T> minimumNode(Node<T> node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    private void inOrder(Node<T> node, DynamicArray<T> output) {
        if (node == null) return;
        inOrder(node.left, output);
        output.add(node.value);
        inOrder(node.right, output);
    }

    private void preOrder(Node<T> node, DynamicArray<T> output) {
        if (node == null) return;
        output.add(node.value);
        preOrder(node.left, output);
        preOrder(node.right, output);
    }

    private void postOrder(Node<T> node, DynamicArray<T> output) {
        if (node == null) return;
        postOrder(node.left, output);
        postOrder(node.right, output);
        output.add(node.value);
    }

    private int height(Node<T> node) {
        if (node == null) return 0;
        return 1 + Math.max(height(node.left), height(node.right));
    }

    private boolean validate(Node<T> node, T lower, T upper, Count count) {
        if (node == null) return true;
        if ((lower != null && compare(node.value, lower) <= 0)
                || (upper != null && compare(node.value, upper) >= 0)) {
            return false;
        }
        count.value++;
        return validate(node.left, lower, node.value, count)
                && validate(node.right, node.value, upper, count);
    }

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

    private void ensureNotEmpty() {
        if (root == null) throw new IllegalStateException("tree is empty");
    }

    private static final class Node<T> {
        private T value;
        private Node<T> left;
        private Node<T> right;
        private Node(T value) { this.value = value; }
    }

    private static final class RemovalFlag { private boolean removed; }
    private static final class Count { private int value; }
}
