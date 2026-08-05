package edu.academic.integrity.core;

import edu.academic.integrity.algorithms.text.TextNormalizer;
import edu.academic.integrity.algorithms.text.Trie;
import edu.academic.integrity.model.Document;

/** Prepares text once and preserves a normalized-to-original character map. */
public final class TextPreparation {
    private TextPreparation() { }

    public static void prepare(Document document, String[] stopwords, boolean removeStopwords) {
        String content = document.content();
        StringBuilder normalized = new StringBuilder(content.length());
        int[] temporaryOffsets = new int[Math.max(1, content.length())];
        int size = 0;
        boolean pendingSpace = false;
        int pendingOffset = 0;
        for (int index = 0; index < content.length();) {
            int current = content.codePointAt(index);
            int sourceWidth = Character.charCount(current);
            if (Character.isLetterOrDigit(current)) {
                if (pendingSpace && normalized.length() > 0) {
                    if (size == temporaryOffsets.length) temporaryOffsets = grow(temporaryOffsets);
                    normalized.append(' ');
                    temporaryOffsets[size++] = pendingOffset;
                }
                int before = normalized.length();
                normalized.appendCodePoint(Character.toLowerCase(current));
                int normalizedWidth = normalized.length() - before;
                while (size + normalizedWidth > temporaryOffsets.length) {
                    temporaryOffsets = grow(temporaryOffsets);
                }
                for (int unit = 0; unit < normalizedWidth; unit++) {
                    temporaryOffsets[size++] = index + Math.min(unit, sourceWidth - 1);
                }
                pendingSpace = false;
            } else if (normalized.length() > 0) {
                if (!pendingSpace) pendingOffset = index;
                pendingSpace = true;
            }
            index += sourceWidth;
        }
        int[] offsets = new int[size];
        System.arraycopy(temporaryOffsets, 0, offsets, 0, size);
        String normalizedText = normalized.toString();
        String[] allTokens = TextNormalizer.tokenizeNormalized(normalizedText, new String[0]);
        String[] tokens = removeStopwords
                ? removeStopwords(allTokens, normalizeStopwords(stopwords)) : allTokens;
        document.setPreparedText(normalizedText, tokens, offsets);
    }

    private static String[] removeStopwords(String[] tokens, String[] stopwords) {
        if (stopwords.length == 0) return tokens;
        Trie<Boolean> index = new Trie<>();
        for (String stopword : stopwords) index.put(stopword, Boolean.TRUE);
        int retained = 0;
        for (String token : tokens) if (!index.containsKey(token)) retained++;
        String[] result = new String[retained];
        int output = 0;
        for (String token : tokens) {
            if (!index.containsKey(token)) result[output++] = token;
        }
        return result;
    }

    private static String[] normalizeStopwords(String[] stopwords) {
        if (stopwords == null || stopwords.length == 0) return new String[0];
        String[] temporary = new String[stopwords.length];
        int size = 0;
        for (String word : stopwords) {
            if (word == null) continue;
            String normalized = TextNormalizer.normalize(word);
            if (normalized.isEmpty() || normalized.indexOf(' ') >= 0 || contains(temporary, size, normalized)) {
                continue;
            }
            temporary[size++] = normalized;
        }
        String[] result = new String[size];
        System.arraycopy(temporary, 0, result, 0, size);
        return result;
    }

    private static boolean contains(String[] values, int size, String target) {
        for (int i = 0; i < size; i++) if (values[i].equals(target)) return true;
        return false;
    }

    private static int[] grow(int[] source) {
        int[] result = new int[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
