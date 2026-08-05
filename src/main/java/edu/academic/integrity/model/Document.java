package edu.academic.integrity.model;

public final class Document {
    private final String id;
    private final String title;
    private final String author;
    private final String filePath;
    private final String content;
    private final DocumentType type;
    private final int sourceLineOffset;
    private String normalizedText;
    private String[] tokens;
    private int[] normalizedToOriginal;

    public Document(String id, String title, String author, String filePath,
                    String content, DocumentType type) {
        this(id, title, author, filePath, content, type, 0);
    }

    public Document(String id, String title, String author, String filePath,
                    String content, DocumentType type, int sourceLineOffset) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Document ID must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("Document content must not be null");
        }
        this.id = id;
        this.title = title == null || title.isBlank() ? id : title;
        this.author = author == null ? "" : author;
        this.filePath = filePath == null ? "" : filePath;
        this.content = content;
        this.type = type;
        this.sourceLineOffset = Math.max(0, sourceLineOffset);
        this.normalizedText = "";
        this.tokens = new String[0];
        this.normalizedToOriginal = new int[0];
    }

    public String id() { return id; }
    public String title() { return title; }
    public String author() { return author; }
    public String filePath() { return filePath; }
    public String content() { return content; }
    public DocumentType type() { return type; }
    public String normalizedText() { return normalizedText; }
    public String[] tokens() { return tokens; }

    public void setPreparedText(String normalizedText, String[] tokens) {
        setPreparedText(normalizedText, tokens, null);
    }

    public void setPreparedText(String normalizedText, String[] tokens, int[] normalizedToOriginal) {
        this.normalizedText = normalizedText == null ? "" : normalizedText;
        this.tokens = tokens == null ? new String[0] : tokens;
        if (normalizedToOriginal == null || normalizedToOriginal.length != this.normalizedText.length()) {
            this.normalizedToOriginal = new int[this.normalizedText.length()];
            for (int i = 0; i < this.normalizedToOriginal.length; i++) {
                this.normalizedToOriginal[i] = Math.min(i, content.length());
            }
        } else {
            this.normalizedToOriginal = new int[normalizedToOriginal.length];
            System.arraycopy(normalizedToOriginal, 0, this.normalizedToOriginal, 0,
                    normalizedToOriginal.length);
        }
    }

    public int originalOffsetForNormalized(int normalizedOffset) {
        if (normalizedToOriginal.length == 0) return 0;
        if (normalizedOffset <= 0) return normalizedToOriginal[0];
        if (normalizedOffset >= normalizedToOriginal.length) return content.length();
        return normalizedToOriginal[normalizedOffset];
    }

    public SourceLocation locate(int characterOffset) {
        int bounded = characterOffset < 0 ? 0 : Math.min(characterOffset, content.length());
        int line = sourceLineOffset + 1;
        int column = 1;
        for (int i = 0; i < bounded; i++) {
            char current = content.charAt(i);
            if (current == '\n') {
                line++;
                column = 1;
            } else if (current == '\r') {
                line++;
                column = 1;
                if (i + 1 < bounded && content.charAt(i + 1) == '\n') i++;
            } else {
                column++;
            }
        }
        return new SourceLocation(bounded, line, column);
    }

    @Override
    public String toString() {
        return id + " (" + title + ")";
    }
}
