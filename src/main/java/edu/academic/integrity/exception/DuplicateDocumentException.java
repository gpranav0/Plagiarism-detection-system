package edu.academic.integrity.exception;

public final class DuplicateDocumentException extends ProjectException {
    public DuplicateDocumentException(String documentId) {
        super("Duplicate document ID: " + documentId);
    }
}

