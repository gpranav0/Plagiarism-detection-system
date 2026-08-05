package edu.academic.integrity.analytics;

import edu.academic.integrity.algorithms.graph.MinimumSpanningTree;
import edu.academic.integrity.algorithms.graph.ShortestPaths;
import edu.academic.integrity.algorithms.graph.WeightedGraph;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.RelationshipEdge;
import edu.academic.integrity.model.ScoreBreakdown;
import edu.academic.integrity.model.SimilarityGroup;

/** Observable similarity grouping, traversal, compact-link, and path features. */
public final class SimilarityNetwork {
    private final WeightedGraph<String> graph = new WeightedGraph<>(false);
    private double[][] similarities;

    public SimilarityNetwork(AnalysisResult[] results, double edgeThreshold) {
        this(null, results, edgeThreshold);
    }

    /** Includes every loaded document so non-candidate files remain visible as isolated vertices. */
    public SimilarityNetwork(Document[] documents, AnalysisResult[] results, double edgeThreshold) {
        if (results == null) throw new IllegalArgumentException("results cannot be null");
        if (documents != null) {
            for (Document document : documents) {
                if (document != null) addVertex(document.id());
            }
        }
        for (AnalysisResult result : results) {
            addVertex(result.submission().id());
            addVertex(result.reference().id());
        }
        similarities = new double[graph.vertexCount()][graph.vertexCount()];
        for (AnalysisResult result : results) {
            double similarity = result.score().total();
            if (similarity < edgeThreshold) continue;
            int first = graph.indexOf(result.submission().id());
            int second = graph.indexOf(result.reference().id());
            if (similarity > similarities[first][second]) {
                similarities[first][second] = similarity;
                similarities[second][first] = similarity;
                double pathCost = Math.max(0.000001, 1.0 - similarity);
                if (graph.hasEdge(first, second)) graph.removeEdge(first, second);
                graph.addEdge(first, second, pathCost);
            }
        }
    }

    private void addVertex(String documentId) {
        if (!graph.containsVertex(documentId)) graph.addVertex(documentId);
    }

    public int vertexCount() { return graph.vertexCount(); }
    public int relationshipCount() { return graph.edgeCount(); }
    public boolean validateInvariants() { return graph.validateInvariants(); }

    public String[] breadthFirst(String startId) {
        int start = graph.indexOf(startId);
        if (start < 0) return new String[0];
        WeightedGraph.Traversal<String> traversal = graph.breadthFirst(start);
        String[] values = new String[traversal.size()];
        for (int i = 0; i < values.length; i++) values[i] = traversal.valueAt(i);
        return values;
    }

    public String[] depthFirst(String startId) {
        int start = graph.indexOf(startId);
        if (start < 0) return new String[0];
        WeightedGraph.Traversal<String> traversal = graph.depthFirst(start);
        String[] values = new String[traversal.size()];
        for (int i = 0; i < values.length; i++) values[i] = traversal.valueAt(i);
        return values;
    }

    public SimilarityGroup[] groups() {
        WeightedGraph.Components components = graph.unionFindComponents();
        int count = 0;
        for (int component = 0; component < components.count(); component++) {
            if (components.sizeOf(component) > 1) count++;
        }
        SimilarityGroup[] groups = new SimilarityGroup[count];
        int output = 0;
        for (int component = 0; component < components.count(); component++) {
            if (components.sizeOf(component) <= 1) continue;
            String[] ids = new String[components.sizeOf(component)];
            for (int position = 0; position < ids.length; position++) {
                ids[position] = graph.vertexAt(components.vertexAt(component, position));
            }
            groups[output] = new SimilarityGroup("GROUP-" + (output + 1), ids);
            output++;
        }
        return groups;
    }

