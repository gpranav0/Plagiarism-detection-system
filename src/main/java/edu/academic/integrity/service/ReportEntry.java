package edu.academic.integrity.service;

import java.io.File;

/** Metadata for an existing generated report. */
public final class ReportEntry {
    private final File file;
    private final String relativePath;
    private final boolean compressed;
    private final long sizeBytes;
    private final long modifiedMillis;

    public ReportEntry(File file, String relativePath, boolean compressed,
            long sizeBytes, long modifiedMillis) {
        if (file == null) throw new IllegalArgumentException("file cannot be null");
        this.file = file.getAbsoluteFile();
        this.relativePath = relativePath == null ? file.getName() : relativePath;
        this.compressed = compressed;
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.modifiedMillis = Math.max(0L, modifiedMillis);
    }

    public File file() { return file; }
    public String relativePath() { return relativePath; }
    public String name() { return file.getName(); }
    public boolean compressed() { return compressed; }
    public long sizeBytes() { return sizeBytes; }
    public long modifiedMillis() { return modifiedMillis; }
}
