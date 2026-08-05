package edu.academic.integrity.index;

import edu.academic.integrity.io.ActivityLogger;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.structures.BPlusTree;
import edu.academic.integrity.structures.BTree;
import edu.academic.integrity.structures.DynamicArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
// Explicit encoders/decoders reject malformed UTF-8 instead of replacing corrupt text.
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
// NIO move options provide temporary-write-then-atomic-replace snapshot recovery.
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Disk-backed document catalog whose searchable state is maintained by the
 * project's custom B-tree and B+ tree. Every successful {@link #put(Document)}
 * writes a deterministic, versioned snapshot before returning.
 */
public final class PersistentDocumentIndex {
    private static final String MAGIC = "APDS_DOCUMENT_INDEX";
    private static final int FORMAT_VERSION = 1;
    private static final int B_TREE_MINIMUM_DEGREE = 3;
    private static final int B_PLUS_TREE_ORDER = 4;

    private final File snapshotFile;
    private final ActivityLogger logger;
    private BTree<String> identifiers = new BTree<>(B_TREE_MINIMUM_DEGREE);
    private BPlusTree<String, Record> records = new BPlusTree<>(B_PLUS_TREE_ORDER);
    private boolean writesAllowed = true;

    /**
     * Opens an existing snapshot or starts an empty catalog when none exists.
     * A valid interrupted-write temporary snapshot is recovered automatically.
     * An unrecoverable malformed primary is moved aside before future writes.
     */
    public PersistentDocumentIndex(File file, ActivityLogger logger) {
        if (file == null) throw new IllegalArgumentException("file cannot be null");
        if (logger == null) throw new IllegalArgumentException("logger cannot be null");
        snapshotFile = file.getAbsoluteFile();
        this.logger = logger;
        loadOrRecover();
    }

    /**
     * Adds or replaces one document record and persists the complete catalog.
     * The former immutable record is returned, or {@code null} for a new ID.
     * If persistence fails, the in-memory mutation is rolled back.
     */
    public synchronized Record put(Document document) {
        requireDocument(document);
        if (!writesAllowed) {
            throw new IllegalStateException(
                    "Persistent index writes are disabled because recovery could not preserve the snapshot");
        }

        Record replacement = new Record(document.id(), document.type(), document.filePath());
        Record former = records.put(replacement.id(), replacement);
        boolean insertedIdentifier = false;
        if (former == null) insertedIdentifier = identifiers.insert(replacement.id());
        try {
            if (!validateInvariant()) {
                throw new IOException("Custom tree invariants failed before snapshot write");
            }
            saveSnapshot();
            return former;
        } catch (IOException | RuntimeException failure) {
            rollback(replacement.id(), former, insertedIdentifier);
            logger.error("Unable to persist document index; in-memory update was rolled back", failure);
            throw new IllegalStateException("Unable to persist document index", failure);
        }
    }

    /** Removes one record and persists the new snapshot before returning. */
    public synchronized Record remove(String documentId) {
        requireDocumentId(documentId);
        requireWritesAllowed();
        Record former = records.get(documentId);
        if (former == null) return null;
        if (!validateInvariant()) {
            throw new IllegalStateException("Persistent document index is invalid before removal");
        }

        records.remove(documentId);
        identifiers.remove(documentId);
        try {
            if (!validateInvariant()) {
                throw new IOException("Custom tree invariants failed before snapshot write");
            }
            saveSnapshot();
            return former;
        } catch (IOException | RuntimeException failure) {
            records.put(documentId, former);
            identifiers.insert(documentId);
            logger.error("Unable to persist document-index removal; mutation was rolled back",
                    failure);
            throw new IllegalStateException("Unable to persist document-index removal", failure);
        }
    }

    /** Clears the catalog atomically, restoring the prior custom trees if persistence fails. */
    public synchronized void clear() {
        requireWritesAllowed();
        if (records.isEmpty()) return;
        BTree<String> formerIdentifiers = identifiers;
        BPlusTree<String, Record> formerRecords = records;
        resetEmpty();
        try {
            saveSnapshot();
        } catch (IOException | RuntimeException failure) {
            identifiers = formerIdentifiers;
            records = formerRecords;
            logger.error("Unable to clear persistent document index; mutation was rolled back",
                    failure);
            throw new IllegalStateException("Unable to clear persistent document index", failure);
        }
    }

