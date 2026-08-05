package edu.academic.integrity.structures;

/** Convenience facade for a maximum-first {@link BinaryHeap}. */
public final class MaxHeap<T> {
    private final BinaryHeap<T> heap;

    public MaxHeap() {
        heap = new BinaryHeap<>(false);
    }

    public MaxHeap(Ordering<? super T> ordering) {
        heap = new BinaryHeap<>(false, ordering);
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
