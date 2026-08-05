package edu.academic.integrity.io;

public final class ValidationSummary {
    private final int validFiles;
    private final String[] errors;

    public ValidationSummary(int validFiles, String[] errors) {
        this.validFiles = validFiles;
        this.errors = errors;
    }

    public int validFiles() { return validFiles; }
    public int invalidFiles() { return errors.length; }

    public String[] errors() {
        String[] copy = new String[errors.length];
        System.arraycopy(errors, 0, copy, 0, errors.length);
        return copy;
    }
}

