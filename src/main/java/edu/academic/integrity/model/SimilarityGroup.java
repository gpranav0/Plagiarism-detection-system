package edu.academic.integrity.model;

public final class SimilarityGroup {
    private final String id;
    private final String[] documentIds;

    public SimilarityGroup(String id, String[] documentIds) {
        this.id = id;
        this.documentIds = documentIds;
    }

    public String id() { return id; }

    public String[] documentIds() {
        String[] result = new String[documentIds.length];
        System.arraycopy(documentIds, 0, result, 0, documentIds.length);
        return result;
    }
}

