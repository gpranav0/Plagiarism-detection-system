package edu.academic.integrity.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
// LocalDateTime and DateTimeFormatter provide human-readable, deterministic log timestamps.
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ActivityLogger {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private final File activityFile;
    private final File errorFile;

    public ActivityLogger(File logDirectory) {
        this.activityFile = new File(logDirectory, "activity.log");
        this.errorFile = new File(logDirectory, "errors.log");
    }

    public synchronized void info(String message) {
        append(activityFile, "INFO", message, null);
    }

    public synchronized void error(String message, Throwable cause) {
        append(errorFile, "ERROR", message, cause);
    }

    private void append(File file, String level, String message, Throwable cause) {
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write('[' + FORMAT.format(LocalDateTime.now()) + "] " + level + " " + message);
            writer.newLine();
            if (cause != null) {
                writer.write("  " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                writer.newLine();
            }
        } catch (IOException ignored) {
            System.err.println("Logging failure: " + ignored.getMessage());
        }
    }

    public File activityFile() { return activityFile; }
    public File errorFile() { return errorFile; }
}

