package edu.academic.integrity.io;

import edu.academic.integrity.model.Reviewer;
import edu.academic.integrity.structures.HashSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class ReviewerLoader {
    private ReviewerLoader() { }

    public static Reviewer[] load(File file, ActivityLogger logger) {
        if (file == null || !file.exists()) return new Reviewer[0];
        Reviewer[] reviewers = new Reviewer[8];
        int size = 0;
        HashSet<String> identifiers = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = splitCsv(trimmed);
                try {
                    if (parts.length != 3) throw new IllegalArgumentException("Expected id,name,capacity");
                    Reviewer reviewer = new Reviewer(parts[0].trim(), parts[1].trim(),
                            Integer.parseInt(parts[2].trim()));
                    if (!identifiers.add(reviewer.id())) {
                        throw new IllegalArgumentException("Duplicate reviewer ID: " + reviewer.id());
                    }
                    if (size == reviewers.length) {
                        Reviewer[] replacement = new Reviewer[reviewers.length * 2];
                        System.arraycopy(reviewers, 0, replacement, 0, size);
                        reviewers = replacement;
                    }
                    reviewers[size++] = reviewer;
                } catch (RuntimeException exception) {
                    logger.error("Invalid reviewer on line " + lineNumber, exception);
                }
            }
        } catch (IOException exception) {
            logger.error("Unable to load reviewers", exception);
        }
        Reviewer[] result = new Reviewer[size];
        System.arraycopy(reviewers, 0, result, 0, size);
        return result;
    }

    private static String[] splitCsv(String line) {
        int commas = 0;
        for (int i = 0; i < line.length(); i++) if (line.charAt(i) == ',') commas++;
        String[] parts = new String[commas + 1];
        int start = 0;
        int part = 0;
        for (int i = 0; i <= line.length(); i++) {
            if (i == line.length() || line.charAt(i) == ',') {
                parts[part++] = line.substring(start, i);
                start = i + 1;
            }
        }
        return parts;
    }
}
