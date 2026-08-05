package edu.academic.integrity.algorithms.text;

/** Descriptive facade for the Smith-Waterman implementation in {@link FuzzyAlignment}. */
public final class SmithWaterman {
    private SmithWaterman() {
    }

    public static FuzzyAlignment.Result align(String[] first, String[] second) {
        return FuzzyAlignment.align(first, second);
    }

    public static FuzzyAlignment.Result align(String[] first, String[] second,
            int matchScore, int mismatchScore, int gapScore) {
        return FuzzyAlignment.align(first, second, matchScore, mismatchScore, gapScore);
    }

    public static FuzzyAlignment.CharacterResult align(String first, String second) {
        return FuzzyAlignment.align(first, second);
    }

    public static FuzzyAlignment.CharacterResult align(String first, String second,
            int matchScore, int mismatchScore, int gapScore) {
        return FuzzyAlignment.align(first, second, matchScore, mismatchScore, gapScore);
    }
}
