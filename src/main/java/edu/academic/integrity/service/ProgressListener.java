package edu.academic.integrity.service;

@FunctionalInterface
public interface ProgressListener {
    ProgressListener NONE = update -> { };

    void onProgress(ProgressUpdate update);
}
