package edu.academic.integrity.structures;

/** Height-balanced AVL tree with unique values and full insertion/deletion rebalancing. */
public final class AVLTree<T> {
    private Node<T> root;
    private int size;
    private final Ordering<? super T> ordering;

    public AVLTree() {
        this(null);
    }

    public AVLTree(Ordering<? super T> ordering) {
        this.ordering = ordering;
    }

    public boolean insert(T value) {
        requireValue(value);
        Mutation mutation = new Mutation();
        root = insert(root, value, mutation);
        if (mutation.changed) size++;
        return mutation.changed;
    }

    public boolean remove(T value) {
        requireValue(value);
        Mutation mutation = new Mutation();
        root = remove(root, value, mutation);
        if (mutation.changed) size--;
        return mutation.changed;
    }

    public boolean contains(T probe) {
        return find(probe) != null;
    }

    public T find(T probe) {
        requireValue(probe);
        Node<T> current = root;
        while (current != null) {
            int comparison = compare(probe, current.value);
            if (comparison == 0) return current.value;
            current = comparison < 0 ? current.left : current.right;
        }
        return null;
    }

    public T minimum() {
        ensureNotEmpty();
        return minimumNode(root).value;
    }

    public T maximum() {
        ensureNotEmpty();
        Node<T> node = root;
        while (node.right != null) node = node.right;
        return node.value;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public int height() { return height(root); }

    public void clear() {
        root = null;
        size = 0;
    }

    public DynamicArray<T> inOrder() {
        DynamicArray<T> result = new DynamicArray<>(size);
        inOrder(root, result);
        return result;
    }

    public DynamicArray<T> levelOrder() {
        DynamicArray<T> result = new DynamicArray<>(size);
        if (root == null) return result;
        LinkedQueue<Node<T>> queue = new LinkedQueue<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            Node<T> node = queue.poll();
            result.add(node.value);
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        return result;
    }

    /** Validates strict ordering, stored heights, balance factors, and node count. */
    public boolean validateInvariant() {
        Count count = new Count();
        return validate(root, null, null, count) >= 0 && count.value == size;
    }

    private Node<T> insert(Node<T> node, T value, Mutation mutation) {
        if (node == null) {
            mutation.changed = true;
            return new Node<>(value);
        }
        int comparison = compare(value, node.value);
        if (comparison < 0) {
            node.left = insert(node.left, value, mutation);
        } else if (comparison > 0) {
            node.right = insert(node.right, value, mutation);
        } else {
            return node;
        }
        return mutation.changed ? rebalance(node) : node;
    }

    private Node<T> remove(Node<T> node, T value, Mutation mutation) {
        if (node == null) return null;
        int comparison = compare(value, node.value);
        if (comparison < 0) {
            node.left = remove(node.left, value, mutation);
        } else if (comparison > 0) {
            node.right = remove(node.right, value, mutation);
        } else {
            mutation.changed = true;
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;
            Node<T> successor = minimumNode(node.right);
            node.value = successor.value;
            node.right = removeMinimum(node.right);
        }
        return mutation.changed ? rebalance(node) : node;
    }

    private Node<T> removeMinimum(Node<T> node) {
        if (node.left == null) return node.right;
        node.left = removeMinimum(node.left);
        return rebalance(node);
    }

    private Node<T> rebalance(Node<T> node) {
        updateHeight(node);
        int balance = balance(node);
        if (balance > 1) {
            if (balance(node.left) < 0) node.left = rotateLeft(node.left); // LR
            return rotateRight(node); // LL
        }
        if (balance < -1) {
            if (balance(node.right) > 0) node.right = rotateRight(node.right); // RL
            return rotateLeft(node); // RR
        }
        return node;
    }

    private Node<T> rotateRight(Node<T> top) {
        Node<T> pivot = top.left;
        Node<T> transfer = pivot.right;
        pivot.right = top;
        top.left = transfer;
        updateHeight(top);
        updateHeight(pivot);
        return pivot;
    }

    private Node<T> rotateLeft(Node<T> top) {
        Node<T> pivot = top.right;
        Node<T> transfer = pivot.left;
        pivot.left = top;
        top.right = transfer;
        updateHeight(top);
        updateHeight(pivot);
        return pivot;
    }

    private static <T> Node<T> minimumNode(Node<T> node) {
        while (node.left != null) node = node.left;
        return node;
    }

    private void inOrder(Node<T> node, DynamicArray<T> output) {
        if (node == null) return;
        inOrder(node.left, output);
        output.add(node.value);
        inOrder(node.right, output);
    }

    private int validate(Node<T> node, T lower, T upper, Count count) {
        if (node == null) return 0;
        if ((lower != null && compare(node.value, lower) <= 0)
                || (upper != null && compare(node.value, upper) >= 0)) return -1;
        int leftHeight = validate(node.left, lower, node.value, count);
        if (leftHeight < 0) return -1;
        int rightHeight = validate(node.right, node.value, upper, count);
        if (rightHeight < 0) return -1;
        int actual = 1 + Math.max(leftHeight, rightHeight);
        if (node.height != actual || Math.abs(leftHeight - rightHeight) > 1) return -1;
        count.value++;
        return actual;
    }

    private static int height(Node<?> node) { return node == null ? 0 : node.height; }
    private static int balance(Node<?> node) { return node == null ? 0 : height(node.left) - height(node.right); }
    private static void updateHeight(Node<?> node) { node.height = 1 + Math.max(height(node.left), height(node.right)); }

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
        private int height = 1;
        private Node(T value) { this.value = value; }
    }

    private static final class Mutation { private boolean changed; }
    private static final class Count { private int value; }
}
