package edu.academic.integrity.io;

import edu.academic.integrity.config.Settings;
import edu.academic.integrity.exception.ProjectException;

import java.io.File;

public final class ProjectPaths {
    private final File root;
    public final File submissions;
    public final File references;
    public final File reviewers;
    public final File quarantine;
    public final File stopwords;
    public final File settings;
    public final File reports;
    public final File logs;
    public final File benchmarks;
    public final File indexes;
    public final File documentIndexSnapshot;

    public ProjectPaths(File root) {
        this(root, null);
    }

    /** Resolves configurable corpus/report paths relative to the project root. */
    public ProjectPaths(File root, Settings configured) {
        if (root == null) throw new IllegalArgumentException("root cannot be null");
        this.root = root.getAbsoluteFile();
        this.submissions = resolve(configured == null ? null : configured.submissionDirectory,
                "data/submissions");
        this.references = resolve(configured == null ? null : configured.referenceDirectory,
                "data/references");
        this.reviewers = new File(this.root, "data/reviewers/reviewers.txt");
        this.quarantine = new File(this.root, "data/quarantine");
        this.stopwords = resolve(configured == null ? null : configured.stopwordFile,
                "data/stopwords.txt");
        this.settings = new File(this.root, "config/settings.txt");
        this.reports = resolve(configured == null ? null : configured.reportDirectory,
                "reports");
        this.logs = new File(this.root, "logs");
        this.benchmarks = new File(this.root, "benchmarks");
        this.indexes = new File(this.root, "data/index");
        this.documentIndexSnapshot = new File(this.indexes, "document-index.tsv");
    }

    public File root() { return root; }

    public void createRequiredDirectories() throws ProjectException {
        File[] directories = {
                submissions, references, quarantine, reviewers.getParentFile(),
                settings.getParentFile(), stopwords.getParentFile(), reports, logs, benchmarks, indexes
        };
        for (File directory : directories) {
            if (directory == null) continue;
            if (!directory.exists() && !directory.mkdirs()) {
                throw new ProjectException("Unable to create directory: " + directory);
            }
            if (!directory.isDirectory()) {
                throw new ProjectException("Expected a directory: " + directory);
            }
        }
    }

    private File resolve(String configured, String fallback) {
        String selected = configured == null || configured.isBlank() ? fallback : configured.trim();
        File candidate = new File(selected);
        return (candidate.isAbsolute() ? candidate : new File(root, selected)).getAbsoluteFile();
    }
}
