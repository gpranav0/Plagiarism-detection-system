package edu.academic.integrity.service;

@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancellationRequested();
}
