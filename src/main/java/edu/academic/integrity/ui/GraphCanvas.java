package edu.academic.integrity.ui;

import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.DocumentType;
import edu.academic.integrity.model.RelationshipEdge;
import edu.academic.integrity.model.SimilarityGroup;
import edu.academic.integrity.service.GraphSnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Dependency-free similarity-network renderer. Graph algorithms are performed by
 * the engine; this panel only lays out and paints the supplied snapshots.
 */
public final class GraphCanvas extends JPanel {
    public interface ScoreResolver {
        double scoreForDocument(String documentId);
    }

    private static final double MIN_ZOOM = 0.35;
    private static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 1.14;
    private static final int NODE_RADIUS = 27;
    private static final int GROUP_PADDING = 42;
    private static final Color EDGE_COLOR = new Color(113, 129, 150, 145);
    private static final Color PATH_COLOR = new Color(28, 91, 170);
    private static final Color NODE_TEXT = Color.WHITE;

    private NodeData[] nodes = new NodeData[0];
    private RelationshipEdge[] edges = new RelationshipEdge[0];
    private SimilarityGroup[] groups = new SimilarityGroup[0];
    private AnalysisResult[] results = new AnalysisResult[0];
    private CopyingPath selectedPath;
    private ScoreResolver scoreResolver;
    private NodePosition[] positions = new NodePosition[0];

    private double zoom = 1.0;
    private double panX;
    private double panY;
    private Point dragAnchor;
    private double dragStartX;
    private double dragStartY;
    private boolean dragged;
    private String selectedDocumentId;
    private String highlightedComponentId;

    public GraphCanvas() {
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        setPreferredSize(new Dimension(760, 520));
        setMinimumSize(new Dimension(360, 280));
        setFocusable(true);
        setToolTipText("Similarity graph");
        Theme.accessibleName(this, "Similarity graph",
                "Documents, weighted similarity relationships, connected groups, and selected paths");
        installMouseNavigation();
        installKeyboardNavigation();
    }

    public GraphCanvas(Document[] documents, RelationshipEdge[] edges,
                       SimilarityGroup[] groups, AnalysisResult[] results,
                       CopyingPath selectedPath) {
        this();
        setGraph(documents, edges, groups, results, selectedPath);
    }

    public void setGraph(Document[] graphDocuments, RelationshipEdge[] relationships,
                         SimilarityGroup[] similarityGroups, CopyingPath path) {
        setGraph(graphDocuments, relationships, similarityGroups, results, path);
    }

    public void setGraph(Document[] graphDocuments, RelationshipEdge[] relationships,
                         SimilarityGroup[] similarityGroups, AnalysisResult[] analysisResults,
                         CopyingPath path) {
        nodes = nodesFromDocuments(graphDocuments);
        edges = compactEdges(relationships);
        groups = compactGroups(similarityGroups);
        results = compactResults(analysisResults);
        selectedPath = path;
        if (selectedDocumentId != null && indexOfDocument(selectedDocumentId) < 0) {
            selectedDocumentId = null;
        }
        layoutNodes();
        repaint();
    }

    /** Uses the service projection directly; no synthetic {@link Document} objects are created. */
    public void setGraph(GraphSnapshot snapshot) {
        if (snapshot == null) {
            nodes = new NodeData[0];
            edges = new RelationshipEdge[0];
            groups = new SimilarityGroup[0];
            selectedPath = null;
        } else {
            nodes = nodesFromSnapshot(snapshot.nodes());
            edges = edgesFromSnapshot(snapshot.edges());
            groups = compactGroups(snapshot.groups());
            selectedPath = snapshot.selectedPath();
        }
        results = new AnalysisResult[0];
        if (selectedDocumentId != null && indexOfDocument(selectedDocumentId) < 0) {
            selectedDocumentId = null;
        }
        layoutNodes();
        repaint();
    }

    public void setResults(AnalysisResult[] analysisResults) {
        results = compactResults(analysisResults);
        repaint();
    }

