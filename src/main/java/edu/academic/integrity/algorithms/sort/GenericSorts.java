package edu.academic.integrity.algorithms.sort;

/** Generic merge, quick, and heap sorts using a project-owned ordering API. */
public final class GenericSorts {
    private GenericSorts() {
    }

    @FunctionalInterface
    public interface Ordering<T> {
        int compare(T first, T second);
    }

    /** Stable O(n log n) merge sort. */
    public static <T> void mergeSort(T[] values, Ordering<? super T> ordering) {
        requireArguments(values, ordering);
        if (values.length < 2) {
            return;
        }
        Object[] work = new Object[values.length];
        mergeSort(values, work, 0, values.length, ordering);
    }

    public static <T> void quickSort(T[] values, Ordering<? super T> ordering) {
        requireArguments(values, ordering);
        quickSort(values, 0, values.length - 1, ordering);
    }

    public static <T> void heapSort(T[] values, Ordering<? super T> ordering) {
        requireArguments(values, ordering);
        for (int root = (values.length >>> 1) - 1; root >= 0; root--) {
            siftDown(values, root, values.length, ordering);
        }
        for (int end = values.length - 1; end > 0; end--) {
            swap(values, 0, end);
            siftDown(values, 0, end, ordering);
        }
    }

    public static <T> boolean isSorted(T[] values, Ordering<? super T> ordering) {
        requireArguments(values, ordering);
        for (int i = 1; i < values.length; i++) {
            if (ordering.compare(values[i - 1], values[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    private static <T> void mergeSort(T[] values, Object[] work, int left, int right,
            Ordering<? super T> ordering) {
        if (right - left < 2) {
            return;
        }
        int middle = left + ((right - left) >>> 1);
        mergeSort(values, work, left, middle, ordering);
        mergeSort(values, work, middle, right, ordering);
        if (ordering.compare(values[middle - 1], values[middle]) <= 0) {
            return;
        }
        int first = left;
        int second = middle;
        int output = left;
        while (first < middle && second < right) {
            if (ordering.compare(values[first], values[second]) <= 0) {
                work[output++] = values[first++];
            } else {
                work[output++] = values[second++];
            }
        }
        while (first < middle) {
            work[output++] = values[first++];
        }
        while (second < right) {
            work[output++] = values[second++];
        }
        for (int i = left; i < right; i++) {
            values[i] = elementAt(work, i);
        }
    }

    private static <T> void quickSort(T[] values, int low, int high,
            Ordering<? super T> ordering) {
        while (low < high) {
            T pivot = values[low + ((high - low) >>> 1)];
            int left = low;
            int right = high;
            while (left <= right) {
                while (ordering.compare(values[left], pivot) < 0) {
                    left++;
                }
                while (ordering.compare(values[right], pivot) > 0) {
                    right--;
                }
                if (left <= right) {
                    swap(values, left++, right--);
                }
            }
            if (right - low < high - left) {
                if (low < right) {
                    quickSort(values, low, right, ordering);
                }
                low = left;
            } else {
                if (left < high) {
                    quickSort(values, left, high, ordering);
                }
                high = right;
            }
        }
    }

    private static <T> void siftDown(T[] values, int root, int size,
            Ordering<? super T> ordering) {
        while (true) {
            int left = (root << 1) + 1;
            if (left >= size) {
                return;
            }
            int right = left + 1;
            int larger = right < size && ordering.compare(values[right], values[left]) > 0
                    ? right : left;
            if (ordering.compare(values[root], values[larger]) >= 0) {
                return;
            }
            swap(values, root, larger);
            root = larger;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T elementAt(Object[] values, int index) {
        return (T) values[index];
    }

    private static <T> void swap(T[] values, int first, int second) {
        T temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private static <T> void requireArguments(T[] values, Ordering<? super T> ordering) {
        if (values == null || ordering == null) {
            throw new IllegalArgumentException("Values and ordering cannot be null");
        }
    }
}
