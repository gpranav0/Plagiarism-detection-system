package edu.academic.integrity.exception;

public final class ValidationException extends ProjectException {
    public ValidationException(String message) { super(message); }
    public ValidationException(String message, Throwable cause) { super(message, cause); }
}

