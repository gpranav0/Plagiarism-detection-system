package edu.academic.integrity.service;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.PassageMatch;

/** One case and the run-level measurements needed by a detail view. */
public final class ResultDetail {
    private final AnalysisResult result;
    private final long approximateMemoryDeltaBytes;

    public ResultDetail(AnalysisResult result, long approximateMemoryDeltaBytes) {
        if (result == null) throw new IllegalArgumentException("result cannot be null");
        this.result = result;
        this.approximateMemoryDeltaBytes = approximateMemoryDeltaBytes;
    }

    public AnalysisResult result() { return result; }
    public PassageMatch[] evidence() {
        PassageMatch[] source = result.evidence();
        PassageMatch[] copy = new PassageMatch[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }
    public long approximateMemoryDeltaBytes() { return approximateMemoryDeltaBytes; }
    public double runtimeMillis() { return result.elapsedNanos() / 1_000_000.0; }
    public String submissionText() { return result.submission().content(); }
    public String referenceText() { return result.reference().content(); }
}
