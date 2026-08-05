package edu.academic.integrity.service;

import edu.academic.integrity.config.Settings;
import edu.academic.integrity.io.ProjectPaths;

/** Complete editable settings state plus the paths resolved by the engine. */
public final class SettingsSnapshot {
    private final int wordShingleSize;
    private final int characterShingleSize;
    private final int minExactPhraseCharacters;
    private final double candidateThreshold;
    private final double reviewThreshold;
    private final double graphEdgeThreshold;
    private final double exactWeight;
    private final double shingleWeight;
    private final double fuzzyWeight;
    private final double graphWeight;
    private final int maxEvidence;
    private final int workerCount;
    private final long maxFileBytes;
    private final boolean removeStopwords;
    private final boolean exactEnabled;
    private final boolean shingleEnabled;
    private final boolean fuzzyEnabled;
    private final boolean graphEnabled;
    private final String submissionDirectory;
    private final String referenceDirectory;
    private final String reportDirectory;
    private final String stopwordFile;

    public SettingsSnapshot(int wordShingleSize, int characterShingleSize,
            int minExactPhraseCharacters, double candidateThreshold, double reviewThreshold,
            double graphEdgeThreshold, double exactWeight, double shingleWeight,
            double fuzzyWeight, double graphWeight, int maxEvidence, int workerCount,
            long maxFileBytes, boolean removeStopwords, boolean exactEnabled,
            boolean shingleEnabled, boolean fuzzyEnabled, boolean graphEnabled,
            String submissionDirectory, String referenceDirectory, String reportDirectory,
            String stopwordFile) {
        this.wordShingleSize = wordShingleSize;
        this.characterShingleSize = characterShingleSize;
        this.minExactPhraseCharacters = minExactPhraseCharacters;
        this.candidateThreshold = candidateThreshold;
        this.reviewThreshold = reviewThreshold;
        this.graphEdgeThreshold = graphEdgeThreshold;
        this.exactWeight = exactWeight;
        this.shingleWeight = shingleWeight;
        this.fuzzyWeight = fuzzyWeight;
        this.graphWeight = graphWeight;
        this.maxEvidence = maxEvidence;
        this.workerCount = workerCount;
        this.maxFileBytes = maxFileBytes;
        this.removeStopwords = removeStopwords;
        this.exactEnabled = exactEnabled;
        this.shingleEnabled = shingleEnabled;
        this.fuzzyEnabled = fuzzyEnabled;
        this.graphEnabled = graphEnabled;
        this.submissionDirectory = requirePath(submissionDirectory, "submission directory");
        this.referenceDirectory = requirePath(referenceDirectory, "reference directory");
        this.reportDirectory = requirePath(reportDirectory, "report directory");
        this.stopwordFile = requirePath(stopwordFile, "stopword file");
        toSettings().validate();
    }

    public static SettingsSnapshot from(Settings settings, ProjectPaths paths) {
        if (settings == null || paths == null) {
            throw new IllegalArgumentException("settings and paths cannot be null");
        }
        return new SettingsSnapshot(settings.wordShingleSize, settings.characterShingleSize,
                settings.minExactPhraseCharacters, settings.candidateThreshold,
                settings.reviewThreshold, settings.graphEdgeThreshold, settings.exactWeight,
                settings.shingleWeight, settings.fuzzyWeight, settings.graphWeight,
                settings.maxEvidence, settings.workerCount, settings.maxFileBytes,
                settings.removeStopwords, settings.enableExact, settings.enableShingle,
                settings.enableFuzzy, settings.enableGraph, settings.submissionDirectory,
                settings.referenceDirectory, settings.reportDirectory, settings.stopwordFile);
    }

    public Settings toSettings() {
        Settings result = new Settings();
        result.wordShingleSize = wordShingleSize;
        result.characterShingleSize = characterShingleSize;
        result.minExactPhraseCharacters = minExactPhraseCharacters;
        result.candidateThreshold = candidateThreshold;
        result.reviewThreshold = reviewThreshold;
        result.graphEdgeThreshold = graphEdgeThreshold;
        result.exactWeight = exactWeight;
        result.shingleWeight = shingleWeight;
        result.fuzzyWeight = fuzzyWeight;
        result.graphWeight = graphWeight;
        result.maxEvidence = maxEvidence;
        result.workerCount = workerCount;
        result.maxFileBytes = maxFileBytes;
        result.removeStopwords = removeStopwords;
        result.enableExact = exactEnabled;
        result.enableShingle = shingleEnabled;
        result.enableFuzzy = fuzzyEnabled;
        result.enableGraph = graphEnabled;
        result.submissionDirectory = submissionDirectory;
        result.referenceDirectory = referenceDirectory;
        result.reportDirectory = reportDirectory;
        result.stopwordFile = stopwordFile;
        return result;
    }

    public int wordShingleSize() { return wordShingleSize; }
    public int characterShingleSize() { return characterShingleSize; }
    public int minExactPhraseCharacters() { return minExactPhraseCharacters; }
    public double candidateThreshold() { return candidateThreshold; }
    public double reviewThreshold() { return reviewThreshold; }
    public double graphEdgeThreshold() { return graphEdgeThreshold; }
    public double exactWeight() { return exactWeight; }
    public double shingleWeight() { return shingleWeight; }
    public double fuzzyWeight() { return fuzzyWeight; }
    public double graphWeight() { return graphWeight; }
    public int maxEvidence() { return maxEvidence; }
    public int workerCount() { return workerCount; }
    public long maxFileBytes() { return maxFileBytes; }
    public boolean removeStopwords() { return removeStopwords; }
    public boolean exactEnabled() { return exactEnabled; }
    public boolean shingleEnabled() { return shingleEnabled; }
    public boolean fuzzyEnabled() { return fuzzyEnabled; }
    public boolean graphEnabled() { return graphEnabled; }
    public String submissionDirectory() { return submissionDirectory; }
    public String referenceDirectory() { return referenceDirectory; }
    public String reportDirectory() { return reportDirectory; }
    public String stopwordFile() { return stopwordFile; }

    private static String requirePath(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " cannot contain a line break");
        }
        return value.trim();
    }
}
