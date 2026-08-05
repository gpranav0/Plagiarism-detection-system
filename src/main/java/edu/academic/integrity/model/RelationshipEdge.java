package edu.academic.integrity.model;

public final class RelationshipEdge {
    private final String firstDocumentId;
    private final String secondDocumentId;
    private final double similarity;

    public RelationshipEdge(String firstDocumentId, String secondDocumentId, double similarity) {
        this.firstDocumentId = firstDocumentId;
        this.secondDocumentId = secondDocumentId;
        this.similarity = similarity;
    }

    public String firstDocumentId() { return firstDocumentId; }
    public String secondDocumentId() { return secondDocumentId; }
    public double similarity() { return similarity; }
}

