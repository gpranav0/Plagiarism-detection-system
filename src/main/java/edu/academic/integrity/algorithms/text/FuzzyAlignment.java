package edu.academic.integrity.algorithms.text;

/** Smith-Waterman local passage alignment with rolling score/coordinate rows. */
public final class FuzzyAlignment {
    public static final int DEFAULT_MATCH_SCORE = 3;
    public static final int DEFAULT_MISMATCH_SCORE = -2;
    public static final int DEFAULT_GAP_SCORE = -2;

    private FuzzyAlignment() {
    }

    public static Result align(String[] first, String[] second) {
        return align(first, second, DEFAULT_MATCH_SCORE, DEFAULT_MISMATCH_SCORE, DEFAULT_GAP_SCORE);
    }

    /**
     * Finds the highest-scoring local token alignment. Mismatch and gap scores
     * must be non-positive; coordinates use half-open token ranges.
     */
    public static Result align(String[] first, String[] second,
            int matchScore, int mismatchScore, int gapScore) {
        validate(first, "first");
        validate(second, "second");
        validateScores(matchScore, mismatchScore, gapScore);

        int columns = second.length;
        int[] previousScores = new int[columns + 1];
        int[] currentScores = new int[columns + 1];
        int[] previousFirstStarts = new int[columns + 1];
        int[] currentFirstStarts = new int[columns + 1];
        int[] previousSecondStarts = new int[columns + 1];
        int[] currentSecondStarts = new int[columns + 1];

        int bestScore = 0;
        int bestFirstStart = 0;
        int bestFirstEnd = 0;
        int bestSecondStart = 0;
        int bestSecondEnd = 0;

        for (int row = 1; row <= first.length; row++) {
            currentScores[0] = 0;
            currentFirstStarts[0] = row;
            currentSecondStarts[0] = 0;
            for (int column = 1; column <= columns; column++) {
                int diagonal = previousScores[column - 1]
                        + (first[row - 1].equals(second[column - 1]) ? matchScore : mismatchScore);
                int above = previousScores[column] + gapScore;
                int left = currentScores[column - 1] + gapScore;

                int score = 0;
                int firstStart = row;
                int secondStart = column;
                if (diagonal > score) {
                    score = diagonal;
                    if (previousScores[column - 1] == 0) {
                        firstStart = row - 1;
                        secondStart = column - 1;
                    } else {
                        firstStart = previousFirstStarts[column - 1];
                        secondStart = previousSecondStarts[column - 1];
                    }
                }
                if (above > score) {
                    score = above;
                    firstStart = previousFirstStarts[column];
                    secondStart = previousSecondStarts[column];
                }
                if (left > score) {
                    score = left;
                    firstStart = currentFirstStarts[column - 1];
                    secondStart = currentSecondStarts[column - 1];
                }
                currentScores[column] = score;
                currentFirstStarts[column] = firstStart;
                currentSecondStarts[column] = secondStart;

                if (score > bestScore) {
                    bestScore = score;
                    bestFirstStart = firstStart;
                    bestFirstEnd = row;
                    bestSecondStart = secondStart;
                    bestSecondEnd = column;
                }
            }

            int[] scoreSwap = previousScores;
            previousScores = currentScores;
            currentScores = scoreSwap;
            int[] firstSwap = previousFirstStarts;
            previousFirstStarts = currentFirstStarts;
            currentFirstStarts = firstSwap;
            int[] secondSwap = previousSecondStarts;
            previousSecondStarts = currentSecondStarts;
            currentSecondStarts = secondSwap;
        }

        return new Result(bestScore, bestFirstStart, bestFirstEnd,
                bestSecondStart, bestSecondEnd,
                slice(first, bestFirstStart, bestFirstEnd),
                slice(second, bestSecondStart, bestSecondEnd),
                normalizedScore(bestScore, matchScore, first.length, second.length));
    }

    /** Character-level local alignment with half-open character coordinates. */
    public static CharacterResult align(String first, String second) {
        return align(first, second, DEFAULT_MATCH_SCORE, DEFAULT_MISMATCH_SCORE, DEFAULT_GAP_SCORE);
    }

