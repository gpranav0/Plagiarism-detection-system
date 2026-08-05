package edu.academic.integrity.algorithms.text;

/** Levenshtein edit distance using two rolling dynamic-programming rows. */
public final class EditDistance {
    private EditDistance() {
    }

    public static int distance(String first, String second) {
        validate(first, "first");
        validate(second, "second");
        String rows = first;
        String columns = second;
        if (columns.length() > rows.length()) {
            rows = second;
            columns = first;
        }
        int[] previous = initialRow(columns.length());
        int[] current = new int[columns.length() + 1];
        for (int row = 1; row <= rows.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= columns.length(); column++) {
                int substitution = previous[column - 1]
                        + (rows.charAt(row - 1) == columns.charAt(column - 1) ? 0 : 1);
                int deletion = previous[column] + 1;
                int insertion = current[column - 1] + 1;
                current[column] = minimum(substitution, deletion, insertion);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[columns.length()];
    }

    public static int distance(String[] first, String[] second) {
        validate(first, "first");
        validate(second, "second");
        String[] rows = first;
        String[] columns = second;
        if (columns.length > rows.length) {
            rows = second;
            columns = first;
        }
        int[] previous = initialRow(columns.length);
        int[] current = new int[columns.length + 1];
        for (int row = 1; row <= rows.length; row++) {
            current[0] = row;
            for (int column = 1; column <= columns.length; column++) {
                int substitution = previous[column - 1]
                        + (rows[row - 1].equals(columns[column - 1]) ? 0 : 1);
                int deletion = previous[column] + 1;
                int insertion = current[column - 1] + 1;
                current[column] = minimum(substitution, deletion, insertion);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[columns.length];
    }

    public static double similarity(String first, String second) {
        validate(first, "first");
        validate(second, "second");
        int maximum = first.length() >= second.length() ? first.length() : second.length();
        return maximum == 0 ? 1.0 : 1.0 - (double) distance(first, second) / maximum;
    }

    public static double similarity(String[] first, String[] second) {
        validate(first, "first");
        validate(second, "second");
        int maximum = first.length >= second.length ? first.length : second.length;
        return maximum == 0 ? 1.0 : 1.0 - (double) distance(first, second) / maximum;
    }

    private static int[] initialRow(int length) {
        int[] row = new int[length + 1];
        for (int index = 0; index <= length; index++) {
            row[index] = index;
        }
        return row;
    }

    private static int minimum(int first, int second, int third) {
        int minimum = first < second ? first : second;
        return minimum < third ? minimum : third;
    }

    private static void validate(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
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
}
