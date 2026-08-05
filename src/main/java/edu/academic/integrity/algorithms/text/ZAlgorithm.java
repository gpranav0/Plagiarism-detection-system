package edu.academic.integrity.algorithms.text;

/** Linear-time Z-array construction and exact pattern search. */
public final class ZAlgorithm {
    private ZAlgorithm() {
    }

    /** Computes the standard Z array; the first entry is defined as zero. */
    public static int[] compute(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        int[] symbols = new int[value.length()];
        for (int index = 0; index < value.length(); index++) {
            symbols[index] = value.charAt(index) + 1;
        }
        return compute(symbols);
    }

    /** Returns all overlapping exact match positions. */
    public static int[] findAll(String text, String pattern) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern cannot be null");
        }
        if (pattern.length() == 0) {
            return new int[] {0};
        }
        int[] combined = new int[pattern.length() + 1 + text.length()];
        for (int index = 0; index < pattern.length(); index++) {
            combined[index] = pattern.charAt(index) + 1;
        }
        combined[pattern.length()] = 0;
        for (int index = 0; index < text.length(); index++) {
            combined[pattern.length() + 1 + index] = text.charAt(index) + 1;
        }

        int[] z = compute(combined);
        IntAccumulator matches = new IntAccumulator();
        int textOffset = pattern.length() + 1;
        for (int index = textOffset; index < combined.length; index++) {
            if (z[index] >= pattern.length()) {
                matches.add(index - textOffset);
            }
        }
        return matches.toArray();
    }

    /** Returns the first match position, or {@code -1}. */
    public static int indexOf(String text, String pattern) {
        int[] matches = findAll(text, pattern);
        return matches.length == 0 ? -1 : matches[0];
    }

    private static int[] compute(int[] symbols) {
        int[] z = new int[symbols.length];
        int left = 0;
        int right = 0;
        for (int index = 1; index < symbols.length; index++) {
            if (index <= right) {
                int mirrored = z[index - left];
                int remaining = right - index + 1;
                z[index] = mirrored < remaining ? mirrored : remaining;
            }
            while (index + z[index] < symbols.length
                    && symbols[z[index]] == symbols[index + z[index]]) {
                z[index]++;
            }
            if (index + z[index] - 1 > right) {
                left = index;
                right = index + z[index] - 1;
            }
        }
        return z;
    }

    private static final class IntAccumulator {
        private int[] values = new int[8];
        private int size;

        void add(int value) {
            if (size == values.length) {
                int[] grown = new int[values.length * 2];
                for (int index = 0; index < values.length; index++) {
                    grown[index] = values[index];
                }
                values = grown;
            }
            values[size++] = value;
        }

        int[] toArray() {
            int[] result = new int[size];
            for (int index = 0; index < size; index++) {
                result[index] = values[index];
            }
            return result;
        }
    }
}
