package edu.academic.integrity.model;

public final class ScoreBreakdown {
    private final double exactMatch;
    private final double shingleSimilarity;
    private final double fuzzyAlignment;
    private final double graphSignal;
    private final double total;

    public ScoreBreakdown(double exactMatch, double shingleSimilarity,
                          double fuzzyAlignment, double graphSignal,
                          double exactWeight, double shingleWeight,
                          double fuzzyWeight, double graphWeight) {
        this.exactMatch = clamp(exactMatch);
        this.shingleSimilarity = clamp(shingleSimilarity);
        this.fuzzyAlignment = clamp(fuzzyAlignment);
        this.graphSignal = clamp(graphSignal);
        if (!finiteNonNegative(exactWeight) || !finiteNonNegative(shingleWeight)
                || !finiteNonNegative(fuzzyWeight) || !finiteNonNegative(graphWeight)) {
            throw new IllegalArgumentException("Score weights must be finite and non-negative");
        }
        double weightTotal = exactWeight + shingleWeight + fuzzyWeight + graphWeight;
        if (!Double.isFinite(weightTotal) || weightTotal <= 0.0) {
            throw new IllegalArgumentException("Score weights must have a finite positive total");
        }
        this.total = clamp((this.exactMatch * exactWeight
                + this.shingleSimilarity * shingleWeight
                + this.fuzzyAlignment * fuzzyWeight
                + this.graphSignal * graphWeight) / weightTotal);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    public double exactMatch() { return exactMatch; }
    public double shingleSimilarity() { return shingleSimilarity; }
    public double fuzzyAlignment() { return fuzzyAlignment; }
    public double graphSignal() { return graphSignal; }
    public double total() { return total; }

    public String riskLabel() {
        if (total >= 0.75) return "CRITICAL";
        if (total >= 0.55) return "HIGH";
        if (total >= 0.35) return "MEDIUM";
        return "LOW";
    }
}
