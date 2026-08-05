package edu.academic.integrity.algorithms.text;

/** Descriptive facade for {@link LCS}. */
public final class LongestCommonSubsequence {
    private LongestCommonSubsequence() {
    }

    public static int length(String first, String second) {
        return LCS.length(first, second);
    }

    public static String sequence(String first, String second) {
        return LCS.sequence(first, second);
    }

    public static int length(String[] first, String[] second) {
        return LCS.length(first, second);
    }

    public static LCS.Result analyze(String[] first, String[] second) {
        return LCS.analyze(first, second);
    }
}
