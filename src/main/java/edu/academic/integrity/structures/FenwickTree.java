package edu.academic.integrity.structures;

/** Binary indexed tree for point updates and prefix/range sums. Public indices are zero-based. */
public final class FenwickTree {
    private final long[] tree;
    private final long[] values;

    public FenwickTree(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        tree = new long[size + 1];
        values = new long[size];
    }

    public FenwickTree(long[] initialValues) {
        if (initialValues == null || initialValues.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }
        tree = new long[initialValues.length + 1];
        values = new long[initialValues.length];
        for (int i = 0; i < initialValues.length; i++) add(i, initialValues[i]);
    }

    public int size() { return values.length; }

    public void add(int index, long delta) {
        checkIndex(index);
        values[index] += delta;
        for (int i = index + 1; i < tree.length; i += i & -i) tree[i] += delta;
    }

    public void set(int index, long value) {
        checkIndex(index);
        add(index, value - values[index]);
    }

    public long get(int index) {
        checkIndex(index);
        return values[index];
    }

    public long prefixSum(int indexInclusive) {
        checkIndex(indexInclusive);
        return rawPrefixSum(indexInclusive);
    }

    public long rangeSum(int leftInclusive, int rightInclusive) {
        if (leftInclusive < 0 || rightInclusive < leftInclusive || rightInclusive >= values.length) {
            throw new IndexOutOfBoundsException("range=[" + leftInclusive + "," + rightInclusive
                    + "], size=" + values.length);
        }
        return rawPrefixSum(rightInclusive) - (leftInclusive == 0 ? 0 : rawPrefixSum(leftInclusive - 1));
    }

    public long totalSum() {
        return rawPrefixSum(values.length - 1);
    }

    public boolean validateInvariant() {
        for (int treeIndex = 1; treeIndex < tree.length; treeIndex++) {
            int start = treeIndex - (treeIndex & -treeIndex);
            long expected = 0;
            for (int i = start; i < treeIndex; i++) expected += values[i];
            if (tree[treeIndex] != expected) return false;
        }
        return true;
    }

    private long rawPrefixSum(int indexInclusive) {
        long sum = 0;
        for (int i = indexInclusive + 1; i > 0; i -= i & -i) sum += tree[i];
        return sum;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= values.length) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + values.length);
        }
    }
}
