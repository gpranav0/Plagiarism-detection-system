package edu.academic.integrity.io;

import edu.academic.integrity.exception.DuplicateDocumentException;
import edu.academic.integrity.model.Document;

public final class CorpusStore {
    private Document[] documents = new Document[16];
    private int size;

    public synchronized void add(Document document) throws DuplicateDocumentException {
        if (find(document.id()) != null) throw new DuplicateDocumentException(document.id());
        if (size == documents.length) grow();
        documents[size++] = document;
    }

    public synchronized Document find(String id) {
        if (id == null) return null;
        for (int i = 0; i < size; i++) {
            if (documents[i].id().equals(id)) return documents[i];
        }
        return null;
    }

    public synchronized Document[] all() {
        Document[] copy = new Document[size];
        System.arraycopy(documents, 0, copy, 0, size);
        return copy;
    }

    public synchronized int size() { return size; }

    /** Removes and returns a document by ID, or {@code null} when it is not loaded. */
    public synchronized Document remove(String id) {
        if (id == null) return null;
        for (int i = 0; i < size; i++) {
            if (!documents[i].id().equals(id)) continue;
            Document removed = documents[i];
            int moved = size - i - 1;
            if (moved > 0) System.arraycopy(documents, i + 1, documents, i, moved);
            documents[--size] = null;
            return removed;
        }
        return null;
    }

    public synchronized void clear() {
        for (int i = 0; i < size; i++) documents[i] = null;
        size = 0;
    }

    private void grow() {
        Document[] replacement = new Document[documents.length * 2];
        System.arraycopy(documents, 0, replacement, 0, size);
        documents = replacement;
    }
}
