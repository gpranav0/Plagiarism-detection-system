package edu.academic.integrity.algorithms.text;

/** Knuth-Morris-Pratt exact matching for characters and token arrays. */
public final class KMP {
    private KMP() {
    }

    /** Returns the first match position, or {@code -1} when no match exists. */
    public static int indexOf(String text, String pattern) {
        validate(text, pattern);
        if (pattern.length() == 0) {
            return 0;
        }
        int[] prefix = prefixFunction(pattern);
        int matched = 0;
        for (int index = 0; index < text.length(); index++) {
            while (matched > 0 && text.charAt(index) != pattern.charAt(matched)) {
                matched = prefix[matched - 1];
            }
            if (text.charAt(index) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == pattern.length()) {
                return index - pattern.length() + 1;
            }
        }
        return -1;
    }

    /** Returns all overlapping match positions. */
    public static int[] findAll(String text, String pattern) {
        validate(text, pattern);
        if (pattern.length() == 0) {
            return new int[] {0};
        }
        int[] prefix = prefixFunction(pattern);
        IntAccumulator matches = new IntAccumulator();
        int matched = 0;
        for (int index = 0; index < text.length(); index++) {
            while (matched > 0 && text.charAt(index) != pattern.charAt(matched)) {
                matched = prefix[matched - 1];
            }
            if (text.charAt(index) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == pattern.length()) {
                matches.add(index - pattern.length() + 1);
                matched = prefix[matched - 1];
            }
        }
        return matches.toArray();
    }

    /** Builds the KMP longest-proper-prefix table. */
    public static int[] prefixFunction(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern cannot be null");
        }
        int[] prefix = new int[pattern.length()];
        for (int index = 1; index < pattern.length(); index++) {
            int candidate = prefix[index - 1];
            while (candidate > 0 && pattern.charAt(index) != pattern.charAt(candidate)) {
                candidate = prefix[candidate - 1];
            }
            if (pattern.charAt(index) == pattern.charAt(candidate)) {
                candidate++;
            }
            prefix[index] = candidate;
        }
        return prefix;
    }

    /** Returns all overlapping token-level matches. */
    public static int[] findAll(String[] text, String[] pattern) {
        validate(text, "text");
        validate(pattern, "pattern");
        if (pattern.length == 0) {
            return new int[] {0};
        }
        int[] prefix = tokenPrefixFunction(pattern);
        IntAccumulator matches = new IntAccumulator();
        int matched = 0;
        for (int index = 0; index < text.length; index++) {
            while (matched > 0 && !text[index].equals(pattern[matched])) {
                matched = prefix[matched - 1];
            }
            if (text[index].equals(pattern[matched])) {
                matched++;
            }
            if (matched == pattern.length) {
                matches.add(index - pattern.length + 1);
                matched = prefix[matched - 1];
            }
        }
        return matches.toArray();
    }

    private static int[] tokenPrefixFunction(String[] pattern) {
        int[] prefix = new int[pattern.length];
        for (int index = 1; index < pattern.length; index++) {
            int candidate = prefix[index - 1];
            while (candidate > 0 && !pattern[index].equals(pattern[candidate])) {
                candidate = prefix[candidate - 1];
            }
            if (pattern[index].equals(pattern[candidate])) {
                candidate++;
            }
            prefix[index] = candidate;
        }
        return prefix;
    }

    private static void validate(String text, String pattern) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern cannot be null");
        }
    }

    private static void validate(String[] values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        for (int index = 0; index < values.length; index++) {
            if (values[index] == null) {
                throw new IllegalArgumentException(name + " cannot contain null values");
            }
        }
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
