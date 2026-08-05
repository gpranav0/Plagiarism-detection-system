package edu.academic.integrity.structures;

/** Project-owned comparison strategy; avoids coupling core structures to library collections. */
@FunctionalInterface
public interface Ordering<T> {
    int compare(T left, T right);
}
