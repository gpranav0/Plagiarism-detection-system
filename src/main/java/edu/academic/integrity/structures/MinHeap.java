package edu.academic.integrity.structures;

/** Convenience facade for a minimum-first {@link BinaryHeap}. */
public final class MinHeap<T> {
    private final BinaryHeap<T> heap;

    public MinHeap() {
        heap = new BinaryHeap<>(true);
    }

    public MinHeap(Ordering<? super T> ordering) {
        heap = new BinaryHeap<>(true, ordering);
    }

    public void add(T value) { heap.add(value); }
    public void offer(T value) { heap.offer(value); }
    public T peek() { return heap.peek(); }
    public T poll() { return heap.poll(); }
    public boolean remove(T value) { return heap.remove(value); }
    public int size() { return heap.size(); }
    public boolean isEmpty() { return heap.isEmpty(); }
    public void clear() { heap.clear(); }
    public boolean validateInvariant() { return heap.validateInvariant(); }
}
