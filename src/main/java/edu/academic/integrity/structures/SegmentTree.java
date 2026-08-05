package edu.academic.integrity.structures;

/** Range sum/minimum/maximum segment tree with lazy range addition and point assignment. */
public final class SegmentTree {
    private final int length;
    private final long[] sums;
    private final long[] minimums;
    private final long[] maximums;
    private final long[] lazyAdds;

    public SegmentTree(int length) {
        if (length <= 0) throw new IllegalArgumentException("length must be positive");
        this.length = length;
        int capacity = safeCapacity(length);
        sums = new long[capacity];
        minimums = new long[capacity];
        maximums = new long[capacity];
        lazyAdds = new long[capacity];
    }

    public SegmentTree(long[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-null and non-empty");
        }
        length = values.length;
        int capacity = safeCapacity(length);
        sums = new long[capacity];
        minimums = new long[capacity];
        maximums = new long[capacity];
        lazyAdds = new long[capacity];
        build(1, 0, length - 1, values);
    }

    public int size() { return length; }

    public long get(int index) {
        checkIndex(index);
        return querySum(index, index);
    }

    public void set(int index, long value) {
        checkIndex(index);
        set(1, 0, length - 1, index, value);
    }

    public void update(int index, long value) {
        set(index, value);
    }

    public void add(int leftInclusive, int rightInclusive, long delta) {
        checkRange(leftInclusive, rightInclusive);
        add(1, 0, length - 1, leftInclusive, rightInclusive, delta);
    }

    public void rangeAdd(int leftInclusive, int rightInclusive, long delta) {
        add(leftInclusive, rightInclusive, delta);
    }

    public long querySum(int leftInclusive, int rightInclusive) {
        checkRange(leftInclusive, rightInclusive);
        return querySum(1, 0, length - 1, leftInclusive, rightInclusive);
    }

    public long queryMin(int leftInclusive, int rightInclusive) {
        checkRange(leftInclusive, rightInclusive);
        return queryMin(1, 0, length - 1, leftInclusive, rightInclusive);
    }

    public long queryMax(int leftInclusive, int rightInclusive) {
        checkRange(leftInclusive, rightInclusive);
        return queryMax(1, 0, length - 1, leftInclusive, rightInclusive);
    }

    /** Checks every aggregate against its children while respecting unpushed lazy values. */
    public boolean validateInvariant() {
        return validate(1, 0, length - 1);
    }

    private void build(int node, int left, int right, long[] values) {
        if (left == right) {
            sums[node] = minimums[node] = maximums[node] = values[left];
            return;
        }
        int middle = (left + right) >>> 1;
        build(node * 2, left, middle, values);
        build(node * 2 + 1, middle + 1, right, values);
        pull(node);
    }

    private void set(int node, int left, int right, int index, long value) {
        if (left == right) {
            sums[node] = minimums[node] = maximums[node] = value;
            lazyAdds[node] = 0;
            return;
        }
        push(node, left, right);
        int middle = (left + right) >>> 1;
        if (index <= middle) set(node * 2, left, middle, index, value);
        else set(node * 2 + 1, middle + 1, right, index, value);
        pull(node);
    }

    private void add(int node, int left, int right, int queryLeft, int queryRight, long delta) {
        if (queryLeft <= left && right <= queryRight) {
            apply(node, left, right, delta);
            return;
        }
        push(node, left, right);
        int middle = (left + right) >>> 1;
        if (queryLeft <= middle) add(node * 2, left, middle, queryLeft, queryRight, delta);
        if (queryRight > middle) add(node * 2 + 1, middle + 1, right, queryLeft, queryRight, delta);
        pull(node);
    }

    private long querySum(int node, int left, int right, int queryLeft, int queryRight) {
        if (queryLeft <= left && right <= queryRight) return sums[node];
        push(node, left, right);
        int middle = (left + right) >>> 1;
        long result = 0;
        if (queryLeft <= middle) result += querySum(node * 2, left, middle, queryLeft, queryRight);
        if (queryRight > middle) result += querySum(node * 2 + 1, middle + 1, right, queryLeft, queryRight);
        return result;
    }

    private long queryMin(int node, int left, int right, int queryLeft, int queryRight) {
        if (queryLeft <= left && right <= queryRight) return minimums[node];
        push(node, left, right);
        int middle = (left + right) >>> 1;
        long result = Long.MAX_VALUE;
        if (queryLeft <= middle) result = Math.min(result,
                queryMin(node * 2, left, middle, queryLeft, queryRight));
        if (queryRight > middle) result = Math.min(result,
                queryMin(node * 2 + 1, middle + 1, right, queryLeft, queryRight));
        return result;
    }

    private long queryMax(int node, int left, int right, int queryLeft, int queryRight) {
        if (queryLeft <= left && right <= queryRight) return maximums[node];
        push(node, left, right);
        int middle = (left + right) >>> 1;
        long result = Long.MIN_VALUE;
        if (queryLeft <= middle) result = Math.max(result,
                queryMax(node * 2, left, middle, queryLeft, queryRight));
        if (queryRight > middle) result = Math.max(result,
                queryMax(node * 2 + 1, middle + 1, right, queryLeft, queryRight));
        return result;
    }

    private void apply(int node, int left, int right, long delta) {
        sums[node] += delta * (right - left + 1L);
        minimums[node] += delta;
        maximums[node] += delta;
        lazyAdds[node] += delta;
    }

    private void push(int node, int left, int right) {
        long pending = lazyAdds[node];
        if (pending == 0 || left == right) return;
        int middle = (left + right) >>> 1;
        apply(node * 2, left, middle, pending);
        apply(node * 2 + 1, middle + 1, right, pending);
        lazyAdds[node] = 0;
    }

    private void pull(int node) {
        sums[node] = sums[node * 2] + sums[node * 2 + 1];
        minimums[node] = Math.min(minimums[node * 2], minimums[node * 2 + 1]);
        maximums[node] = Math.max(maximums[node * 2], maximums[node * 2 + 1]);
    }

    private boolean validate(int node, int left, int right) {
        if (left == right) return sums[node] == minimums[node] && sums[node] == maximums[node];
        int middle = (left + right) >>> 1;
        if (!validate(node * 2, left, middle) || !validate(node * 2 + 1, middle + 1, right)) return false;
        long pending = lazyAdds[node];
        long expectedSum = sums[node * 2] + sums[node * 2 + 1]
                + pending * (right - left + 1L);
        long expectedMinimum = Math.min(minimums[node * 2], minimums[node * 2 + 1]) + pending;
        long expectedMaximum = Math.max(maximums[node * 2], maximums[node * 2 + 1]) + pending;
        return sums[node] == expectedSum && minimums[node] == expectedMinimum
                && maximums[node] == expectedMaximum;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + length);
        }
    }

    private void checkRange(int left, int right) {
        if (left < 0 || right < left || right >= length) {
            throw new IndexOutOfBoundsException("range=[" + left + "," + right + "], size=" + length);
        }
    }

    private static int safeCapacity(int length) {
        if (length > (Integer.MAX_VALUE - 5) / 4) {
            throw new IllegalArgumentException("length is too large");
        }
        return length * 4 + 5;
    }
}
