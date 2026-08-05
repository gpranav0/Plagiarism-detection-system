package edu.academic.integrity.structures;

/** A custom hash set implemented on top of {@link HashTable}. */
public final class HashSet<T> {
    private static final Object PRESENT = new Object();
    private final HashTable<T, Object> table;

    public HashSet() {
        table = new HashTable<>();
    }

    public HashSet(int initialCapacity) {
        table = new HashTable<>(initialCapacity);
    }

    public boolean add(T value) {
        if (table.containsKey(value)) {
            return false;
        }
        table.put(value, PRESENT);
        return true;
    }

    public boolean contains(T value) {
        return table.containsKey(value);
    }

    public boolean remove(T value) {
        if (!table.containsKey(value)) {
            return false;
        }
        table.remove(value);
        return true;
    }

    public int size() {
        return table.size();
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public DynamicArray<T> values() {
        return table.keys();
    }

    public void clear() {
        table.clear();
    }

    public boolean validateInvariant() {
        return table.validateInvariant();
    }
}
