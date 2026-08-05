package edu.academic.integrity.algorithms.text;

/** Rolling-hash exact search with character-by-character collision verification. */
public final class RabinKarp {
    private static final long BASE = 257L;
    private static final long MODULUS = 1_000_000_007L;

    private RabinKarp() {
    }

    /** Returns the first verified match position, or {@code -1}. */
    public static int indexOf(String text, String pattern) {
        int[] matches = find(text, pattern, true);
        return matches.length == 0 ? -1 : matches[0];
    }

    /** Returns all overlapping, hash-verified match positions. */
    public static int[] findAll(String text, String pattern) {
        return find(text, pattern, false);
    }

    private static int[] find(String text, String pattern, boolean firstOnly) {
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("text and pattern cannot be null");
        }
        int patternLength = pattern.length();
        if (patternLength == 0) {
            return new int[] {0};
        }
        if (patternLength > text.length()) {
            return new int[0];
        }

        long highestPower = 1L;
        for (int index = 1; index < patternLength; index++) {
            highestPower = (highestPower * BASE) % MODULUS;
        }

        long patternHash = 0L;
        long windowHash = 0L;
        for (int index = 0; index < patternLength; index++) {
            patternHash = (patternHash * BASE + pattern.charAt(index) + 1L) % MODULUS;
            windowHash = (windowHash * BASE + text.charAt(index) + 1L) % MODULUS;
        }

        IntAccumulator matches = new IntAccumulator();
        int finalStart = text.length() - patternLength;
        for (int start = 0; start <= finalStart; start++) {
            if (windowHash == patternHash && verifiedMatch(text, pattern, start)) {
                matches.add(start);
                if (firstOnly) {
                    return matches.toArray();
                }
            }
            if (start < finalStart) {
                long outgoing = ((text.charAt(start) + 1L) * highestPower) % MODULUS;
                windowHash = (windowHash - outgoing + MODULUS) % MODULUS;
                windowHash = (windowHash * BASE + text.charAt(start + patternLength) + 1L) % MODULUS;
            }
        }
        return matches.toArray();
    }

    private static boolean verifiedMatch(String text, String pattern, int start) {
        for (int offset = 0; offset < pattern.length(); offset++) {
            if (text.charAt(start + offset) != pattern.charAt(offset)) {
                return false;
            }
        }
        return true;
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
