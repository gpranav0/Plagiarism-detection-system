package edu.academic.integrity.structures;

/** A singly linked list with constant-time insertion at both ends. */
public final class SinglyLinkedList<T> {
    private Node<T> head;
    private Node<T> tail;
    private int size;

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void addFirst(T value) {
        Node<T> node = new Node<>(value);
        node.next = head;
        head = node;
        if (tail == null) {
            tail = node;
        }
        size++;
    }

    public void addLast(T value) {
        Node<T> node = new Node<>(value);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    public void add(int index, T value) {
        checkPosition(index);
        if (index == 0) {
            addFirst(value);
            return;
        }
        if (index == size) {
            addLast(value);
            return;
        }
        Node<T> previous = nodeAt(index - 1);
        Node<T> node = new Node<>(value);
        node.next = previous.next;
        previous.next = node;
        size++;
    }

    public T getFirst() {
        ensureNotEmpty();
        return head.value;
    }

    public T getLast() {
        ensureNotEmpty();
        return tail.value;
    }

    public T get(int index) {
        checkIndex(index);
        return nodeAt(index).value;
    }

    public T set(int index, T value) {
        checkIndex(index);
        Node<T> node = nodeAt(index);
        T old = node.value;
        node.value = value;
        return old;
    }

    public T removeFirst() {
        ensureNotEmpty();
        T removed = head.value;
        head = head.next;
        size--;
        if (size == 0) {
            tail = null;
        }
        return removed;
    }

    public T removeLast() {
        ensureNotEmpty();
        if (size == 1) {
            return removeFirst();
        }
        Node<T> previous = nodeAt(size - 2);
        T removed = tail.value;
        previous.next = null;
        tail = previous;
        size--;
        return removed;
    }

    public T removeAt(int index) {
        checkIndex(index);
        if (index == 0) {
            return removeFirst();
        }
        Node<T> previous = nodeAt(index - 1);
        Node<T> removed = previous.next;
        previous.next = removed.next;
        if (removed == tail) {
            tail = previous;
        }
        size--;
        return removed.value;
    }

    public boolean remove(T value) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            if (equal(current.value, value)) {
                if (previous == null) {
                    removeFirst();
                } else {
                    previous.next = current.next;
                    if (current == tail) {
                        tail = previous;
                    }
                    size--;
                }
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    public int indexOf(T value) {
        int index = 0;
        for (Node<T> node = head; node != null; node = node.next) {
            if (equal(node.value, value)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public void clear() {
        head = tail = null;
        size = 0;
    }

    public DynamicArray<T> toDynamicArray() {
        DynamicArray<T> values = new DynamicArray<>(size);
        for (Node<T> node = head; node != null; node = node.next) {
            values.add(node.value);
        }
        return values;
    }

    private Node<T> nodeAt(int index) {
        Node<T> node = head;
        for (int i = 0; i < index; i++) {
            node = node.next;
        }
        return node;
    }

    private void ensureNotEmpty() {
        if (head == null) {
            throw new IllegalStateException("list is empty");
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void checkPosition(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private static boolean equal(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
    }

    private static final class Node<T> {
        private T value;
        private Node<T> next;

        private Node(T value) {
            this.value = value;
        }
    }
}
