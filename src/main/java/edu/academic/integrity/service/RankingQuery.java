package edu.academic.integrity.service;

/** Filters and orders the strongest result for each submission. */
public final class RankingQuery {
    private final boolean descending;
    private final String riskLabel;
    private final String documentId;

    public RankingQuery(boolean descending, String riskLabel, String documentId) {
        this.descending = descending;
        this.riskLabel = normalize(riskLabel);
        this.documentId = normalize(documentId);
    }

    public static RankingQuery descending() { return new RankingQuery(true, null, null); }

    public boolean isDescending() { return descending; }
    public String riskLabel() { return riskLabel; }
    public String documentId() { return documentId; }
    public boolean filtersRisk() { return riskLabel != null && !"ALL".equalsIgnoreCase(riskLabel); }
    public boolean filtersDocument() { return documentId != null; }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
