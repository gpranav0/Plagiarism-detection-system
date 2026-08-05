package edu.academic.integrity.algorithms.text;

/** Creates fixed-width word and character shingles using only arrays. */
public final class ShingleGenerator {
    private ShingleGenerator() {
    }

    /** Returns every consecutive group of {@code width} words. */
    public static String[] wordShingles(String[] tokens, int width) {
        validateWidth(width);
        validateValues(tokens, "tokens");
        if (tokens.length < width) {
            return new String[0];
        }
        String[] shingles = new String[tokens.length - width + 1];
        for (int start = 0; start < shingles.length; start++) {
            int length = width - 1;
            for (int offset = 0; offset < width; offset++) {
                length += tokens[start + offset].length();
            }
            StringBuilder shingle = new StringBuilder(length);
            for (int offset = 0; offset < width; offset++) {
                if (offset > 0) {
                    shingle.append(' ');
                }
                shingle.append(tokens[start + offset]);
            }
            shingles[start] = shingle.toString();
        }
        return shingles;
    }

    /** Normalizes, tokenizes, removes stopwords, and creates word shingles. */
    public static String[] wordShingles(String text, String[] stopwords, int width) {
        return wordShingles(TextNormalizer.tokenize(text, stopwords), width);
    }

    /** Returns every consecutive character shingle from the supplied text. */
    public static String[] characterShingles(String text, int width) {
        validateWidth(width);
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (text.length() < width) {
            return new String[0];
        }
        String[] shingles = new String[text.length() - width + 1];
        for (int start = 0; start < shingles.length; start++) {
            shingles[start] = text.substring(start, start + width);
        }
        return shingles;
    }

    /** Normalizes text before generating character shingles. */
    public static String[] normalizedCharacterShingles(String text, int width) {
        return characterShingles(TextNormalizer.normalize(text), width);
    }

    /** Computes set-based Jaccard similarity without a library set. */
    public static double jaccardSimilarity(String[] first, String[] second) {
        validateValues(first, "first");
        validateValues(second, "second");
        int uniqueFirst = uniqueCount(first);
        int uniqueSecondOnly = 0;
        int intersection = 0;

        for (int index = 0; index < second.length; index++) {
            if (appearedEarlier(second, index)) {
                continue;
            }
            if (contains(first, second[index])) {
                intersection++;
            } else {
                uniqueSecondOnly++;
            }
        }
        int union = uniqueFirst + uniqueSecondOnly;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    private static int uniqueCount(String[] values) {
        int count = 0;
        for (int index = 0; index < values.length; index++) {
            if (!appearedEarlier(values, index)) {
                count++;
            }
        }
        return count;
    }

    private static boolean appearedEarlier(String[] values, int index) {
        for (int previous = 0; previous < index; previous++) {
            if (values[previous].equals(values[index])) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String[] values, String target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static void validateWidth(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("shingle width must be positive");
        }
    }

    private static void validateValues(String[] values, String name) {
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
