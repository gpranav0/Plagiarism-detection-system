package edu.academic.integrity.model;

public final class SourceLocation {
    private final int offset;
    private final int line;
    private final int column;

    public SourceLocation(int offset, int line, int column) {
        this.offset = offset;
        this.line = line;
        this.column = column;
    }

    public int offset() { return offset; }
    public int line() { return line; }
    public int column() { return column; }

    @Override
    public String toString() {
        return "line " + line + ", column " + column;
    }
}

