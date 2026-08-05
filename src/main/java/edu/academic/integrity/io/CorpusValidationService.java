package edu.academic.integrity.io;

import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.structures.HashSet;

import java.io.File;

public final class CorpusValidationService {
    private final DocumentFileParser parser;
    private final ActivityLogger logger;

    public CorpusValidationService(DocumentFileParser parser, ActivityLogger logger) {
        this.parser = parser;
        this.logger = logger;
    }

    public ValidationSummary validate(File submissions, File references) {
        return validate(new File[]{submissions}, new File[]{references});
    }

    public ValidationSummary validate(File[] submissionDirectories, File[] referenceDirectories) {
        if (submissionDirectories == null || referenceDirectories == null) {
            throw new IllegalArgumentException("Validation directories cannot be null");
        }
        HashSet<String> identifiers = new HashSet<>();
        ErrorAccumulator errors = new ErrorAccumulator();
        int valid = 0;
        for (File source : submissionDirectories) {
            valid += validateSource(source, DocumentType.SUBMISSION, identifiers, errors);
        }
        for (File source : referenceDirectories) {
            valid += validateSource(source, DocumentType.REFERENCE, identifiers, errors);
        }
        logger.info("Validation finished: " + valid + " valid, " + errors.size + " invalid");
        return new ValidationSummary(valid, errors.toArray());
    }

    private int validateSource(File source, DocumentType type, HashSet<String> identifiers,
                               ErrorAccumulator errors) {
        if (source != null && source.isFile()) {
            return validateFile(source, type, identifiers, errors);
        }
        return validateDirectory(source, type, identifiers, errors);
    }

    private int validateDirectory(File directory, DocumentType type, HashSet<String> identifiers,
                                  ErrorAccumulator errors) {
        if (directory == null || !directory.isDirectory()) {
            String message = "Missing directory: " + directory;
            errors.add(message);
            logger.error(message, null);
            return 0;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            String message = "Inaccessible directory: " + directory;
            errors.add(message);
            logger.error(message, null);
            return 0;
        }
        int valid = 0;
        for (File file : files) {
            if (!file.isFile()) continue;
            valid += validateFile(file, type, identifiers, errors);
        }
        return valid;
    }

    private int validateFile(File file, DocumentType type, HashSet<String> identifiers,
                             ErrorAccumulator errors) {
        try {
            Document document = parser.parse(file, type);
            if (!identifiers.add(document.id())) {
                String message = file.getName() + ": duplicate document ID " + document.id();
                errors.add(message);
                logger.error(message, null);
                return 0;
            }
            return 1;
        } catch (Exception exception) {
            errors.add(file.getName() + ": " + exception.getMessage());
            logger.error("Validation rejected " + file.getName(), exception);
            return 0;
        }
    }

    private static final class ErrorAccumulator {
        private String[] values = new String[8];
        private int size;

        void add(String value) {
            if (size == values.length) {
                String[] replacement = new String[values.length * 2];
                System.arraycopy(values, 0, replacement, 0, values.length);
                values = replacement;
            }
            values[size++] = value;
        }

        String[] toArray() {
            String[] result = new String[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
