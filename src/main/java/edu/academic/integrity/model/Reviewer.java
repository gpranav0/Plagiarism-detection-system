package edu.academic.integrity.model;

public final class Reviewer {
    private final String id;
    private final String name;
    private final int capacity;

    public Reviewer(String id, String name, int capacity) {
        if (id == null || id.isBlank() || capacity < 0) {
            throw new IllegalArgumentException("Reviewer requires an ID and non-negative capacity");
        }
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.capacity = capacity;
    }

    public String id() { return id; }
    public String name() { return name; }
    public int capacity() { return capacity; }
}

