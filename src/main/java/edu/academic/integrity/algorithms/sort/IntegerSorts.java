package edu.academic.integrity.algorithms.sort;

/** Manual in-place integer sorting algorithms used by score analytics. */
public final class IntegerSorts {
    public static final int DEFAULT_MAX_COUNTING_RANGE = 10_000_000;

    private IntegerSorts() {
    }

    /** Stable bottom-up merge sort: O(n log n) time, O(n) space. */
    public static void mergeSort(int[] values) {
        requireArray(values);
        int n = values.length;
        if (n < 2) {
            return;
        }
        int[] work = new int[n];
        int[] source = values;
        int[] target = work;
        for (int width = 1; width < n; width = nextWidth(width, n)) {
            for (int left = 0; left < n; left += safeBlockWidth(width, n)) {
                int middle = minimum(left + width, n);
                int right = minimum(left + safeBlockWidth(width, n), n);
                merge(source, target, left, middle, right);
            }
            int[] temporary = source;
            source = target;
            target = temporary;
            if (width > n / 2) {
                break;
            }
        }
        if (source != values) {
            copy(source, values);
        }
    }

    /** In-place quicksort with bounded recursion through smaller-side recursion. */
    public static void quickSort(int[] values) {
        requireArray(values);
        quickSort(values, 0, values.length - 1);
    }

    /** In-place max-heap sort: O(n log n) time and O(1) auxiliary space. */
    public static void heapSort(int[] values) {
        requireArray(values);
        for (int root = (values.length >>> 1) - 1; root >= 0; root--) {
            siftDown(values, root, values.length);
        }
        for (int end = values.length - 1; end > 0; end--) {
            swap(values, 0, end);
            siftDown(values, 0, end);
        }
    }

    /** Stable counting sort supporting negative integers within a safe range. */
    public static void countingSort(int[] values) {
        countingSort(values, DEFAULT_MAX_COUNTING_RANGE);
    }

    public static void countingSort(int[] values, int maximumRange) {
        requireArray(values);
        if (maximumRange < 1) {
            throw new IllegalArgumentException("Maximum counting range must be positive");
        }
        if (values.length < 2) {
            return;
        }
        int minimum = values[0];
        int maximum = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] < minimum) {
                minimum = values[i];
            }
            if (values[i] > maximum) {
                maximum = values[i];
            }
        }
        long rangeLong = (long) maximum - minimum + 1L;
        if (rangeLong > maximumRange) {
            throw new IllegalArgumentException("Counting-sort value range " + rangeLong
                    + " exceeds configured limit " + maximumRange);
        }
        int[] counts = new int[(int) rangeLong];
        for (int i = 0; i < values.length; i++) {
            counts[(int) ((long) values[i] - minimum)]++;
        }
        int output = 0;
        for (int offset = 0; offset < counts.length; offset++) {
            int value = (int) ((long) minimum + offset);
            for (int occurrence = 0; occurrence < counts[offset]; occurrence++) {
                values[output++] = value;
            }
        }
    }

    /** Stable four-pass LSD radix sort over the complete signed int domain. */
    public static void radixSort(int[] values) {
        requireArray(values);
        if (values.length < 2) {
            return;
        }
        int[] work = new int[values.length];
        int[] source = values;
        int[] target = work;
        for (int shift = 0; shift < 32; shift += 8) {
            int[] counts = new int[256];
            for (int i = 0; i < source.length; i++) {
                int key = source[i] ^ Integer.MIN_VALUE;
                counts[(key >>> shift) & 0xff]++;
            }
            int position = 0;
            for (int bucket = 0; bucket < counts.length; bucket++) {
                int frequency = counts[bucket];
                counts[bucket] = position;
                position += frequency;
            }
            for (int i = 0; i < source.length; i++) {
                int key = source[i] ^ Integer.MIN_VALUE;
                int bucket = (key >>> shift) & 0xff;
                target[counts[bucket]++] = source[i];
            }
            int[] temporary = source;
            source = target;
            target = temporary;
        }
        if (source != values) {
            copy(source, values);
        }
    }

    public static boolean isSorted(int[] values) {
        requireArray(values);
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] > values[i]) {
                return false;
            }
        }
        return true;
    }

    private static void quickSort(int[] values, int low, int high) {
        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            int pivot = median(values[low], values[middle], values[high]);
            int left = low;
            int right = high;
            while (left <= right) {
                while (values[left] < pivot) {
                    left++;
                }
                while (values[right] > pivot) {
                    right--;
                }
                if (left <= right) {
                    swap(values, left++, right--);
                }
            }
            if (right - low < high - left) {
                if (low < right) {
                    quickSort(values, low, right);
                }
                low = left;
            } else {
                if (left < high) {
                    quickSort(values, left, high);
                }
                high = right;
            }
        }
    }

    private static int median(int first, int second, int third) {
        if (first < second) {
            if (second < third) {
                return second;
            }
            return first < third ? third : first;
        }
        if (first < third) {
            return first;
        }
        return second < third ? third : second;
    }

    private static void siftDown(int[] values, int root, int size) {
        while (true) {
            int left = (root << 1) + 1;
            if (left >= size) {
                return;
            }
            int right = left + 1;
            int larger = right < size && values[right] > values[left] ? right : left;
            if (values[root] >= values[larger]) {
                return;
            }
            swap(values, root, larger);
            root = larger;
        }
    }

    private static void merge(int[] source, int[] target,
            int left, int middle, int right) {
        int first = left;
        int second = middle;
        int output = left;
        while (first < middle && second < right) {
            if (source[first] <= source[second]) {
                target[output++] = source[first++];
            } else {
                target[output++] = source[second++];
            }
        }
        while (first < middle) {
            target[output++] = source[first++];
        }
        while (second < right) {
            target[output++] = source[second++];
        }
    }

    private static int nextWidth(int width, int length) {
        return width > length / 2 ? length : width << 1;
    }

    private static int safeBlockWidth(int width, int length) {
        return width > length / 2 ? length : width << 1;
    }

    private static int minimum(int first, int second) {
        return first < second ? first : second;
    }

    private static void copy(int[] source, int[] target) {
        for (int i = 0; i < source.length; i++) {
            target[i] = source[i];
        }
    }

    private static void swap(int[] values, int first, int second) {
        int temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private static void requireArray(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }
    }
}
