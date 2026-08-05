package edu.academic.integrity.model;

public final class CopyingPath {
    private final String[] documentIds;
    private final double[] relationshipSimilarities;
    private final double totalCost;

    public CopyingPath(String[] documentIds, double[] relationshipSimilarities, double totalCost) {
        this.documentIds = documentIds;
        this.relationshipSimilarities = relationshipSimilarities;
        this.totalCost = totalCost;
    }

    public String[] documentIds() {
        String[] copy = new String[documentIds.length];
        System.arraycopy(documentIds, 0, copy, 0, documentIds.length);
        return copy;
    }

    public double[] relationshipSimilarities() {
        double[] copy = new double[relationshipSimilarities.length];
        System.arraycopy(relationshipSimilarities, 0, copy, 0, relationshipSimilarities.length);
        return copy;
    }

    public double totalCost() { return totalCost; }
    public boolean exists() { return documentIds.length > 0; }
}

