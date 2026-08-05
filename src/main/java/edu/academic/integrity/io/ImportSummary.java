package edu.academic.integrity.io;

import edu.academic.integrity.model.Document;

public final class ImportSummary {
    private Document[] documents = new Document[8];
    private String[] errors = new String[8];
    private int documentCount;
    private int errorCount;

    void addDocument(Document document) {
        if (documentCount == documents.length) documents = grow(documents);
        documents[documentCount++] = document;
    }

    void addError(String error) {
        if (errorCount == errors.length) errors = grow(errors);
        errors[errorCount++] = error;
    }

    public Document[] documents() {
        Document[] copy = new Document[documentCount];
        System.arraycopy(documents, 0, copy, 0, documentCount);
        return copy;
    }

    public String[] errors() {
        String[] copy = new String[errorCount];
        System.arraycopy(errors, 0, copy, 0, errorCount);
        return copy;
    }

    public int importedCount() { return documentCount; }
    public int errorCount() { return errorCount; }

    /** Appends a defensive snapshot of another import result. */
    public void include(ImportSummary other) {
        if (other == null) return;
        for (Document document : other.documents()) addDocument(document);
        for (String error : other.errors()) addError(error);
    }

    private Document[] grow(Document[] source) {
        Document[] result = new Document[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private String[] grow(String[] source) {
        String[] result = new String[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
