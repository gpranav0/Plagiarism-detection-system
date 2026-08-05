package edu.academic.integrity.io;

import edu.academic.integrity.exception.ProjectException;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class CorpusImporter {
    private final DocumentFileParser parser;
    private final ActivityLogger logger;
    private final File quarantineDirectory;

    public CorpusImporter(DocumentFileParser parser, ActivityLogger logger, File quarantineDirectory) {
        this.parser = parser;
        this.logger = logger;
        this.quarantineDirectory = quarantineDirectory;
    }

    public ImportSummary importDirectory(File directory, DocumentType type, CorpusStore target) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (target == null) throw new IllegalArgumentException("target cannot be null");
        ImportSummary summary = new ImportSummary();
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            String error = "Import directory is missing or invalid: " + directory;
            summary.addError(error);
            logger.error(error, null);
            return summary;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            String error = "Import directory is inaccessible: " + directory;
            summary.addError(error);
            logger.error(error, null);
            return summary;
        }
        manualSortByName(files);
        for (File file : files) {
            if (!file.isFile()) continue;
            importOne(file, type, target, summary);
        }
        return summary;
    }

    /** Imports one strict UTF-8 text file through the same validation path as directory import. */
    public ImportSummary importFile(File file, DocumentType type, CorpusStore target) {
        ImportSummary summary = new ImportSummary();
        importOne(file, type, target, summary);
        return summary;
    }

    private void importOne(File file, DocumentType type, CorpusStore target,
                           ImportSummary summary) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        if (target == null) throw new IllegalArgumentException("target cannot be null");
        String name = file == null ? "(missing file)" : file.getName();
        try {
            Document document = parser.parse(file, type);
            target.add(document);
            summary.addDocument(document);
            logger.info("Imported " + type + " " + document.id() + " from " + name);
        } catch (Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            summary.addError(name + ": " + message);
            logger.error("Skipped invalid " + type + " file " + name, exception);
            if (file != null) quarantineCopy(file);
        }
    }

    private void manualSortByName(File[] files) {
        for (int i = 1; i < files.length; i++) {
            File value = files[i];
            int j = i - 1;
            while (j >= 0 && files[j].getName().compareToIgnoreCase(value.getName()) > 0) {
                files[j + 1] = files[j];
                j--;
            }
            files[j + 1] = value;
        }
    }

    private void quarantineCopy(File source) {
        if (!source.isFile()) return;
        if (!quarantineDirectory.exists() && !quarantineDirectory.mkdirs()) return;
        File destination = uniqueQuarantineDestination(source.getName());
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException exception) {
            logger.error("Unable to copy invalid file into quarantine: " + source.getName(), exception);
        }
    }

    private File uniqueQuarantineDestination(String sourceName) {
        File first = new File(quarantineDirectory, sourceName + ".invalid-copy");
        if (!first.exists()) return first;
        for (int suffix = 1; suffix < 1_000_000; suffix++) {
            File candidate = new File(quarantineDirectory,
                    sourceName + ".invalid-copy." + suffix);
            if (!candidate.exists()) return candidate;
        }
        return new File(quarantineDirectory,
                sourceName + ".invalid-copy." + System.nanoTime());
    }
}