    public CopyingPath shortestRelationshipPath(String fromId, String toId) {
        int from = graph.indexOf(fromId);
        int to = graph.indexOf(toId);
        if (from < 0 || to < 0) return new CopyingPath(new String[0], new double[0], Double.POSITIVE_INFINITY);
        ShortestPaths.SingleSourceResult result = ShortestPaths.dijkstra(graph, from);
        int[] indexes = result.pathTo(to);
        if (indexes.length == 0) return new CopyingPath(new String[0], new double[0], Double.POSITIVE_INFINITY);
        String[] ids = new String[indexes.length];
        double[] edgeSimilarities = new double[Math.max(0, indexes.length - 1)];
        for (int i = 0; i < indexes.length; i++) {
            ids[i] = graph.vertexAt(indexes[i]);
            if (i > 0) edgeSimilarities[i - 1] = similarities[indexes[i - 1]][indexes[i]];
        }
        return new CopyingPath(ids, edgeSimilarities, result.distanceTo(to));
    }

    public RelationshipEdge[] compactRelationships() {
        MinimumSpanningTree.Result tree = MinimumSpanningTree.kruskal(graph);
        MinimumSpanningTree.WeightedEdge[] edges = tree.edges();
        RelationshipEdge[] relationships = new RelationshipEdge[edges.length];
        for (int i = 0; i < edges.length; i++) {
            relationships[i] = new RelationshipEdge(graph.vertexAt(edges[i].from()),
                    graph.vertexAt(edges[i].to()), similarities[edges[i].from()][edges[i].to()]);
        }
        return relationships;
    }

    /** Returns every threshold-qualified undirected relationship exactly once. */
    public RelationshipEdge[] relationships() {
        int count = 0;
        for (int from = 0; from < graph.vertexCount(); from++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(from); edge != null;
                 edge = edge.next()) {
                if (from < edge.to()) count++;
            }
        }
        RelationshipEdge[] result = new RelationshipEdge[count];
        int output = 0;
        for (int from = 0; from < graph.vertexCount(); from++) {
            for (WeightedGraph.Edge edge = graph.firstEdge(from); edge != null;
                 edge = edge.next()) {
                if (from >= edge.to()) continue;
                result[output++] = new RelationshipEdge(graph.vertexAt(from),
                        graph.vertexAt(edge.to()), similarities[from][edge.to()]);
            }
        }
        return result;
    }

    public int degree(String documentId) {
        int index = graph.indexOf(documentId);
        if (index < 0) return 0;
        int degree = 0;
        for (WeightedGraph.Edge edge = graph.firstEdge(index); edge != null; edge = edge.next()) degree++;
        return degree;
    }

    public AnalysisResult[] enrichGraphSignals(AnalysisResult[] results, Settings settings) {
        if (settings == null) throw new IllegalArgumentException("settings cannot be null");
        settings.validate();
        AnalysisResult[] enriched = new AnalysisResult[results.length];
        int denominator = Math.max(1, graph.vertexCount() - 1);
        for (int i = 0; i < results.length; i++) {
            AnalysisResult result = results[i];
            int first = graph.indexOf(result.submission().id());
            int second = graph.indexOf(result.reference().id());
            double signal = 0.0;
            if (first >= 0 && second >= 0 && graph.hasEdge(first, second)) {
                double density = (degree(result.submission().id()) + degree(result.reference().id()))
                        / (2.0 * denominator);
                signal = Math.min(1.0, 0.75 * similarities[first][second] + 0.25 * density);
            }
            ScoreBreakdown previous = result.score();
            ScoreBreakdown score = new ScoreBreakdown(previous.exactMatch(),
                    previous.shingleSimilarity(), previous.fuzzyAlignment(), signal,
                    settings.enableExact ? settings.exactWeight : 0.0,
                    settings.enableShingle ? settings.shingleWeight : 0.0,
                    settings.enableFuzzy ? settings.fuzzyWeight : 0.0,
                    settings.enableGraph ? settings.graphWeight : 0.0);
            enriched[i] = new AnalysisResult(result.caseId(), result.submission(), result.reference(),
                    score, result.evidence(), result.elapsedNanos());
        }
        return enriched;
    }
}
