package edu.academic.integrity.service;

/** A UI-neutral progress event; completed/total may describe phases or work items. */
public final class ProgressUpdate {
    private final String phase;
    private final int completed;
    private final int total;
    private final String message;

    public ProgressUpdate(String phase, int completed, int total, String message) {
        if (completed < 0 || total < 0 || completed > total) {
            throw new IllegalArgumentException("Progress counts are inconsistent");
        }
        this.phase = phase == null ? "" : phase;
        this.completed = completed;
        this.total = total;
        this.message = message == null ? "" : message;
    }

    public String phase() { return phase; }
    public int completed() { return completed; }
    public int total() { return total; }
    public String message() { return message; }
    public int percent() { return total == 0 ? 0 : (int) ((long) completed * 100L / total); }
}