    public void setScoreResolver(ScoreResolver resolver) {
        scoreResolver = resolver;
        repaint();
    }

    public void setSelectedPath(CopyingPath path) {
        selectedPath = path;
        repaint();
    }

    /** Highlights one engine-supplied connected component; {@code null} shows all equally. */
    public void setHighlightedComponentId(String componentId) {
        highlightedComponentId = componentId == null || componentId.isBlank()
                ? null : componentId;
        repaint();
    }

    public String highlightedComponentId() {
        return highlightedComponentId;
    }

    public CopyingPath selectedPath() {
        return selectedPath;
    }

    public String selectedDocumentId() {
        return selectedDocumentId;
    }

    public void setSelectedDocumentId(String documentId) {
        String replacement = documentId != null && indexOfDocument(documentId) >= 0
                ? documentId : null;
        String former = selectedDocumentId;
        if (same(former, replacement)) return;
        selectedDocumentId = replacement;
        firePropertyChange("selectedDocumentId", former, replacement);
        repaint();
    }

    public double zoom() {
        return zoom;
    }

    public void setZoom(double requestedZoom) {
        setZoomAround(requestedZoom, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void zoomIn() {
        setZoom(zoom * ZOOM_STEP);
    }

    public void zoomOut() {
        setZoom(zoom / ZOOM_STEP);
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (nodes.length == 0) {
                paintEmpty(g);
                return;
            }
            paintGroups(g);
            paintEdges(g);
            paintSelectedPath(g);
            paintNodes(g);
            paintNavigationHint(g);
        } finally {
            g.dispose();
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int node = nodeAt(event.getX(), event.getY());
        if (node >= 0) {
            NodeData document = nodes[node];
            double score = scoreFor(document.id);
            String title = document.title.equals(document.id) ? "" : " - " + document.title;
            String measurement = Double.isFinite(score) ? " " + UiFormat.percent(score) : "";
            String component = document.componentId.isBlank()
                    ? "" : " | component " + document.componentId;
            return document.id + title + " | " + document.type + " | "
                    + riskFor(document, score) + measurement + component;
        }
        RelationshipEdge edge = edgeAt(event.getX(), event.getY());
        if (edge != null) {
            return edge.firstDocumentId() + " - " + edge.secondDocumentId()
                    + " | similarity " + UiFormat.percent(clampScore(edge.similarity()));
        }
        return "Mouse wheel: zoom | Drag: pan | Click: select node";
    }

    private void paintEmpty(Graphics2D g) {
        String text = "No similarity graph is available.";
        g.setFont(Theme.BODY);
        g.setColor(Theme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, Math.max(12, (getWidth() - metrics.stringWidth(text)) / 2),
                Math.max(24, getHeight() / 2));
    }

    private void paintGroups(Graphics2D g) {
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            SimilarityGroup group = groups[groupIndex];
            String[] ids = group.documentIds();
            double minimumX = Double.POSITIVE_INFINITY;
            double minimumY = Double.POSITIVE_INFINITY;
            double maximumX = Double.NEGATIVE_INFINITY;
            double maximumY = Double.NEGATIVE_INFINITY;
            int members = 0;
            for (String id : ids) {
                int index = indexOfDocument(id);
                if (index < 0) continue;
                double x = screenX(positions[index].x);
                double y = screenY(positions[index].y);
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
                members++;
            }
            if (members < 2) continue;
            double x = minimumX - GROUP_PADDING;
            double y = minimumY - GROUP_PADDING;
            double width = maximumX - minimumX + GROUP_PADDING * 2.0;
            double height = maximumY - minimumY + GROUP_PADDING * 2.0;
            Color base = groupColor(groupIndex);
            boolean emphasized = highlightedComponentId == null
                    || highlightedComponentId.equals(group.id());
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    emphasized ? 34 : 10));
            g.fill(new RoundRectangle2D.Double(x, y, width, height, 38, 38));
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    emphasized ? 125 : 45));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Double(x, y, width, height, 38, 38));
            g.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            g.setColor(base.darker());
            g.drawString(group.id(), (float) x + 10, (float) y + 18);
        }
    }

    private void paintEdges(Graphics2D g) {
        for (RelationshipEdge edge : edges) {
            int first = indexOfDocument(edge.firstDocumentId());
            int second = indexOfDocument(edge.secondDocumentId());
            if (first < 0 || second < 0 || first == second) continue;
            double similarity = clampScore(edge.similarity());
            float width = (float) (1.0 + similarity * 3.0);
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            boolean emphasized = highlightedComponentId == null
                    || inHighlightedComponent(first) && inHighlightedComponent(second);
            g.setColor(emphasized ? EDGE_COLOR : new Color(177, 187, 199, 72));
            double x1 = screenX(positions[first].x);
            double y1 = screenY(positions[first].y);
            double x2 = screenX(positions[second].x);
            double y2 = screenY(positions[second].y);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
            if (nodes.length <= 12) {
                paintEdgeLabel(g, (x1 + x2) / 2.0, (y1 + y2) / 2.0,
                        UiFormat.percent(similarity), Theme.MUTED);
            }
        }
    }

    private void paintSelectedPath(Graphics2D g) {
        if (selectedPath == null || !selectedPath.exists()) return;
        String[] ids = selectedPath.documentIds();
        double[] similarities = selectedPath.relationshipSimilarities();
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(PATH_COLOR);
        for (int i = 0; i + 1 < ids.length; i++) {
            int first = indexOfDocument(ids[i]);
            int second = indexOfDocument(ids[i + 1]);
            if (first < 0 || second < 0) continue;
            double x1 = screenX(positions[first].x);
            double y1 = screenY(positions[first].y);
            double x2 = screenX(positions[second].x);
            double y2 = screenY(positions[second].y);
            g.draw(new Line2D.Double(x1, y1, x2, y2));
            if (i < similarities.length) {
                paintEdgeLabel(g, (x1 + x2) / 2.0, (y1 + y2) / 2.0,
                        UiFormat.percent(clampScore(similarities[i])), PATH_COLOR);
            }
        }
    }

    private void paintEdgeLabel(Graphics2D g, double centerX, double centerY,
                                String text, Color foreground) {
        g.setFont(Theme.SMALL);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 8;
        int height = metrics.getHeight() + 2;
        int x = (int) Math.round(centerX - width / 2.0);
        int y = (int) Math.round(centerY - height / 2.0);
        g.setColor(new Color(255, 255, 255, 224));
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setColor(foreground);
        g.drawString(text, x + 4, y + metrics.getAscent() + 1);
    }

    private void paintNodes(Graphics2D g) {
        for (int i = 0; i < nodes.length; i++) {
            NodeData document = nodes[i];
            double x = screenX(positions[i].x);
            double y = screenY(positions[i].y);
            double score = scoreFor(document.id);
            String risk = riskFor(document, score);
            Color fill = highlightedComponentId == null || inHighlightedComponent(i)
                    ? RiskBadge.colorForRisk(risk) : new Color(181, 190, 201);
            Ellipse2D node = new Ellipse2D.Double(x - NODE_RADIUS, y - NODE_RADIUS,
                    NODE_RADIUS * 2.0, NODE_RADIUS * 2.0);

            g.setColor(new Color(21, 35, 52, 35));
            g.fill(new Ellipse2D.Double(x - NODE_RADIUS + 2, y - NODE_RADIUS + 4,
                    NODE_RADIUS * 2.0, NODE_RADIUS * 2.0));
            g.setColor(fill);
            g.fill(node);

            boolean selected = document.id.equals(selectedDocumentId);
            boolean onPath = isPathNode(document.id);
            g.setStroke(new BasicStroke(selected ? 4.0f : onPath ? 3.0f : 1.5f));
            g.setColor(selected ? Theme.TEXT : onPath ? PATH_COLOR : Color.WHITE);
            g.draw(node);

            g.setColor(NODE_TEXT);
            g.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            String nodeLabel = fitted(document.id, g.getFontMetrics(), NODE_RADIUS * 2 - 12);
            FontMetrics nodeMetrics = g.getFontMetrics();
            g.drawString(nodeLabel, (float) (x - nodeMetrics.stringWidth(nodeLabel) / 2.0),
                    (float) (y + nodeMetrics.getAscent() / 2.0 - 2));

            String type = document.type == DocumentType.SUBMISSION ? "S" : "R";
            int markerSize = 16;
            g.setColor(Theme.NAVY);
            g.fillOval((int) Math.round(x + NODE_RADIUS - 12),
                    (int) Math.round(y - NODE_RADIUS - 2), markerSize, markerSize);
            g.setColor(Color.WHITE);
            g.setFont(Theme.SMALL.deriveFont(Font.BOLD, 10f));
            g.drawString(type, (float) (x + NODE_RADIUS - 7), (float) (y - NODE_RADIUS + 10));

            g.setFont(Theme.SMALL);
            g.setColor(RiskBadge.foregroundForRisk(risk));
            String caption = risk + (Double.isFinite(score) ? " " + UiFormat.percent(score) : "");
            FontMetrics captionMetrics = g.getFontMetrics();
            g.drawString(caption, (float) (x - captionMetrics.stringWidth(caption) / 2.0),
                    (float) (y + NODE_RADIUS + 17));
        }
    }

    private void paintNavigationHint(Graphics2D g) {
        String hint = "Wheel: zoom   Drag: pan   Click: select   0: reset";
        g.setFont(Theme.SMALL);
        FontMetrics metrics = g.getFontMetrics();
        int x = 12;
        int y = getHeight() - 12;
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect(x - 5, y - metrics.getAscent(), metrics.stringWidth(hint) + 10,
                metrics.getHeight() + 2, 8, 8);
        g.setColor(Theme.MUTED);
        g.drawString(hint, x, y);
    }

    private void layoutNodes() {
        positions = new NodePosition[nodes.length];
        int count = nodes.length;
        if (count == 1) {
            positions[0] = new NodePosition(0.0, 0.0);
            return;
        }
        if (count <= 14) {
            double radius = Math.max(145.0, 58.0 + count * 14.0);
            for (int i = 0; i < count; i++) {
                double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i / count);
                positions[i] = new NodePosition(Math.cos(angle) * radius,
                        Math.sin(angle) * radius);
            }
            return;
        }

        int first = 0;
        int ring = 0;
        while (first < count) {
            int capacity = 12 + ring * 6;
            int ringCount = Math.min(capacity, count - first);
            double radius = 170.0 + ring * 115.0;
            for (int offset = 0; offset < ringCount; offset++) {
                double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * offset / ringCount)
                        + ring * 0.17;
                positions[first + offset] = new NodePosition(Math.cos(angle) * radius,
                        Math.sin(angle) * radius);
            }
            first += ringCount;
            ring++;
        }
    }

    private void installMouseNavigation() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                int selected = nodeAt(event.getX(), event.getY());
                if (selected >= 0) {
                    setSelectedDocumentId(nodes[selected].id);
                    if (event.getClickCount() == 2) {
                        firePropertyChange("nodeActivated", null, selectedDocumentId);
                    }
                }
                dragAnchor = event.getPoint();
                dragStartX = panX;
                dragStartY = panY;
                dragged = false;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragAnchor == null) return;
                panX = dragStartX + event.getX() - dragAnchor.x;
                panY = dragStartY + event.getY() - dragAnchor.y;
                dragged = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragAnchor = null;
                setCursor(Cursor.getDefaultCursor());
                if (!dragged && nodeAt(event.getX(), event.getY()) < 0) {
                    setSelectedDocumentId(null);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                double factor = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
                setZoomAround(zoom * factor, event.getX(), event.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeyboardNavigation() {
        bind("zoom-in", KeyStroke.getKeyStroke('+'), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { zoomIn(); }
        });
        bind("zoom-in-equals", KeyStroke.getKeyStroke('='), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { zoomIn(); }
        });
        bind("zoom-out", KeyStroke.getKeyStroke('-'), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { zoomOut(); }
        });
        bind("reset", KeyStroke.getKeyStroke('0'), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { resetView(); }
        });
        bind("previous-node", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { selectRelative(-1); }
        });
        bind("next-node", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { selectRelative(1); }
        });
        bind("activate-node", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (selectedDocumentId != null) {
                    firePropertyChange("nodeActivated", null, selectedDocumentId);
                }
            }
        });
    }

    private void bind(String name, KeyStroke key, AbstractAction action) {
        getInputMap(WHEN_FOCUSED).put(key, name);
        getActionMap().put(name, action);
    }

    private void selectRelative(int direction) {
        if (nodes.length == 0) return;
        int current = indexOfDocument(selectedDocumentId);
        int replacement = current < 0 ? 0
                : (current + direction + nodes.length) % nodes.length;
        setSelectedDocumentId(nodes[replacement].id);
    }

    private void setZoomAround(double requested, double anchorX, double anchorY) {
        double replacement = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requested));
        if (Math.abs(replacement - zoom) < 0.000001) return;
        double centerX = getWidth() / 2.0;
        double centerY = getHeight() / 2.0;
        double ratio = replacement / zoom;
        panX = anchorX - centerX - (anchorX - centerX - panX) * ratio;
        panY = anchorY - centerY - (anchorY - centerY - panY) * ratio;
        zoom = replacement;
        repaint();
    }

    private int nodeAt(double x, double y) {
        for (int i = nodes.length - 1; i >= 0; i--) {
            double dx = x - screenX(positions[i].x);
            double dy = y - screenY(positions[i].y);
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) return i;
        }
        return -1;
    }

    private RelationshipEdge edgeAt(double x, double y) {
        for (RelationshipEdge edge : edges) {
            int first = indexOfDocument(edge.firstDocumentId());
            int second = indexOfDocument(edge.secondDocumentId());
            if (first < 0 || second < 0) continue;
            if (Line2D.ptSegDist(screenX(positions[first].x), screenY(positions[first].y),
                    screenX(positions[second].x), screenY(positions[second].y), x, y) <= 6.0) {
                return edge;
            }
        }
        return null;
    }

    private int indexOfDocument(String documentId) {
        if (documentId == null) return -1;
        for (int i = 0; i < nodes.length; i++) {
            if (documentId.equals(nodes[i].id)) return i;
        }
        return -1;
    }

    private double scoreFor(String documentId) {
        if (scoreResolver != null) {
            try {
                return clampScore(scoreResolver.scoreForDocument(documentId));
            } catch (RuntimeException ignored) {
                return Double.NaN;
            }
        }
        double maximum = 0.0;
        boolean found = false;
        for (AnalysisResult analysis : results) {
            if (analysis.submission().id().equals(documentId)
                    || analysis.reference().id().equals(documentId)) {
                maximum = Math.max(maximum, analysis.score().total());
                found = true;
            }
        }
        return found ? clampScore(maximum) : Double.NaN;
    }

    private String riskFor(NodeData node, double score) {
        if (node.riskLabel != null && !node.riskLabel.isBlank()) return node.riskLabel;
        return RiskBadge.riskForScore(score);
    }

    private boolean isPathNode(String id) {
        if (selectedPath == null || !selectedPath.exists()) return false;
        for (String pathId : selectedPath.documentIds()) {
            if (pathId.equals(id)) return true;
        }
        return false;
    }

    private boolean inHighlightedComponent(int nodeIndex) {
        return highlightedComponentId == null
                || highlightedComponentId.equals(nodes[nodeIndex].componentId);
    }

    private double screenX(double worldX) {
        return getWidth() / 2.0 + panX + worldX * zoom;
    }

    private double screenY(double worldY) {
        return getHeight() / 2.0 + panY + worldY * zoom;
    }

    private String fitted(String value, FontMetrics metrics, int maximumWidth) {
        if (metrics.stringWidth(value) <= maximumWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 1 && metrics.stringWidth(value.substring(0, end) + suffix) > maximumWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    private Color groupColor(int index) {
        Color[] palette = {
                Theme.BLUE, Theme.SUCCESS, Theme.WARNING,
                new Color(117, 78, 166), new Color(35, 130, 142)
        };
        return palette[index % palette.length];
    }

    private boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private double clampScore(double score) {
        if (!Double.isFinite(score) || score < 0.0) return 0.0;
        return Math.min(1.0, score);
    }

    private NodeData[] nodesFromDocuments(Document[] source) {
        if (source == null) return new NodeData[0];
        int count = 0;
        for (Document value : source) if (value != null) count++;
        NodeData[] copy = new NodeData[count];
        int output = 0;
        for (Document value : source) {
            if (value != null) {
                copy[output++] = new NodeData(value.id(), value.title(), value.type(), null, null);
            }
        }
        return copy;
    }

    private NodeData[] nodesFromSnapshot(GraphSnapshot.Node[] source) {
        if (source == null) return new NodeData[0];
        int count = 0;
        for (GraphSnapshot.Node value : source) if (value != null && !value.id().isBlank()) count++;
        NodeData[] copy = new NodeData[count];
        int output = 0;
        for (GraphSnapshot.Node value : source) {
            if (value != null && !value.id().isBlank()) {
                copy[output++] = new NodeData(value.id(), value.title(), value.type(),
                        value.riskLabel(), value.componentId());
            }
        }
        return copy;
    }

    private RelationshipEdge[] edgesFromSnapshot(GraphSnapshot.Edge[] source) {
        if (source == null) return new RelationshipEdge[0];
        int count = 0;
        for (GraphSnapshot.Edge value : source) if (value != null) count++;
        RelationshipEdge[] copy = new RelationshipEdge[count];
        int output = 0;
        for (GraphSnapshot.Edge value : source) {
            if (value != null) {
                copy[output++] = new RelationshipEdge(value.firstDocumentId(),
                        value.secondDocumentId(), value.similarity());
            }
        }
        return copy;
    }

    private RelationshipEdge[] compactEdges(RelationshipEdge[] source) {
        if (source == null) return new RelationshipEdge[0];
        int count = 0;
        for (RelationshipEdge value : source) if (value != null) count++;
        RelationshipEdge[] copy = new RelationshipEdge[count];
        int output = 0;
        for (RelationshipEdge value : source) if (value != null) copy[output++] = value;
        return copy;
    }

    private SimilarityGroup[] compactGroups(SimilarityGroup[] source) {
        if (source == null) return new SimilarityGroup[0];
        int count = 0;
        for (SimilarityGroup value : source) if (value != null) count++;
        SimilarityGroup[] copy = new SimilarityGroup[count];
        int output = 0;
        for (SimilarityGroup value : source) if (value != null) copy[output++] = value;
        return copy;
    }

    private AnalysisResult[] compactResults(AnalysisResult[] source) {
        if (source == null) return new AnalysisResult[0];
        int count = 0;
        for (AnalysisResult value : source) if (value != null) count++;
        AnalysisResult[] copy = new AnalysisResult[count];
        int output = 0;
        for (AnalysisResult value : source) if (value != null) copy[output++] = value;
        return copy;
    }

    private static final class NodePosition {
        private final double x;
        private final double y;

        private NodePosition(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class NodeData {
        private final String id;
        private final String title;
        private final DocumentType type;
        private final String riskLabel;
        private final String componentId;

        private NodeData(String id, String title, DocumentType type, String riskLabel,
                         String componentId) {
            this.id = id;
            this.title = title == null || title.isBlank() ? id : title;
            this.type = type;
            this.riskLabel = riskLabel;
            this.componentId = componentId == null ? "" : componentId;
        }
    }
}
