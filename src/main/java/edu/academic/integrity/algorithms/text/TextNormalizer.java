package edu.academic.integrity.algorithms.text;

/** Utility methods for canonicalizing and tokenizing submission text. */
public final class TextNormalizer {
    private TextNormalizer() {
    }

    /**
     * Converts letters to lower case, replaces punctuation with spaces, and
     * collapses all whitespace. Letters and digits from any Unicode script are
     * retained.
     */
    public static String normalize(String text) {
        requireText(text, "text");
        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int index = 0; index < text.length();) {
            int current = text.codePointAt(index);
            index += Character.charCount(current);
            if (Character.isLetterOrDigit(current)) {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                normalized.appendCodePoint(Character.toLowerCase(current));
                pendingSpace = false;
            } else if (normalized.length() > 0) {
                pendingSpace = true;
            }
        }
        return normalized.toString();
    }

    /** Tokenizes text after normalization and removes the supplied stopwords. */
    public static String[] tokenize(String text, String[] stopwords) {
        return tokenizeNormalized(normalize(text), normalizedStopwords(stopwords));
    }

    /** Tokenizes text after normalization without removing stopwords. */
    public static String[] tokenize(String text) {
        return tokenize(text, null);
    }

    /**
     * Tokenizes a string that has already been normalized. This method still
     * tolerates repeated spaces so callers are not required to trust input.
     */
    public static String[] tokenizeNormalized(String normalizedText, String[] normalizedStopwords) {
        requireText(normalizedText, "normalizedText");
        int tokenCount = 0;
        int index = 0;
        while (index < normalizedText.length()) {
            while (index < normalizedText.length() && Character.isWhitespace(normalizedText.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < normalizedText.length() && !Character.isWhitespace(normalizedText.charAt(index))) {
                index++;
            }
            if (start < index && !isStopword(normalizedText, start, index, normalizedStopwords)) {
                tokenCount++;
            }
        }

        String[] tokens = new String[tokenCount];
        int output = 0;
        index = 0;
        while (index < normalizedText.length()) {
            while (index < normalizedText.length() && Character.isWhitespace(normalizedText.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < normalizedText.length() && !Character.isWhitespace(normalizedText.charAt(index))) {
                index++;
            }
            if (start < index && !isStopword(normalizedText, start, index, normalizedStopwords)) {
                tokens[output++] = normalizedText.substring(start, index);
            }
        }
        return tokens;
    }

    /** Joins tokens with one space, which is useful for report passages. */
    public static String join(String[] tokens) {
        requireTokens(tokens, "tokens");
        if (tokens.length == 0) {
            return "";
        }
        int capacity = tokens.length - 1;
        for (int index = 0; index < tokens.length; index++) {
            if (tokens[index] == null) {
                throw new IllegalArgumentException("tokens cannot contain null values");
            }
            capacity += tokens[index].length();
        }
        StringBuilder joined = new StringBuilder(capacity);
        for (int index = 0; index < tokens.length; index++) {
            if (index > 0) {
                joined.append(' ');
            }
            joined.append(tokens[index]);
        }
        return joined.toString();
    }

    private static String[] normalizedStopwords(String[] stopwords) {
        if (stopwords == null || stopwords.length == 0) {
            return new String[0];
        }
        String[] temporary = new String[stopwords.length];
        int size = 0;
        for (int index = 0; index < stopwords.length; index++) {
            if (stopwords[index] == null) {
                continue;
            }
            String normalized = normalize(stopwords[index]);
            if (normalized.length() == 0 || containsWhitespace(normalized)) {
                continue;
            }
            boolean duplicate = false;
            for (int existing = 0; existing < size; existing++) {
                if (temporary[existing].equals(normalized)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                temporary[size++] = normalized;
            }
        }
        String[] result = new String[size];
        for (int index = 0; index < size; index++) {
            result[index] = temporary[index];
        }
        return result;
    }

    private static boolean isStopword(String text, int start, int end, String[] stopwords) {
        if (stopwords == null) {
            return false;
        }
        int length = end - start;
        for (int word = 0; word < stopwords.length; word++) {
            String stopword = stopwords[word];
            if (stopword == null || stopword.length() != length) {
                continue;
            }
            int offset = 0;
            while (offset < length && text.charAt(start + offset) == stopword.charAt(offset)) {
                offset++;
            }
            if (offset == length) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static void requireText(String text, String name) {
        if (text == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    private static void requireTokens(String[] tokens, String name) {
        if (tokens == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}
