package edu.academic.integrity.service;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.BatchAnalysisResult;

/** Analysis output plus approximate process-memory measurements for the results screen. */
public final class AnalysisRun {
    private final BatchAnalysisResult batch;
    private final AnalysisResult[] results;
    private final long memoryBeforeBytes;
    private final long memoryAfterBytes;
    private final long elapsedNanos;

    public AnalysisRun(BatchAnalysisResult batch, AnalysisResult[] results,
            long memoryBeforeBytes, long memoryAfterBytes, long elapsedNanos) {
        if (batch == null || results == null) {
            throw new IllegalArgumentException("Analysis output cannot be null");
        }
        this.batch = batch;
        this.results = copy(results);
        this.memoryBeforeBytes = memoryBeforeBytes;
        this.memoryAfterBytes = memoryAfterBytes;
        this.elapsedNanos = Math.max(0L, elapsedNanos);
    }

    public BatchAnalysisResult batch() { return batch; }
    public AnalysisResult[] results() { return copy(results); }
    public long memoryBeforeBytes() { return memoryBeforeBytes; }
    public long memoryAfterBytes() { return memoryAfterBytes; }
    public long memoryDeltaBytes() { return memoryAfterBytes - memoryBeforeBytes; }
    public long elapsedNanos() { return elapsedNanos; }
    public double elapsedMillis() { return elapsedNanos / 1_000_000.0; }

    private static AnalysisResult[] copy(AnalysisResult[] source) {
        AnalysisResult[] result = new AnalysisResult[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
