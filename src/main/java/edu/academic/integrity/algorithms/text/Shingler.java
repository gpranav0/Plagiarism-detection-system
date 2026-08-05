package edu.academic.integrity.algorithms.text;

/** Concise facade for {@link ShingleGenerator}. */
public final class Shingler {
    private Shingler() {
    }

    public static String[] wordShingles(String[] tokens, int width) {
        return ShingleGenerator.wordShingles(tokens, width);
    }

    public static String[] wordShingles(String text, String[] stopwords, int width) {
        return ShingleGenerator.wordShingles(text, stopwords, width);
    }

    public static String[] characterShingles(String text, int width) {
        return ShingleGenerator.characterShingles(text, width);
    }

    public static String[] normalizedCharacterShingles(String text, int width) {
        return ShingleGenerator.normalizedCharacterShingles(text, width);
    }

    public static double jaccardSimilarity(String[] first, String[] second) {
        return ShingleGenerator.jaccardSimilarity(first, second);
    }
}
