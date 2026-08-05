package edu.academic.integrity.service;

import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.model.SimilarityGroup;

/** Array-backed graph projection suitable for a renderer without exposing graph nodes. */
public final class GraphSnapshot {
    private final Node[] nodes;
    private final Edge[] edges;
    private final SimilarityGroup[] groups;
    private final CopyingPath selectedPath;

    public GraphSnapshot(Node[] nodes, Edge[] edges, SimilarityGroup[] groups,
            CopyingPath selectedPath) {
        this.nodes = copy(nodes == null ? new Node[0] : nodes);
        this.edges = copy(edges == null ? new Edge[0] : edges);
        this.groups = copy(groups == null ? new SimilarityGroup[0] : groups);
        this.selectedPath = selectedPath == null
                ? new CopyingPath(new String[0], new double[0], Double.POSITIVE_INFINITY)
                : selectedPath;
    }

    public Node[] nodes() { return copy(nodes); }
    public Edge[] edges() { return copy(edges); }
    public SimilarityGroup[] groups() { return copy(groups); }
    public CopyingPath selectedPath() { return selectedPath; }

    public static final class Node {
        private final String id;
        private final String title;
        private final DocumentType type;
        private final String riskLabel;
        private final String componentId;

        public Node(String id, String title, DocumentType type, String riskLabel,
                String componentId) {
            this.id = id == null ? "" : id;
            this.title = title == null ? this.id : title;
            this.type = type;
            this.riskLabel = riskLabel == null ? "LOW" : riskLabel;
            this.componentId = componentId == null ? "" : componentId;
        }

        public String id() { return id; }
        public String title() { return title; }
        public DocumentType type() { return type; }
        public String riskLabel() { return riskLabel; }
        public String componentId() { return componentId; }
    }

    public static final class Edge {
        private final String firstDocumentId;
        private final String secondDocumentId;
        private final double similarity;
        private final boolean selected;

        public Edge(String firstDocumentId, String secondDocumentId, double similarity,
                boolean selected) {
            this.firstDocumentId = firstDocumentId == null ? "" : firstDocumentId;
            this.secondDocumentId = secondDocumentId == null ? "" : secondDocumentId;
            this.similarity = similarity;
            this.selected = selected;
        }

        public String firstDocumentId() { return firstDocumentId; }
        public String secondDocumentId() { return secondDocumentId; }
        public double similarity() { return similarity; }
        public boolean selected() { return selected; }
    }

    private static Node[] copy(Node[] source) {
        Node[] result = new Node[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static Edge[] copy(Edge[] source) {
        Edge[] result = new Edge[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static SimilarityGroup[] copy(SimilarityGroup[] source) {
        SimilarityGroup[] result = new SimilarityGroup[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
