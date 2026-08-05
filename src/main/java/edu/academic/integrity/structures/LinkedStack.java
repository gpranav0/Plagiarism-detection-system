package edu.academic.integrity.structures;

/** A LIFO stack backed by custom linked nodes. */
public final class LinkedStack<T> {
    private Node<T> top;
    private int size;

    public void push(T value) {
        top = new Node<>(value, top);
        size++;
    }

    public T pop() {
        ensureNotEmpty();
        T value = top.value;
        top = top.next;
        size--;
        return value;
    }

    public T peek() {
        ensureNotEmpty();
        return top.value;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        top = null;
        size = 0;
    }

    private void ensureNotEmpty() {
        if (top == null) {
            throw new IllegalStateException("stack is empty");
        }
    }

    private static final class Node<T> {
        private final T value;
        private final Node<T> next;

        private Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }
}
