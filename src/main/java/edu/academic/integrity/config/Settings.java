package edu.academic.integrity.config;

public final class Settings {
    public boolean enableExact = true;
    public boolean enableShingle = true;
    public boolean enableFuzzy = true;
    public boolean enableGraph = true;
    public int wordShingleSize = 4;
    public int characterShingleSize = 9;
    public int minExactPhraseCharacters = 28;
    public double candidateThreshold = 0.18;
    public double reviewThreshold = 0.32;
    public double graphEdgeThreshold = 0.30;
    public double exactWeight = 0.35;
    public double shingleWeight = 0.30;
    public double fuzzyWeight = 0.25;
    public double graphWeight = 0.10;
    public int maxEvidence = 8;
    public int workerCount = 4;
    public long maxFileBytes = 2_000_000L;
    public boolean removeStopwords = true;
    public String submissionDirectory = "data/submissions";
    public String referenceDirectory = "data/references";
    public String reportDirectory = "reports";
    public String stopwordFile = "data/stopwords.txt";

    public void validate() {
        if (!enableExact && !enableShingle && !enableFuzzy && !enableGraph) {
            throw new IllegalArgumentException("At least one analysis algorithm must be enabled");
        }
        if (wordShingleSize < 1 || characterShingleSize < 2 || minExactPhraseCharacters < 4) {
            throw new IllegalArgumentException("Shingle and phrase sizes must be positive");
        }
        if (!unit(candidateThreshold) || !unit(reviewThreshold) || !unit(graphEdgeThreshold)) {
            throw new IllegalArgumentException("Thresholds must be between 0 and 1");
        }
        if (!finiteNonNegative(exactWeight) || !finiteNonNegative(shingleWeight)
                || !finiteNonNegative(fuzzyWeight) || !finiteNonNegative(graphWeight)) {
            throw new IllegalArgumentException("Score weights must be finite and non-negative");
        }
        double weightTotal = (enableExact ? exactWeight : 0.0)
                + (enableShingle ? shingleWeight : 0.0)
                + (enableFuzzy ? fuzzyWeight : 0.0)
                + (enableGraph ? graphWeight : 0.0);
        if (!Double.isFinite(weightTotal) || weightTotal <= 0) {
            throw new IllegalArgumentException(
                    "Enabled algorithm weights must have a finite positive total");
        }
        if (maxEvidence < 1 || workerCount < 1 || maxFileBytes < 1) {
            throw new IllegalArgumentException("Capacity settings must be positive");
        }
        requirePath(submissionDirectory, "submissionDirectory");
        requirePath(referenceDirectory, "referenceDirectory");
        requirePath(reportDirectory, "reportDirectory");
        requirePath(stopwordFile, "stopwordFile");
    }

    private boolean unit(double value) {
        return value >= 0.0 && value <= 1.0 && !Double.isNaN(value);
    }

    private boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private void requirePath(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(name + " contains an invalid path character");
        }
    }
}
