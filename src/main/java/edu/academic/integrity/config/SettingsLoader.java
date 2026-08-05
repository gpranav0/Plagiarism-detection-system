package edu.academic.integrity.config;

import edu.academic.integrity.exception.ValidationException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class SettingsLoader {
    private SettingsLoader() { }

    public static Settings load(File file) throws ValidationException {
        Settings settings = new Settings();
        if (file == null || !file.exists()) {
            settings.validate();
            return settings;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int equals = trimmed.indexOf('=');
                if (equals < 1 || equals == trimmed.length() - 1) {
                    throw new ValidationException("Malformed setting on line " + number);
                }
                apply(settings, trimmed.substring(0, equals).trim(),
                        trimmed.substring(equals + 1).trim(), number);
            }
            settings.validate();
            return settings;
        } catch (ValidationException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ValidationException("Unable to load settings: " + exception.getMessage(), exception);
        }
    }

    private static void apply(Settings settings, String key, String value, int line)
            throws ValidationException {
        try {
            switch (key) {
                case "enableExact" -> settings.enableExact = parseBoolean(value);
                case "enableShingle" -> settings.enableShingle = parseBoolean(value);
                case "enableFuzzy" -> settings.enableFuzzy = parseBoolean(value);
                case "enableGraph" -> settings.enableGraph = parseBoolean(value);
                case "wordShingleSize" -> settings.wordShingleSize = Integer.parseInt(value);
                case "characterShingleSize" -> settings.characterShingleSize = Integer.parseInt(value);
                case "minExactPhraseCharacters" -> settings.minExactPhraseCharacters = Integer.parseInt(value);
                case "candidateThreshold" -> settings.candidateThreshold = Double.parseDouble(value);
                case "reviewThreshold" -> settings.reviewThreshold = Double.parseDouble(value);
                case "graphEdgeThreshold" -> settings.graphEdgeThreshold = Double.parseDouble(value);
                case "exactWeight" -> settings.exactWeight = Double.parseDouble(value);
                case "shingleWeight" -> settings.shingleWeight = Double.parseDouble(value);
                case "fuzzyWeight" -> settings.fuzzyWeight = Double.parseDouble(value);
                case "graphWeight" -> settings.graphWeight = Double.parseDouble(value);
                case "maxEvidence" -> settings.maxEvidence = Integer.parseInt(value);
                case "workerCount" -> settings.workerCount = Integer.parseInt(value);
                case "maxFileBytes" -> settings.maxFileBytes = Long.parseLong(value);
                case "removeStopwords" -> settings.removeStopwords = parseBoolean(value);
                case "submissionDirectory" -> settings.submissionDirectory = value;
                case "referenceDirectory" -> settings.referenceDirectory = value;
                case "reportDirectory" -> settings.reportDirectory = value;
                case "stopwordFile" -> settings.stopwordFile = value;
                default -> throw new ValidationException("Unknown setting '" + key + "' on line " + line);
            }
        } catch (NumberFormatException exception) {
            throw new ValidationException("Invalid value for '" + key + "' on line " + line, exception);
        }
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false");
    }
}
