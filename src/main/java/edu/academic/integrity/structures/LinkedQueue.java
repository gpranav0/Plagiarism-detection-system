package edu.academic.integrity.structures;

/** A FIFO queue backed by custom linked nodes. */
public final class LinkedQueue<T> {
    private Node<T> front;
    private Node<T> rear;
    private int size;

    public void offer(T value) {
        Node<T> node = new Node<>(value);
        if (rear == null) {
            front = rear = node;
        } else {
            rear.next = node;
            rear = node;
        }
        size++;
    }

    public void enqueue(T value) {
        offer(value);
    }

    public T poll() {
        ensureNotEmpty();
        T value = front.value;
        front = front.next;
        size--;
        if (front == null) {
            rear = null;
        }
        return value;
    }

    public T dequeue() {
        return poll();
    }

    public T peek() {
        ensureNotEmpty();
        return front.value;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        front = rear = null;
        size = 0;
    }

    private void ensureNotEmpty() {
        if (front == null) {
            throw new IllegalStateException("queue is empty");
        }
    }

    private static final class Node<T> {
        private final T value;
        private Node<T> next;

        private Node(T value) {
            this.value = value;
        }
    }
}