    public static CharacterResult align(String first, String second,
            int matchScore, int mismatchScore, int gapScore) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("first and second cannot be null");
        }
        String[] firstCharacters = characters(first);
        String[] secondCharacters = characters(second);
        Result tokenResult = align(firstCharacters, secondCharacters, matchScore, mismatchScore, gapScore);
        return new CharacterResult(tokenResult.score(), tokenResult.firstStart(), tokenResult.firstEndExclusive(),
                tokenResult.secondStart(), tokenResult.secondEndExclusive(),
                first.substring(tokenResult.firstStart(), tokenResult.firstEndExclusive()),
                second.substring(tokenResult.secondStart(), tokenResult.secondEndExclusive()),
                tokenResult.normalizedScore());
    }

    private static double normalizedScore(int score, int matchScore, int firstLength, int secondLength) {
        int shorter = firstLength < secondLength ? firstLength : secondLength;
        if (shorter == 0) {
            return 0.0;
        }
        double normalized = (double) score / ((double) matchScore * shorter);
        return normalized > 1.0 ? 1.0 : normalized;
    }

    private static String[] characters(String value) {
        String[] characters = new String[value.length()];
        for (int index = 0; index < value.length(); index++) {
            characters[index] = String.valueOf(value.charAt(index));
        }
        return characters;
    }

    private static String[] slice(String[] source, int start, int end) {
        String[] result = new String[end - start];
        for (int index = start; index < end; index++) {
            result[index - start] = source[index];
        }
        return result;
    }

    private static void validateScores(int matchScore, int mismatchScore, int gapScore) {
        if (matchScore <= 0 || mismatchScore > 0 || gapScore > 0) {
            throw new IllegalArgumentException(
                    "match score must be positive; mismatch and gap scores must be non-positive");
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

    public static class Coordinates {
        private final int score;
        private final int firstStart;
        private final int firstEndExclusive;
        private final int secondStart;
        private final int secondEndExclusive;
        private final double normalizedScore;

        private Coordinates(int score, int firstStart, int firstEndExclusive,
                int secondStart, int secondEndExclusive, double normalizedScore) {
            this.score = score;
            this.firstStart = firstStart;
            this.firstEndExclusive = firstEndExclusive;
            this.secondStart = secondStart;
            this.secondEndExclusive = secondEndExclusive;
            this.normalizedScore = normalizedScore;
        }

        public int score() {
            return score;
        }

        public int getScore() {
            return score;
        }

        public int firstStart() {
            return firstStart;
        }

        public int getFirstStart() {
            return firstStart;
        }

        public int firstEndExclusive() {
            return firstEndExclusive;
        }

        public int getFirstEndExclusive() {
            return firstEndExclusive;
        }

        public int secondStart() {
            return secondStart;
        }

        public int getSecondStart() {
            return secondStart;
        }

        public int secondEndExclusive() {
            return secondEndExclusive;
        }

        public int getSecondEndExclusive() {
            return secondEndExclusive;
        }

        public double normalizedScore() {
            return normalizedScore;
        }

        public double getNormalizedScore() {
            return normalizedScore;
        }
    }

    public static final class Result extends Coordinates {
        private final String[] firstPassage;
        private final String[] secondPassage;

        private Result(int score, int firstStart, int firstEndExclusive,
                int secondStart, int secondEndExclusive,
                String[] firstPassage, String[] secondPassage, double normalizedScore) {
            super(score, firstStart, firstEndExclusive, secondStart, secondEndExclusive, normalizedScore);
            this.firstPassage = firstPassage;
            this.secondPassage = secondPassage;
        }

        public String[] firstPassageTokens() {
            return copy(firstPassage);
        }

        public String[] getFirstPassageTokens() {
            return firstPassageTokens();
        }

        public String firstPassage() {
            return TextNormalizer.join(firstPassage);
        }

        public String getFirstPassage() {
            return firstPassage();
        }

        public String[] secondPassageTokens() {
            return copy(secondPassage);
        }

        public String[] getSecondPassageTokens() {
            return secondPassageTokens();
        }

        public String secondPassage() {
            return TextNormalizer.join(secondPassage);
        }

        public String getSecondPassage() {
            return secondPassage();
        }

        private static String[] copy(String[] source) {
            String[] result = new String[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = source[index];
            }
            return result;
        }
    }

    public static final class CharacterResult extends Coordinates {
        private final String firstPassage;
        private final String secondPassage;

        private CharacterResult(int score, int firstStart, int firstEndExclusive,
                int secondStart, int secondEndExclusive,
                String firstPassage, String secondPassage, double normalizedScore) {
            super(score, firstStart, firstEndExclusive, secondStart, secondEndExclusive, normalizedScore);
            this.firstPassage = firstPassage;
            this.secondPassage = secondPassage;
        }

        public String firstPassage() {
            return firstPassage;
        }

        public String getFirstPassage() {
            return firstPassage;
        }

        public String secondPassage() {
            return secondPassage;
        }

        public String getSecondPassage() {
            return secondPassage;
        }
    }
}
