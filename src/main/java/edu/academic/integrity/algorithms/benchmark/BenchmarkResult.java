package edu.academic.integrity.algorithms.benchmark;

/** Immutable timing result suitable for console and exported benchmark reports. */
public final class BenchmarkResult {
    private final String algorithm;
    private final int inputSize;
    private final int repetitions;
    private final long totalNanoseconds;
    private final long minimumNanoseconds;
    private final long maximumNanoseconds;
    private final long outputChecksum;
    private final boolean successful;
    private final String failureMessage;

    private BenchmarkResult(String algorithm, int inputSize, int repetitions,
            long totalNanoseconds, long minimumNanoseconds, long maximumNanoseconds,
            long outputChecksum, boolean successful, String failureMessage) {
        this.algorithm = algorithm;
        this.inputSize = inputSize;
        this.repetitions = repetitions;
        this.totalNanoseconds = totalNanoseconds;
        this.minimumNanoseconds = minimumNanoseconds;
        this.maximumNanoseconds = maximumNanoseconds;
        this.outputChecksum = outputChecksum;
        this.successful = successful;
        this.failureMessage = failureMessage;
    }

    static BenchmarkResult success(String algorithm, int inputSize, int repetitions,
            long totalNanoseconds, long minimumNanoseconds, long maximumNanoseconds,
            long outputChecksum) {
        return new BenchmarkResult(algorithm, inputSize, repetitions, totalNanoseconds,
                minimumNanoseconds, maximumNanoseconds, outputChecksum, true, "");
    }

    static BenchmarkResult failure(String algorithm, int inputSize, int repetitions,
            String message) {
        return new BenchmarkResult(algorithm, inputSize, repetitions, 0L,
                0L, 0L, 0L, false, message == null ? "Unspecified failure" : message);
    }

    public String algorithm() {
        return algorithm;
    }

    public int inputSize() {
        return inputSize;
    }

    public int repetitions() {
        return repetitions;
    }

    public long totalNanoseconds() {
        return totalNanoseconds;
    }

    public long minimumNanoseconds() {
        return minimumNanoseconds;
    }

    public long maximumNanoseconds() {
        return maximumNanoseconds;
    }

    public double averageNanoseconds() {
        return repetitions == 0 ? 0.0 : (double) totalNanoseconds / repetitions;
    }

    public double averageMilliseconds() {
        return averageNanoseconds() / 1_000_000.0;
    }

    public long outputChecksum() {
        return outputChecksum;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public String toReportLine() {
        if (!successful) {
            return algorithm + " | n=" + inputSize + " | FAILED: " + failureMessage;
        }
        return algorithm + " | n=" + inputSize + " | runs=" + repetitions
                + " | avg=" + averageMilliseconds() + " ms | min="
                + (minimumNanoseconds / 1_000_000.0) + " ms | max="
                + (maximumNanoseconds / 1_000_000.0) + " ms";
    }

    @Override
    public String toString() {
        return toReportLine();
    }
}