    /** Returns the immutable record for an ID, or {@code null} when absent. */
    public synchronized Record get(String documentId) {
        requireDocumentId(documentId);
        return records.get(documentId);
    }

    public synchronized int size() {
        return records.size();
    }

    /** Validates both custom trees and their key/value cross-references. */
    public synchronized boolean validateInvariant() {
        if (!identifiers.validateInvariant() || !records.validateInvariant()
                || identifiers.size() != records.size()) {
            return false;
        }
        DynamicArray<BPlusTree.Entry<String, Record>> entries = records.entries();
        if (entries.size() != identifiers.size()) return false;
        for (int i = 0; i < entries.size(); i++) {
            BPlusTree.Entry<String, Record> entry = entries.get(i);
            Record record = entry.value();
            if (!identifiers.contains(entry.key()) || record == null
                    || !entry.key().equals(record.id()) || record.type() == null
                    || record.sourcePath() == null) {
                return false;
            }
        }
        return true;
    }

    /** Returns false when any cataloged source path is missing, unreadable, or not a file. */
    public synchronized boolean backingFilesAccessible() {
        DynamicArray<BPlusTree.Entry<String, Record>> entries = records.entries();
        for (int i = 0; i < entries.size(); i++) {
            File source = new File(entries.get(i).value().sourcePath());
            if (!source.isFile() || !source.canRead()) return false;
        }
        return true;
    }

    public File file() {
        return snapshotFile;
    }

    private void requireWritesAllowed() {
        if (!writesAllowed) {
            throw new IllegalStateException(
                    "Persistent index writes are disabled because recovery could not preserve the snapshot");
        }
    }

    private void loadOrRecover() {
        File temporary = temporaryFile();
        if (snapshotFile.isFile()) {
            try {
                install(readSnapshot(snapshotFile));
                cleanupStaleTemporary(temporary);
                logger.info("Loaded persistent document index with " + size()
                        + " record(s) from " + snapshotFile);
                return;
            } catch (IOException | RuntimeException malformed) {
                logger.error("Persistent document-index snapshot is malformed: " + snapshotFile,
                        malformed);
                if (recoverTemporary(temporary)) return;
                writesAllowed = isolateMalformed(snapshotFile);
                resetEmpty();
                return;
            }
        }

        if (snapshotFile.exists()) {
            logger.error("Persistent document-index path is not a regular file: " + snapshotFile,
                    null);
            writesAllowed = false;
            return;
        }
        recoverTemporary(temporary);
    }

    private boolean recoverTemporary(File temporary) {
        if (!temporary.isFile()) return false;
        try {
            LoadedSnapshot recovered = readSnapshot(temporary);
            replace(temporary, snapshotFile);
            install(recovered);
            logger.info("Recovered persistent document index from temporary snapshot " + temporary);
            return true;
        } catch (IOException | RuntimeException failure) {
            logger.error("Persistent document-index recovery failed for " + temporary, failure);
            isolateMalformed(temporary);
            return false;
        }
    }

    private boolean isolateMalformed(File malformed) {
        if (!malformed.exists()) return true;
        if (!malformed.isFile()) return false;
        File destination = recoveryCopyFile(malformed);
        try {
            replaceWithoutOverwrite(malformed, destination);
            logger.info("Isolated malformed document-index snapshot at " + destination);
            return true;
        } catch (IOException failure) {
            logger.error("Persistent document-index recovery could not isolate " + malformed,
                    failure);
            return false;
        }
    }

