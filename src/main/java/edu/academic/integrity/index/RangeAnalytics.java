package edu.academic.integrity.index;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.structures.FenwickTree;
import edu.academic.integrity.structures.SegmentTree;

/** Range score metrics and cumulative flagged-case counts over a fixed result sequence. */
public final class RangeAnalytics {
    private static final long SCORE_SCALE = 1_000_000L;

    private final int size;
    private final long threshold;
    private final SegmentTree scores;
    private final FenwickTree flaggedCounts;

    public RangeAnalytics(double[] initialScores, double flaggedThreshold) {
        if (initialScores == null) throw new IllegalArgumentException("initialScores cannot be null");
        threshold = scaledScore(flaggedThreshold, "flaggedThreshold");
        size = initialScores.length;
        if (size == 0) {
            scores = null;
            flaggedCounts = null;
            return;
        }
        long[] scaledScores = new long[size];
        long[] flags = new long[size];
        for (int i = 0; i < size; i++) {
            scaledScores[i] = scaledScore(initialScores[i], "initialScores[" + i + "]");
            flags[i] = scaledScores[i] >= threshold ? 1 : 0;
        }
        scores = new SegmentTree(scaledScores);
        flaggedCounts = new FenwickTree(flags);
    }

    public RangeAnalytics(AnalysisResult[] results, double flaggedThreshold) {
        this(extractScores(results), flaggedThreshold);
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public double flaggedThreshold() { return unscale(threshold); }

    public double scoreAt(int index) {
        checkIndex(index);
        return unscale(scores.get(index));
    }

    public void updateScore(int index, double score) {
        checkIndex(index);
        long replacement = scaledScore(score, "score");
        long former = scores.get(index);
        scores.set(index, replacement);
        updateFlag(index, former, replacement);
    }

    /** Adds a delta lazily to a score range; the resulting scores must remain in [0,1]. */
    public void addToRange(int firstInclusive, int lastInclusive, double delta) {
        checkRange(firstInclusive, lastInclusive);
        if (Double.isNaN(delta) || Double.isInfinite(delta)) {
            throw new IllegalArgumentException("delta must be finite");
        }
        long scaledDelta = Math.round(delta * SCORE_SCALE);
        long newMinimum = scores.queryMin(firstInclusive, lastInclusive) + scaledDelta;
        long newMaximum = scores.queryMax(firstInclusive, lastInclusive) + scaledDelta;
        if (newMinimum < 0 || newMaximum > SCORE_SCALE) {
            throw new IllegalArgumentException("range adjustment would move a score outside [0,1]");
        }
        for (int i = firstInclusive; i <= lastInclusive; i++) {
            long former = scores.get(i);
            if ((former >= threshold) != (former + scaledDelta >= threshold)) {
                flaggedCounts.add(i, former >= threshold ? -1 : 1);
            }
        }
        scores.rangeAdd(firstInclusive, lastInclusive, scaledDelta);
    }

    public double rangeSum(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        return unscale(scores.querySum(firstInclusive, lastInclusive));
    }

    public double rangeMinimum(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        return unscale(scores.queryMin(firstInclusive, lastInclusive));
    }

    public double rangeMaximum(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        return unscale(scores.queryMax(firstInclusive, lastInclusive));
    }

    public double rangeAverage(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        return rangeSum(firstInclusive, lastInclusive) / (lastInclusive - firstInclusive + 1);
    }

    public int cumulativeFlaggedCount(int lastInclusive) {
        checkIndex(lastInclusive);
        return (int) flaggedCounts.prefixSum(lastInclusive);
    }

    public int flaggedCount(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        return (int) flaggedCounts.rangeSum(firstInclusive, lastInclusive);
    }

    public RangeSummary summarize(int firstInclusive, int lastInclusive) {
        checkRange(firstInclusive, lastInclusive);
        double sum = rangeSum(firstInclusive, lastInclusive);
        return new RangeSummary(firstInclusive, lastInclusive,
                rangeMinimum(firstInclusive, lastInclusive),
                rangeMaximum(firstInclusive, lastInclusive), sum,
                sum / (lastInclusive - firstInclusive + 1),
                flaggedCount(firstInclusive, lastInclusive));
    }

    public double[] scores() {
        double[] snapshot = new double[size];
        for (int i = 0; i < size; i++) snapshot[i] = scoreAt(i);
        return snapshot;
    }

    public ValidationSummary validationSummary() {
        if (size == 0) return new ValidationSummary(true, true, true, 0);
        boolean segmentTreeValid = scores.validateInvariant();
        boolean fenwickTreeValid = flaggedCounts.validateInvariant();
        boolean flagsMatchThreshold = true;
        for (int i = 0; i < size; i++) {
            long expected = scores.get(i) >= threshold ? 1 : 0;
            if (flaggedCounts.get(i) != expected) {
                flagsMatchThreshold = false;
                break;
            }
        }
        return new ValidationSummary(segmentTreeValid, fenwickTreeValid,
                flagsMatchThreshold, size);
    }

    public boolean validateInvariant() {
        return validationSummary().valid();
    }

    private void updateFlag(int index, long former, long replacement) {
        boolean wasFlagged = former >= threshold;
        boolean nowFlagged = replacement >= threshold;
        if (wasFlagged != nowFlagged) flaggedCounts.add(index, nowFlagged ? 1 : -1);
    }

    private static double[] extractScores(AnalysisResult[] results) {
        if (results == null) throw new IllegalArgumentException("results cannot be null");
        double[] extracted = new double[results.length];
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null || results[i].score() == null) {
                throw new IllegalArgumentException("results[" + i + "] and its score must be non-null");
            }
            extracted[i] = results[i].score().total();
        }
        return extracted;
    }

    private static long scaledScore(double score, String name) {
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
        return Math.round(score * SCORE_SCALE);
    }

    private static double unscale(long score) {
        return score / (double) SCORE_SCALE;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void checkRange(int first, int last) {
        if (first < 0 || last < first || last >= size) {
            throw new IndexOutOfBoundsException("range=[" + first + "," + last + "], size=" + size);
        }
    }

    public static final class RangeSummary {
        private final int firstIndex;
        private final int lastIndex;
        private final double minimum;
        private final double maximum;
        private final double sum;
        private final double average;
        private final int flaggedCount;

        private RangeSummary(int firstIndex, int lastIndex, double minimum, double maximum,
                double sum, double average, int flaggedCount) {
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
            this.minimum = minimum;
            this.maximum = maximum;
            this.sum = sum;
            this.average = average;
            this.flaggedCount = flaggedCount;
        }

        public int firstIndex() { return firstIndex; }
        public int lastIndex() { return lastIndex; }
        public double minimum() { return minimum; }
        public double maximum() { return maximum; }
        public double sum() { return sum; }
        public double average() { return average; }
        public int flaggedCount() { return flaggedCount; }
    }

    public static final class ValidationSummary {
        private final boolean segmentTreeValid;
        private final boolean fenwickTreeValid;
        private final boolean flagsMatchThreshold;
        private final int valuesChecked;

        private ValidationSummary(boolean segmentTreeValid, boolean fenwickTreeValid,
                boolean flagsMatchThreshold, int valuesChecked) {
            this.segmentTreeValid = segmentTreeValid;
            this.fenwickTreeValid = fenwickTreeValid;
            this.flagsMatchThreshold = flagsMatchThreshold;
            this.valuesChecked = valuesChecked;
        }

        public boolean segmentTreeValid() { return segmentTreeValid; }
        public boolean fenwickTreeValid() { return fenwickTreeValid; }
        public boolean flagsMatchThreshold() { return flagsMatchThreshold; }
        public int valuesChecked() { return valuesChecked; }
        public boolean valid() {
            return segmentTreeValid && fenwickTreeValid && flagsMatchThreshold;
        }
    }
}
