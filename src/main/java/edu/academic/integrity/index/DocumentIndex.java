package edu.academic.integrity.index;

import edu.academic.integrity.model.Document;
import edu.academic.integrity.structures.BPlusTree;
import edu.academic.integrity.structures.BTree;
import edu.academic.integrity.structures.DynamicArray;

/** Searchable document catalog backed by both B-tree and linked-leaf B+ tree indexes. */
public final class DocumentIndex {
    private static final char KEY_SEPARATOR = '\u0000';
    private static final char RANGE_END = '\u0001';

    private final BTree<String> documentIds;
    private final BPlusTree<String, Document> byId;
    private final BPlusTree<String, Document> byFilePath;

    public DocumentIndex() {
        this(3, 4);
    }

    public DocumentIndex(int bTreeMinimumDegree, int bPlusTreeOrder) {
        documentIds = new BTree<>(bTreeMinimumDegree);
        byId = new BPlusTree<>(bPlusTreeOrder);
        byFilePath = new BPlusTree<>(bPlusTreeOrder);
    }

    /** Adds a document unless its ID is already indexed. */
    public boolean add(Document document) {
        requireDocument(document);
        validateCompositePart(document.id(), "document ID");
        validateCompositePart(document.filePath(), "file path");
        if (byId.containsKey(document.id())) return false;
        documentIds.insert(document.id());
        byId.insert(document.id(), document);
        byFilePath.insert(fileKey(document), document);
        return true;
    }

    /** Adds or replaces a document by ID and returns the former document, if any. */
    public Document put(Document document) {
        requireDocument(document);
        validateCompositePart(document.id(), "document ID");
        validateCompositePart(document.filePath(), "file path");
        Document former = get(document.id());
        if (former != null) remove(document.id());
        add(document);
        return former;
    }

    public Document get(String documentId) {
        requireText(documentId, "documentId");
        return byId.get(documentId);
    }

    public boolean containsId(String documentId) {
        return get(documentId) != null;
    }

    public Document remove(String documentId) {
        requireText(documentId, "documentId");
        Document document = byId.get(documentId);
        if (document == null) return null;
        documentIds.remove(documentId);
        byId.remove(documentId);
        byFilePath.remove(fileKey(document));
        return document;
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public void clear() {
        documentIds.clear();
        byId.clear();
        byFilePath.clear();
    }

    public String[] documentIds() {
        DynamicArray<String> ordered = documentIds.inOrder();
        String[] result = new String[ordered.size()];
        for (int i = 0; i < result.length; i++) result[i] = ordered.get(i);
        return result;
    }

    public Document[] allById() {
        return documents(byId.entries());
    }

    public Document[] allByFilePath() {
        return documents(byFilePath.entries());
    }

    public Document[] rangeById(String firstIdInclusive, String lastIdInclusive) {
        requireText(firstIdInclusive, "firstIdInclusive");
        requireText(lastIdInclusive, "lastIdInclusive");
        return documents(byId.range(firstIdInclusive, lastIdInclusive));
    }

    /** Returns every document whose file path exactly matches the supplied path. */
    public Document[] findByFilePath(String filePath) {
        if (filePath == null) throw new IllegalArgumentException("filePath cannot be null");
        validateCompositePart(filePath, "file path");
        return documents(byFilePath.range(filePath + KEY_SEPARATOR,
                filePath + RANGE_END));
    }

    /** Returns documents ordered by path, then document ID, with both path bounds inclusive. */
    public Document[] rangeByFilePath(String firstPathInclusive, String lastPathInclusive) {
        if (firstPathInclusive == null || lastPathInclusive == null) {
            throw new IllegalArgumentException("file path bounds cannot be null");
        }
        validateCompositePart(firstPathInclusive, "first path");
        validateCompositePart(lastPathInclusive, "last path");
        return documents(byFilePath.range(firstPathInclusive + KEY_SEPARATOR,
                lastPathInclusive + RANGE_END));
    }

    public ValidationSummary validationSummary() {
        boolean bTreeValid = documentIds.validateInvariant();
        boolean idIndexValid = byId.validateInvariant();
        boolean fileIndexValid = byFilePath.validateInvariant();
        boolean sizesMatch = documentIds.size() == byId.size() && byId.size() == byFilePath.size();
        boolean crossReferencesMatch = sizesMatch;
        if (crossReferencesMatch) {
            DynamicArray<BPlusTree.Entry<String, Document>> entries = byId.entries();
            for (int i = 0; i < entries.size(); i++) {
                Document document = entries.get(i).value();
                if (!documentIds.contains(document.id())
                        || byFilePath.get(fileKey(document)) != document) {
                    crossReferencesMatch = false;
                    break;
                }
            }
        }
        return new ValidationSummary(bTreeValid, idIndexValid, fileIndexValid,
                sizesMatch, crossReferencesMatch, size());
    }

    public boolean validateInvariant() {
        return validationSummary().valid();
    }

    private static Document[] documents(DynamicArray<BPlusTree.Entry<String, Document>> entries) {
        Document[] result = new Document[entries.size()];
        for (int i = 0; i < result.length; i++) result[i] = entries.get(i).value();
        return result;
    }

    private static String fileKey(Document document) {
        return document.filePath() + KEY_SEPARATOR + document.id();
    }

    private static void requireDocument(Document document) {
        if (document == null) throw new IllegalArgumentException("document cannot be null");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }

    private static void validateCompositePart(String value, String name) {
        if (value.indexOf(KEY_SEPARATOR) >= 0 || value.indexOf(RANGE_END) >= 0) {
            throw new IllegalArgumentException(name + " contains a reserved index character");
        }
    }

    public static final class ValidationSummary {
        private final boolean bTreeValid;
        private final boolean idIndexValid;
        private final boolean fileIndexValid;
        private final boolean sizesMatch;
        private final boolean crossReferencesMatch;
        private final int indexedDocuments;

        private ValidationSummary(boolean bTreeValid, boolean idIndexValid,
                boolean fileIndexValid, boolean sizesMatch, boolean crossReferencesMatch,
                int indexedDocuments) {
            this.bTreeValid = bTreeValid;
            this.idIndexValid = idIndexValid;
            this.fileIndexValid = fileIndexValid;
            this.sizesMatch = sizesMatch;
            this.crossReferencesMatch = crossReferencesMatch;
            this.indexedDocuments = indexedDocuments;
        }

        public boolean bTreeValid() { return bTreeValid; }
        public boolean idIndexValid() { return idIndexValid; }
        public boolean fileIndexValid() { return fileIndexValid; }
        public boolean sizesMatch() { return sizesMatch; }
        public boolean crossReferencesMatch() { return crossReferencesMatch; }
        public int indexedDocuments() { return indexedDocuments; }
        public boolean valid() {
            return bTreeValid && idIndexValid && fileIndexValid && sizesMatch
                    && crossReferencesMatch;
        }
    }
}
