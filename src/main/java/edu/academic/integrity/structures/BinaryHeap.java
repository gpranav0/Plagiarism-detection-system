package edu.academic.integrity.structures;

/** Array-backed binary min-heap or max-heap. */
public final class BinaryHeap<T> {
    private final DynamicArray<T> elements;
    private final Ordering<? super T> ordering;
    private final boolean minimumFirst;

    /** Uses the elements' natural {@link Comparable} order. */
    public BinaryHeap(boolean minimumFirst) {
        this(minimumFirst, null);
    }

    public BinaryHeap(boolean minimumFirst, Ordering<? super T> ordering) {
        this.minimumFirst = minimumFirst;
        this.ordering = ordering;
        this.elements = new DynamicArray<>();
    }

    public void offer(T value) {
        requireValue(value);
        elements.add(value);
        siftUp(elements.size() - 1);
    }

    public void add(T value) {
        offer(value);
    }

    public T peek() {
        ensureNotEmpty();
        return elements.get(0);
    }

    public T poll() {
        ensureNotEmpty();
        T root = elements.get(0);
        T last = elements.removeAt(elements.size() - 1);
        if (!elements.isEmpty()) {
            elements.set(0, last);
            siftDown(0);
        }
        return root;
    }

    public T removeRoot() {
        return poll();
    }

    public T replaceRoot(T value) {
        requireValue(value);
        ensureNotEmpty();
        T root = elements.set(0, value);
        siftDown(0);
        return root;
    }

    public boolean remove(T value) {
        int index = elements.indexOf(value);
        if (index < 0) {
            return false;
        }
        int lastIndex = elements.size() - 1;
        T replacement = elements.removeAt(lastIndex);
        if (index != lastIndex) {
            elements.set(index, replacement);
            if (index > 0 && higherPriority(elements.get(index), elements.get(parent(index)))) {
                siftUp(index);
            } else {
                siftDown(index);
            }
        }
        return true;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public void clear() {
        elements.clear();
    }

    public DynamicArray<T> snapshot() {
        DynamicArray<T> copy = new DynamicArray<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            copy.add(elements.get(i));
        }
        return copy;
    }

    public boolean validateInvariant() {
        for (int i = 1; i < elements.size(); i++) {
            if (higherPriority(elements.get(i), elements.get(parent(i)))) {
                return false;
            }
        }
        return true;
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = parent(index);
            if (!higherPriority(elements.get(index), elements.get(parent))) {
                return;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        while (true) {
            int left = index * 2 + 1;
            if (left >= elements.size()) {
                return;
            }
            int right = left + 1;
            int selected = left;
            if (right < elements.size() && higherPriority(elements.get(right), elements.get(left))) {
                selected = right;
            }
            if (!higherPriority(elements.get(selected), elements.get(index))) {
                return;
            }
            swap(index, selected);
            index = selected;
        }
    }

    private void swap(int first, int second) {
        T temporary = elements.get(first);
        elements.set(first, elements.get(second));
        elements.set(second, temporary);
    }

    private boolean higherPriority(T left, T right) {
        int comparison = compare(left, right);
        return minimumFirst ? comparison < 0 : comparison > 0;
    }

    @SuppressWarnings("unchecked")
    private int compare(T left, T right) {
        if (ordering != null) {
            return ordering.compare(left, right);
        }
        if (!(left instanceof Comparable<?>)) {
            throw new IllegalStateException("values must implement Comparable when no Ordering is supplied");
        }
        return ((Comparable<? super T>) left).compareTo(right);
    }

    private static int parent(int index) {
        return (index - 1) >>> 1;
    }

    private static void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("heap does not accept null values");
        }
    }

    private void ensureNotEmpty() {
        if (elements.isEmpty()) {
            throw new IllegalStateException("heap is empty");
        }
    }
}
