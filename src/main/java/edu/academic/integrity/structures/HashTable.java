package edu.academic.integrity.structures;

/** Separate-chaining hash table with automatic growth and shrinkage. */
public final class HashTable<K, V> {
    private static final int MIN_CAPACITY = 8;
    private static final int LOAD_NUMERATOR = 3;
    private static final int LOAD_DENOMINATOR = 4;

    private Node<K, V>[] buckets;
    private int size;

    public HashTable() {
        this(MIN_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public HashTable(int requestedCapacity) {
        if (requestedCapacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        int capacity = MIN_CAPACITY;
        while (capacity < requestedCapacity && capacity < (1 << 30)) {
            capacity <<= 1;
        }
        buckets = (Node<K, V>[]) new Node[capacity];
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return buckets.length;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public V put(K key, V value) {
        int index = bucketIndex(key, buckets.length);
        for (Node<K, V> node = buckets[index]; node != null; node = node.next) {
            if (equal(node.key, key)) {
                V old = node.value;
                node.value = value;
                return old;
            }
        }
        if ((size + 1) * LOAD_DENOMINATOR > buckets.length * LOAD_NUMERATOR) {
            resize(buckets.length << 1);
            index = bucketIndex(key, buckets.length);
        }
        buckets[index] = new Node<>(key, value, buckets[index]);
        size++;
        return null;
    }

    public V putIfAbsent(K key, V value) {
        Node<K, V> existing = findNode(key);
        if (existing != null) {
            return existing.value;
        }
        put(key, value);
        return null;
    }

    public V get(K key) {
        Node<K, V> node = findNode(key);
        return node == null ? null : node.value;
    }

    public V getOrDefault(K key, V fallback) {
        Node<K, V> node = findNode(key);
        return node == null ? fallback : node.value;
    }

    public boolean containsKey(K key) {
        return findNode(key) != null;
    }

    public boolean containsValue(V value) {
        for (int i = 0; i < buckets.length; i++) {
            for (Node<K, V> node = buckets[i]; node != null; node = node.next) {
                if (equal(node.value, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public V remove(K key) {
        int index = bucketIndex(key, buckets.length);
        Node<K, V> previous = null;
        Node<K, V> current = buckets[index];
        while (current != null) {
            if (equal(current.key, key)) {
                if (previous == null) {
                    buckets[index] = current.next;
                } else {
                    previous.next = current.next;
                }
                size--;
                if (buckets.length > MIN_CAPACITY && size * 4 <= buckets.length) {
                    resize(buckets.length >> 1);
                }
                return current.value;
            }
            previous = current;
            current = current.next;
        }
        return null;
    }

    public DynamicArray<K> keys() {
        DynamicArray<K> result = new DynamicArray<>(size);
        for (int i = 0; i < buckets.length; i++) {
            for (Node<K, V> node = buckets[i]; node != null; node = node.next) {
                result.add(node.key);
            }
        }
        return result;
    }

    public DynamicArray<V> values() {
        DynamicArray<V> result = new DynamicArray<>(size);
        for (int i = 0; i < buckets.length; i++) {
            for (Node<K, V> node = buckets[i]; node != null; node = node.next) {
                result.add(node.value);
            }
        }
        return result;
    }

    public void forEach(EntryConsumer<? super K, ? super V> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer cannot be null");
        }
        for (int i = 0; i < buckets.length; i++) {
            for (Node<K, V> node = buckets[i]; node != null; node = node.next) {
                consumer.accept(node.key, node.value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        buckets = (Node<K, V>[]) new Node[MIN_CAPACITY];
        size = 0;
    }

    public boolean validateInvariant() {
        int counted = 0;
        for (int i = 0; i < buckets.length; i++) {
            for (Node<K, V> node = buckets[i]; node != null; node = node.next) {
                if (bucketIndex(node.key, buckets.length) != i) {
                    return false;
                }
                counted++;
            }
        }
        return counted == size && (buckets.length & (buckets.length - 1)) == 0;
    }

    private Node<K, V> findNode(K key) {
        int index = bucketIndex(key, buckets.length);
        for (Node<K, V> node = buckets[index]; node != null; node = node.next) {
            if (equal(node.key, key)) {
                return node;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCapacity) {
        Node<K, V>[] replacement = (Node<K, V>[]) new Node[Math.max(MIN_CAPACITY, newCapacity)];
        for (int i = 0; i < buckets.length; i++) {
            Node<K, V> node = buckets[i];
            while (node != null) {
                Node<K, V> next = node.next;
                int destination = bucketIndex(node.key, replacement.length);
                node.next = replacement[destination];
                replacement[destination] = node;
                node = next;
            }
        }
        buckets = replacement;
    }

    private static int bucketIndex(Object key, int length) {
        int hash = key == null ? 0 : key.hashCode();
        hash ^= hash >>> 16;
        return hash & (length - 1);
    }

    private static boolean equal(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
    }

    @FunctionalInterface
    public interface EntryConsumer<K, V> {
        void accept(K key, V value);
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> next;

        private Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
