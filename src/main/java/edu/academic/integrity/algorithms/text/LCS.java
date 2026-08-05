package edu.academic.integrity.algorithms.text;

/** Longest common subsequence operations for characters and document tokens. */
public final class LCS {
    private LCS() {
    }

    /** Computes token LCS length with two rolling rows. */
    public static int length(String[] first, String[] second) {
        validate(first, "first");
        validate(second, "second");
        String[] rows = first;
        String[] columns = second;
        if (columns.length > rows.length) {
            rows = second;
            columns = first;
        }
        int[] previous = new int[columns.length + 1];
        int[] current = new int[columns.length + 1];
        for (int row = 1; row <= rows.length; row++) {
            for (int column = 1; column <= columns.length; column++) {
                if (rows[row - 1].equals(columns[column - 1])) {
                    current[column] = previous[column - 1] + 1;
                } else {
                    current[column] = previous[column] >= current[column - 1]
                            ? previous[column] : current[column - 1];
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            for (int column = 0; column < current.length; column++) {
                current[column] = 0;
            }
        }
        return previous[columns.length];
    }

    /**
     * Computes a representative token LCS and the corresponding source token
     * positions. Ties are resolved deterministically toward earlier columns.
     */
    public static Result analyze(String[] first, String[] second) {
        validate(first, "first");
        validate(second, "second");
        int[][] lengths = new int[first.length + 1][second.length + 1];
        for (int left = 1; left <= first.length; left++) {
            for (int right = 1; right <= second.length; right++) {
                if (first[left - 1].equals(second[right - 1])) {
                    lengths[left][right] = lengths[left - 1][right - 1] + 1;
                } else {
                    lengths[left][right] = lengths[left - 1][right] >= lengths[left][right - 1]
                            ? lengths[left - 1][right] : lengths[left][right - 1];
                }
            }
        }

        int sequenceLength = lengths[first.length][second.length];
        String[] sequence = new String[sequenceLength];
        int[] firstPositions = new int[sequenceLength];
        int[] secondPositions = new int[sequenceLength];
        int output = sequenceLength - 1;
        int left = first.length;
        int right = second.length;
        while (left > 0 && right > 0) {
            if (first[left - 1].equals(second[right - 1])) {
                sequence[output] = first[left - 1];
                firstPositions[output] = left - 1;
                secondPositions[output] = right - 1;
                output--;
                left--;
                right--;
            } else if (lengths[left - 1][right] >= lengths[left][right - 1]) {
                left--;
            } else {
                right--;
            }
        }
        return new Result(sequence, firstPositions, secondPositions);
    }

    /** Computes character LCS length with two rolling rows. */
    public static int length(String first, String second) {
        validate(first, "first");
        validate(second, "second");
        String rows = first;
        String columns = second;
        if (columns.length() > rows.length()) {
            rows = second;
            columns = first;
        }
        int[] previous = new int[columns.length() + 1];
        int[] current = new int[columns.length() + 1];
        for (int row = 1; row <= rows.length(); row++) {
            for (int column = 1; column <= columns.length(); column++) {
                if (rows.charAt(row - 1) == columns.charAt(column - 1)) {
                    current[column] = previous[column - 1] + 1;
                } else {
                    current[column] = previous[column] >= current[column - 1]
                            ? previous[column] : current[column - 1];
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            for (int column = 0; column < current.length; column++) {
                current[column] = 0;
            }
        }
        return previous[columns.length()];
    }

    /** Returns one deterministic character LCS. */
    public static String sequence(String first, String second) {
        validate(first, "first");
        validate(second, "second");
        int[][] lengths = new int[first.length() + 1][second.length() + 1];
        for (int left = 1; left <= first.length(); left++) {
            for (int right = 1; right <= second.length(); right++) {
                if (first.charAt(left - 1) == second.charAt(right - 1)) {
                    lengths[left][right] = lengths[left - 1][right - 1] + 1;
                } else {
                    lengths[left][right] = lengths[left - 1][right] >= lengths[left][right - 1]
                            ? lengths[left - 1][right] : lengths[left][right - 1];
                }
            }
        }
        char[] sequence = new char[lengths[first.length()][second.length()]];
        int output = sequence.length - 1;
        int left = first.length();
        int right = second.length();
        while (left > 0 && right > 0) {
            if (first.charAt(left - 1) == second.charAt(right - 1)) {
                sequence[output--] = first.charAt(left - 1);
                left--;
                right--;
            } else if (lengths[left - 1][right] >= lengths[left][right - 1]) {
                left--;
            } else {
                right--;
            }
        }
        return new String(sequence);
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

    private static void validate(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    public static final class Result {
        private final String[] sequence;
        private final int[] firstPositions;
        private final int[] secondPositions;

        private Result(String[] sequence, int[] firstPositions, int[] secondPositions) {
            this.sequence = sequence;
            this.firstPositions = firstPositions;
            this.secondPositions = secondPositions;
        }

        public int length() {
            return sequence.length;
        }

        public int getLength() {
            return sequence.length;
        }

        public String[] sequence() {
            return copy(sequence);
        }

        public String[] getSequence() {
            return sequence();
        }

        public String passage() {
            return TextNormalizer.join(sequence);
        }

        public String getPassage() {
            return passage();
        }

        public int[] firstPositions() {
            return copy(firstPositions);
        }

        public int[] getFirstPositions() {
            return firstPositions();
        }

        public int[] secondPositions() {
            return copy(secondPositions);
        }

        public int[] getSecondPositions() {
            return secondPositions();
        }

        public int firstStart() {
            return firstPositions.length == 0 ? -1 : firstPositions[0];
        }

        public int firstEndExclusive() {
            return firstPositions.length == 0 ? -1 : firstPositions[firstPositions.length - 1] + 1;
        }

        public int secondStart() {
            return secondPositions.length == 0 ? -1 : secondPositions[0];
        }

        public int secondEndExclusive() {
            return secondPositions.length == 0 ? -1 : secondPositions[secondPositions.length - 1] + 1;
        }

        private static String[] copy(String[] source) {
            String[] result = new String[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = source[index];
            }
            return result;
        }

        private static int[] copy(int[] source) {
            int[] result = new int[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = source[index];
            }
            return result;
        }
    }
}
