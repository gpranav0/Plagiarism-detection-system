package edu.academic.integrity.structures;

/** A resizable, contiguous generic array. */
public final class DynamicArray<T> {
    private static final int DEFAULT_CAPACITY = 8;
    private Object[] elements;
    private int size;

    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be non-negative");
        }
        elements = new Object[Math.max(1, initialCapacity)];
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return elements.length;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void add(T value) {
        ensureCapacity(size + 1);
        elements[size++] = value;
    }

    public void add(int index, T value) {
        checkPosition(index);
        ensureCapacity(size + 1);
        for (int i = size; i > index; i--) {
            elements[i] = elements[i - 1];
        }
        elements[index] = value;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) elements[index];
    }

    @SuppressWarnings("unchecked")
    public T set(int index, T value) {
        checkIndex(index);
        T old = (T) elements[index];
        elements[index] = value;
        return old;
    }

    @SuppressWarnings("unchecked")
    public T removeAt(int index) {
        checkIndex(index);
        T removed = (T) elements[index];
        for (int i = index; i < size - 1; i++) {
            elements[i] = elements[i + 1];
        }
        elements[--size] = null;
        shrinkIfSparse();
        return removed;
    }

    public boolean remove(T value) {
        int index = indexOf(value);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
    }

    public int indexOf(T value) {
        for (int i = 0; i < size; i++) {
            if (equal(elements[i], value)) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    public void clear() {
        elements = new Object[DEFAULT_CAPACITY];
        size = 0;
    }

    public Object[] toArray() {
        Object[] copy = new Object[size];
        for (int i = 0; i < size; i++) {
            copy[i] = elements[i];
        }
        return copy;
    }

    public void ensureCapacity(int required) {
        if (required <= elements.length) {
            return;
        }
        int newCapacity = elements.length;
        while (newCapacity < required) {
            int grown = newCapacity + (newCapacity >> 1) + 1;
            if (grown < newCapacity) {
                throw new OutOfMemoryError("array capacity overflow");
            }
            newCapacity = grown;
        }
        resize(newCapacity);
    }

    private void shrinkIfSparse() {
        if (elements.length > DEFAULT_CAPACITY && size <= elements.length / 4) {
            resize(Math.max(DEFAULT_CAPACITY, elements.length / 2));
        }
    }

    private void resize(int capacity) {
        Object[] replacement = new Object[capacity];
        for (int i = 0; i < size; i++) {
            replacement[i] = elements[i];
        }
        elements = replacement;
    }

    private static boolean equal(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
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
}
