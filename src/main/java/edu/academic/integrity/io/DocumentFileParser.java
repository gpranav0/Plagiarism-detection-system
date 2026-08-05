package edu.academic.integrity.io;

import edu.academic.integrity.exception.ValidationException;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
// CharsetDecoder is required to reject malformed UTF-8 instead of silently replacing corrupt bytes.
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class DocumentFileParser {
    private final long maxFileBytes;

    public DocumentFileParser(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public Document parse(File file, DocumentType type) throws ValidationException {
        validateBasic(file);
        String raw = readUtf8(file);
        if (raw.isBlank()) throw new ValidationException("File is empty: " + file.getName());

        String fallbackId = file.getName().substring(0, file.getName().length() - 4);
        Metadata metadata = extractMetadata(raw, fallbackId);
        if (metadata.content.isBlank()) {
            throw new ValidationException("Document body is empty: " + file.getName());
        }
        if (!containsSearchableCharacter(metadata.content)) {
            throw new ValidationException("Document body has no searchable letters or digits: "
                    + file.getName());
        }
        return new Document(metadata.id, metadata.title, metadata.author,
                file.getAbsolutePath(), metadata.content, type, metadata.sourceLineOffset);
    }

    private void validateBasic(File file) throws ValidationException {
        if (file == null || !file.exists()) throw new ValidationException("File does not exist");
        if (!file.isFile()) throw new ValidationException("Not a regular file: " + file);
        if (!file.canRead()) throw new ValidationException("File is inaccessible: " + file.getName());
        if (!file.getName().toLowerCase().endsWith(".txt")) {
            throw new ValidationException("Unsupported file type: " + file.getName());
        }
        if (file.length() == 0) throw new ValidationException("File is empty: " + file.getName());
        if (file.length() > maxFileBytes) {
            throw new ValidationException("File exceeds maximum size: " + file.getName());
        }
    }

    private String readUtf8(File file) throws ValidationException {
        StringBuilder builder = new StringBuilder((int) Math.min(file.length(), 65_536L));
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), decoder))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) builder.append(buffer, 0, count);
            return builder.toString();
        } catch (IOException exception) {
            throw new ValidationException("Unable to read valid UTF-8 from " + file.getName(), exception);
        }
    }

    private Metadata extractMetadata(String raw, String fallbackId) throws ValidationException {
        String id = fallbackId;
        String title = fallbackId;
        String author = "";
        int initialStart = !raw.isEmpty() && raw.charAt(0) == '\uFEFF' ? 1 : 0;
        int cursor = initialStart;
        int contentStart = initialStart;
        boolean foundMetadata = false;
        while (cursor < raw.length()) {
            int end = cursor;
            while (end < raw.length() && raw.charAt(end) != '\n' && raw.charAt(end) != '\r') end++;
            String line = raw.substring(cursor, end).trim();
            int next = end;
            if (next < raw.length() && raw.charAt(next) == '\r') next++;
            if (next < raw.length() && raw.charAt(next) == '\n') next++;
            if (line.isEmpty()) {
                if (foundMetadata) contentStart = next;
                break;
            }
            String value;
            if ((value = prefixedValue(line, "ID:")) != null) {
                id = value;
                foundMetadata = true;
            } else if ((value = prefixedValue(line, "TITLE:")) != null) {
                title = value;
                foundMetadata = true;
            } else if ((value = prefixedValue(line, "AUTHOR:")) != null) {
                author = value;
                foundMetadata = true;
            } else {
                if (foundMetadata) contentStart = cursor;
                break;
            }
            cursor = next;
            contentStart = next;
        }
        if (id.isBlank()) throw new ValidationException("Document ID is blank");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                throw new ValidationException("Document ID contains an invalid character: " + id);
            }
        }
        String content = raw.substring(Math.min(contentStart, raw.length()));
        int sourceLineOffset = lineCountBefore(raw, Math.min(contentStart, raw.length()));
        return new Metadata(id, title, author, content, sourceLineOffset);
    }

    private String prefixedValue(String line, String prefix) {
        if (!line.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        return line.substring(prefix.length()).trim();
    }

    private boolean containsSearchableCharacter(String content) {
        for (int i = 0; i < content.length();) {
            int codePoint = content.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private int lineCountBefore(String text, int endExclusive) {
        int lines = 0;
        for (int i = 0; i < endExclusive; i++) {
            char current = text.charAt(i);
            if (current == '\n') {
                lines++;
            } else if (current == '\r') {
                lines++;
                if (i + 1 < endExclusive && text.charAt(i + 1) == '\n') i++;
            }
        }
        return lines;
    }

    private static final class Metadata {
        private final String id;
        private final String title;
        private final String author;
        private final String content;
        private final int sourceLineOffset;

        private Metadata(String id, String title, String author, String content,
                         int sourceLineOffset) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.content = content;
            this.sourceLineOffset = sourceLineOffset;
        }
    }
}