    private LoadedSnapshot readSnapshot(File source) throws IOException {
        if (!source.isFile() || !source.canRead()) {
            throw new IOException("Snapshot is missing or inaccessible: " + source);
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(source), decoder))) {
            requireLine(reader.readLine(), MAGIC, "magic header");
            requireLine(reader.readLine(), "VERSION\t" + FORMAT_VERSION, "format version");
            String countLine = reader.readLine();
            if (countLine == null || !countLine.startsWith("COUNT\t")) {
                throw format("Missing record count");
            }
            int expectedCount = parseCount(countLine.substring(6));
            BTree<String> loadedIds = new BTree<>(B_TREE_MINIMUM_DEGREE);
            BPlusTree<String, Record> loadedRecords = new BPlusTree<>(B_PLUS_TREE_ORDER);
            String previousId = null;
            for (int index = 0; index < expectedCount; index++) {
                String line = reader.readLine();
                if (line == null) throw format("Snapshot ended before record " + (index + 1));
                Record record = parseRecord(line, index + 1);
                if (previousId != null && previousId.compareTo(record.id()) >= 0) {
                    throw format("Record IDs must be unique and strictly ordered");
                }
                if (!loadedIds.insert(record.id())
                        || !loadedRecords.insert(record.id(), record)) {
                    throw format("Duplicate document ID: " + record.id());
                }
                previousId = record.id();
            }
            if (reader.readLine() != null) throw format("Unexpected data after declared records");
            if (!loadedIds.validateInvariant() || !loadedRecords.validateInvariant()
                    || loadedIds.size() != expectedCount || loadedRecords.size() != expectedCount) {
                throw format("Loaded tree invariants do not match the declared count");
            }
            return new LoadedSnapshot(loadedIds, loadedRecords);
        }
    }

    private Record parseRecord(String line, int recordNumber) throws IOException {
        int first = nextTab(line, 0);
        int second = first < 0 ? -1 : nextTab(line, first + 1);
        int third = second < 0 ? -1 : nextTab(line, second + 1);
        if (first < 0 || second < 0 || third < 0 || nextTab(line, third + 1) >= 0
                || !"RECORD".equals(line.substring(0, first))) {
            throw format("Malformed record " + recordNumber);
        }
        String id = unescape(line.substring(first + 1, second), "record ID");
        String typeText = line.substring(second + 1, third);
        String sourcePath = unescape(line.substring(third + 1), "source path");
        requireDocumentId(id);
        DocumentType type = parseType(typeText);
        requireWellFormedText(sourcePath, "source path", true);
        return new Record(id, type, sourcePath);
    }

    private void saveSnapshot() throws IOException {
        File parent = snapshotFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create snapshot directory: " + parent);
        }
        if (parent != null && !parent.isDirectory()) {
            throw new IOException("Snapshot parent is not a directory: " + parent);
        }

        File temporary = temporaryFile();
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(temporary), encoder))) {
                writer.write(MAGIC);
                writer.newLine();
                writer.write("VERSION\t" + FORMAT_VERSION);
                writer.newLine();
                writer.write("COUNT\t" + records.size());
                writer.newLine();
                DynamicArray<BPlusTree.Entry<String, Record>> entries = records.entries();
                for (int i = 0; i < entries.size(); i++) {
                    Record record = entries.get(i).value();
                    writer.write("RECORD\t");
                    writer.write(escape(record.id()));
                    writer.write('\t');
                    writer.write(record.type().name());
                    writer.write('\t');
                    writer.write(escape(record.sourcePath()));
                    writer.newLine();
                }
            }
            replace(temporary, snapshotFile);
        } catch (IOException | RuntimeException failure) {
            cleanupFailedTemporary(temporary);
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Unable to encode persistent document index", failure);
        }
    }

    private void rollback(String id, Record former, boolean insertedIdentifier) {
        if (former == null) {
            records.remove(id);
            if (insertedIdentifier) identifiers.remove(id);
        } else {
            records.put(id, former);
        }
        if (!validateInvariant()) {
            logger.error("Persistent document-index rollback left invalid custom tree state", null);
        }
    }

    private void install(LoadedSnapshot loaded) {
        identifiers = loaded.identifiers;
        records = loaded.records;
        writesAllowed = true;
    }

    private void resetEmpty() {
        identifiers = new BTree<>(B_TREE_MINIMUM_DEGREE);
        records = new BPlusTree<>(B_PLUS_TREE_ORDER);
    }

    private void cleanupStaleTemporary(File temporary) {
        if (temporary.exists() && !temporary.delete()) {
            logger.error("Persistent document-index recovery could not remove stale temporary file "
                    + temporary, null);
        }
    }

    private void cleanupFailedTemporary(File temporary) {
        if (temporary.exists() && !temporary.delete()) {
            logger.error("Persistent document-index recovery could not remove incomplete snapshot "
                    + temporary, null);
        }
    }

    private File temporaryFile() {
        return new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
    }

    private File recoveryCopyFile(File source) {
        File parent = source.getParentFile();
        String base = source.getName() + ".invalid-copy";
        File first = new File(parent, base);
        if (!first.exists()) return first;
        for (int suffix = 1; suffix < 1_000_000; suffix++) {
            File candidate = new File(parent, base + '.' + suffix);
            if (!candidate.exists()) return candidate;
        }
        return new File(parent, base + '.' + System.nanoTime());
    }

    private void replace(File temporary, File destination) throws IOException {
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void replaceWithoutOverwrite(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source.toPath(), destination.toPath());
        }
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        escaped.append("\\u");
                        appendHex(escaped, character);
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String unescape(String value, String field) throws IOException {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character != '\\') {
                if (character < 0x20 || character == 0x7f) {
                    throw format("Unescaped control character in " + field);
                }
                decoded.append(character);
                continue;
            }
            if (++i >= value.length()) throw format("Incomplete escape in " + field);
            char escape = value.charAt(i);
            switch (escape) {
                case '\\' -> decoded.append('\\');
                case 't' -> decoded.append('\t');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw format("Incomplete Unicode escape in " + field);
                    int decodedValue = 0;
                    for (int digit = 0; digit < 4; digit++) {
                        decodedValue = (decodedValue << 4) | hex(value.charAt(++i), field);
                    }
                    decoded.append((char) decodedValue);
                }
                default -> throw format("Unknown escape in " + field + ": \\" + escape);
            }
        }
        String result = decoded.toString();
        requireWellFormedText(result, field, true);
        return result;
    }

    private void appendHex(StringBuilder destination, char value) {
        final char[] digits = "0123456789ABCDEF".toCharArray();
        destination.append(digits[(value >>> 12) & 0xf]);
        destination.append(digits[(value >>> 8) & 0xf]);
        destination.append(digits[(value >>> 4) & 0xf]);
        destination.append(digits[value & 0xf]);
    }

    private int hex(char value, String field) throws IOException {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'a' && value <= 'f') return value - 'a' + 10;
        if (value >= 'A' && value <= 'F') return value - 'A' + 10;
        throw format("Invalid Unicode escape in " + field);
    }

    private int nextTab(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (value.charAt(i) == '\t') return i;
        }
        return -1;
    }

    private int parseCount(String value) throws IOException {
        if (value.isEmpty()) throw format("Record count is empty");
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') throw format("Record count is not a non-negative integer");
            if (count > (Integer.MAX_VALUE - (digit - '0')) / 10) {
                throw format("Record count exceeds the supported integer range");
            }
            count = count * 10 + digit - '0';
        }
        return count;
    }

    private DocumentType parseType(String value) throws IOException {
        if (DocumentType.SUBMISSION.name().equals(value)) return DocumentType.SUBMISSION;
        if (DocumentType.REFERENCE.name().equals(value)) return DocumentType.REFERENCE;
        throw format("Unknown document type: " + value);
    }

    private void requireLine(String actual, String expected, String description) throws IOException {
        if (!expected.equals(actual)) throw format("Invalid " + description);
    }

    private static void requireDocument(Document document) {
        if (document == null) throw new IllegalArgumentException("document cannot be null");
        requireDocumentId(document.id());
        if (document.type() == null) throw new IllegalArgumentException("document type cannot be null");
        requireWellFormedText(document.filePath(), "source path", true);
    }

    private static void requireDocumentId(String id) {
        requireWellFormedText(id, "document ID", false);
    }

    private static void requireWellFormedText(String value, String name, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isBlank())) {
            throw new IllegalArgumentException(name + " cannot be " + (allowEmpty ? "null" : "blank"));
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException(name + " contains an unpaired surrogate");
                }
                i++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(name + " contains an unpaired surrogate");
            }
        }
    }

    private IOException format(String message) {
        return new IOException("Malformed persistent document-index snapshot: " + message);
    }

    /** Immutable catalog value stored in the custom B+ tree. */
    public static final class Record {
        private final String id;
        private final DocumentType type;
        private final String sourcePath;

        private Record(String id, DocumentType type, String sourcePath) {
            this.id = id;
            this.type = type;
            this.sourcePath = sourcePath;
        }

        public String id() { return id; }
        public DocumentType type() { return type; }
        public String sourcePath() { return sourcePath; }

        @Override
        public String toString() {
            return id + " [" + type + "] " + sourcePath;
        }
    }

    private static final class LoadedSnapshot {
        private final BTree<String> identifiers;
        private final BPlusTree<String, Record> records;

        private LoadedSnapshot(BTree<String> identifiers,
                BPlusTree<String, Record> records) {
            this.identifiers = identifiers;
            this.records = records;
        }
    }
}
