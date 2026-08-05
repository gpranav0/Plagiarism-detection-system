package edu.academic.integrity.ui;

import edu.academic.integrity.controller.ApplicationController;
import edu.academic.integrity.model.CopyingPath;
import edu.academic.integrity.model.SimilarityGroup;
import edu.academic.integrity.service.GraphSnapshot;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Interactive renderer for engine-computed relationships, components, and paths. */
public final class SimilarityGraphPanel extends ScreenPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String GRAPH_CARD = "graph";

    private final ApplicationController controller;
    private final UiHost host;
    private final GraphCanvas canvas = new GraphCanvas();
    private final JComboBox<String> from = new JComboBox<>();
    private final JComboBox<String> to = new JComboBox<>();
    private final JComboBox<ComponentChoice> component = new JComboBox<>();
    private final JLabel graphSummary = Theme.mutedLabel("No graph loaded");
    private final JLabel pathSummary = Theme.mutedLabel("No relationship path selected");
    private final JTextArea selectedNode = new JTextArea();
    private final CardLayout bodyCards = new CardLayout();
    private final JPanel bodyDeck = new JPanel(bodyCards);
    private final EmptyStatePanel emptyState = new EmptyStatePanel(
            "Run an analysis to build the document similarity network.");

    private GraphSnapshot snapshot;
    private boolean updatingControls;

    public SimilarityGraphPanel(ApplicationController controller, UiHost host) {
        super("Similarity graph",
                "Explore engine-computed relationships, connected components, and shortest paths.");
        if (controller == null || host == null) {
            throw new IllegalArgumentException("controller and host are required");
        }
        this.controller = controller;
        this.host = host;
        setScreenContent(buildContent());
    }

    @Override
    public void refreshData() {
        try {
            applySnapshot(controller.graphSnapshot());
        } catch (RuntimeException failure) {
            controller.logDetailedError("Similarity graph could not be loaded", failure);
            snapshot = null;
            emptyState.setMessage("The similarity graph could not be loaded.");
            bodyCards.show(bodyDeck, EMPTY_CARD);
            host.taskFailed("Unable to load the similarity graph.");
        }
    }

    public String selectedDocumentId() {
        return canvas.selectedDocumentId();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.add(buildToolbar(), BorderLayout.NORTH);

        bodyDeck.setOpaque(false);
        bodyDeck.add(emptyState, EMPTY_CARD);
        bodyDeck.add(buildGraphBody(), GRAPH_CARD);
        root.add(bodyDeck, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(10, 8));
        toolbar.setBackground(Theme.SURFACE);
        toolbar.setBorder(Theme.cardBorder());

        JPanel path = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        path.setOpaque(false);
        JLabel pathLabel = new JLabel("Relationship path");
        pathLabel.setFont(Theme.BODY_BOLD);
        from.setPrototypeDisplayValue("SUBMISSION_DOCUMENT_000");
        to.setPrototypeDisplayValue("REFERENCE_DOCUMENT_000");
        from.setToolTipText("Starting document for custom-heap Dijkstra path search");
        to.setToolTipText("Destination document for custom-heap Dijkstra path search");
        JButton findPath = Theme.primaryButton("Find path");
        findPath.setMnemonic('P');
        findPath.addActionListener(event -> findPath());
        JButton clearPath = Theme.secondaryButton("Clear path");
        clearPath.addActionListener(event -> {
            canvas.setSelectedPath(null);
            pathSummary.setText("No relationship path selected");
        });
        path.add(pathLabel);
        path.add(new JLabel("From"));
        path.add(from);
        path.add(new JLabel("to"));
        path.add(to);
        path.add(findPath);
        path.add(clearPath);
        toolbar.add(path, BorderLayout.CENTER);

        JPanel view = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        view.setOpaque(false);
        JButton zoomOut = Theme.secondaryButton("-");
        zoomOut.setToolTipText("Zoom out");
        zoomOut.addActionListener(event -> canvas.zoomOut());
        JButton zoomIn = Theme.secondaryButton("+");
        zoomIn.setToolTipText("Zoom in");
        zoomIn.addActionListener(event -> canvas.zoomIn());
        JButton reset = Theme.secondaryButton("Reset view");
        reset.setMnemonic('V');
        reset.addActionListener(event -> canvas.resetView());
        view.add(zoomOut);
        view.add(zoomIn);
        view.add(reset);
        JButton refresh = Theme.secondaryButton("Refresh");
        refresh.addActionListener(event -> refreshData());
        view.add(refresh);
        toolbar.add(view, BorderLayout.EAST);

        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        status.add(graphSummary, BorderLayout.WEST);
        status.add(pathSummary, BorderLayout.EAST);
        toolbar.add(status, BorderLayout.SOUTH);
        return toolbar;
    }

    private JPanel buildGraphBody() {
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setOpaque(false);
        canvas.addPropertyChangeListener("selectedDocumentId",
                event -> updateSelectedNode((String) event.getNewValue()));
        body.add(canvas, BorderLayout.CENTER);
        body.add(buildSidebar(), BorderLayout.EAST);
        return body;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(275, 420));
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setBorder(Theme.cardBorder());

        JLabel componentHeading = new JLabel("Connected components");
        componentHeading.setFont(Theme.SECTION);
        componentHeading.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setToolTipText("Highlight a component computed by the custom union-find graph");
        component.addActionListener(event -> {
            if (updatingControls) return;
            ComponentChoice choice = (ComponentChoice) component.getSelectedItem();
            canvas.setHighlightedComponentId(choice == null ? null : choice.id);
        });
        sidebar.add(componentHeading);
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(component);
        sidebar.add(Box.createVerticalStrut(20));

        JLabel selectedHeading = new JLabel("Selected document");
        selectedHeading.setFont(Theme.SECTION);
        selectedHeading.setAlignmentX(LEFT_ALIGNMENT);
        selectedNode.setEditable(false);
        selectedNode.setLineWrap(true);
        selectedNode.setWrapStyleWord(true);
        selectedNode.setRows(7);
        selectedNode.setText("Click a node to inspect its document and component.");
        selectedNode.setBackground(Theme.SURFACE);
        selectedNode.setForeground(Theme.TEXT);
        JScrollPane selectedScroll = new JScrollPane(selectedNode);
        selectedScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        selectedScroll.setAlignmentX(LEFT_ALIGNMENT);
        selectedScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        sidebar.add(selectedHeading);
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(selectedScroll);
        sidebar.add(Box.createVerticalStrut(20));

        JLabel legendHeading = new JLabel("Risk legend");
        legendHeading.setFont(Theme.SECTION);
        legendHeading.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(legendHeading);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(legendRow("LOW", "Below 35%"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(legendRow("MEDIUM", "35% to 54.99%"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(legendRow("HIGH", "55% to 74.99%"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(legendRow("CRITICAL", "75% or higher"));
        sidebar.add(Box.createVerticalGlue());

        JLabel policy = Theme.mutedLabel(
                "Edges show similarity, not direction or proof of copying.");
        policy.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(policy);
        return sidebar;
    }

    private JPanel legendRow(String risk, String range) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new RiskBadge(risk), BorderLayout.WEST);
        row.add(Theme.mutedLabel(range), BorderLayout.CENTER);
        return row;
    }

    private void applySnapshot(GraphSnapshot graph) {
        snapshot = graph;
        GraphSnapshot.Node[] nodes = graph == null ? new GraphSnapshot.Node[0] : graph.nodes();
        if (nodes.length == 0) {
            canvas.setGraph((GraphSnapshot) null);
            populateNodeControls(nodes);
            populateComponents(new SimilarityGroup[0]);
            graphSummary.setText("No graph data");
            pathSummary.setText("No relationship path selected");
            selectedNode.setText("No document nodes are available.");
            bodyCards.show(bodyDeck, EMPTY_CARD);
            return;
        }

        String formerFrom = (String) from.getSelectedItem();
        String formerTo = (String) to.getSelectedItem();
        String formerComponent = selectedComponentId();
        populateNodeControls(nodes);
        restoreNodeSelection(from, formerFrom, 0);
        restoreNodeSelection(to, formerTo, Math.min(1, nodes.length - 1));
        populateComponents(graph.groups());
        restoreComponent(formerComponent);

        canvas.setGraph(graph);
        ComponentChoice choice = (ComponentChoice) component.getSelectedItem();
        canvas.setHighlightedComponentId(choice == null ? null : choice.id);
        graphSummary.setText(nodes.length + " documents  |  " + graph.edges().length
                + " relationships  |  " + graph.groups().length + " connected groups");
        updatePathSummary(graph.selectedPath());
        updateSelectedNode(canvas.selectedDocumentId());
        bodyCards.show(bodyDeck, GRAPH_CARD);
        revalidate();
        repaint();
    }

    private void findPath() {
        String first = (String) from.getSelectedItem();
        String second = (String) to.getSelectedItem();
        if (first == null || second == null) {
            host.taskFailed("Choose both path endpoints.");
            return;
        }
        if (first.equals(second)) {
            host.taskFailed("Choose two different documents for a relationship path.");
            return;
        }
        try {
            GraphSnapshot selected = controller.selectPath(first, second);
            applySnapshot(selected);
            CopyingPath path = selected.selectedPath();
            if (path == null || !path.exists()) {
                pathSummary.setText("No relationship path connects the selected documents");
                host.taskFailed("No relationship path connects those documents.");
            } else {
                host.taskFinished("Relationship path selected");
            }
        } catch (RuntimeException failure) {
            controller.logDetailedError("Similarity path could not be calculated", failure);
            host.taskFailed("Unable to calculate the selected relationship path.");
        }
    }

    private void updatePathSummary(CopyingPath path) {
        if (path == null || !path.exists()) {
            pathSummary.setText("No relationship path selected");
            return;
        }
        String[] ids = path.documentIds();
        pathSummary.setText("Selected path: " + ids.length + " nodes, cost "
                + formatCost(path.totalCost()));
    }

    private String formatCost(double value) {
        if (!Double.isFinite(value)) return "unreachable";
        return String.format("%.4f", value);
    }

    private void updateSelectedNode(String documentId) {
        if (snapshot == null || documentId == null) {
            selectedNode.setText("Click a node to inspect its document and component.");
            return;
        }
        GraphSnapshot.Node node = findNode(documentId);
        if (node == null) {
            selectedNode.setText("The selected document is no longer in this graph.");
            return;
        }
        String componentText = node.componentId().isBlank()
                ? "Isolated / no multi-document component" : node.componentId();
        selectedNode.setText("Document: " + node.id() + "\n"
                + "Title: " + node.title() + "\n"
                + "Type: " + node.type() + "\n"
                + "Risk: " + node.riskLabel() + "\n"
                + "Component: " + componentText);
        selectedNode.setCaretPosition(0);
    }

    private GraphSnapshot.Node findNode(String id) {
        for (GraphSnapshot.Node node : snapshot.nodes()) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }

    private void populateNodeControls(GraphSnapshot.Node[] nodes) {
        updatingControls = true;
        try {
            from.removeAllItems();
            to.removeAllItems();
            for (GraphSnapshot.Node node : nodes) {
                from.addItem(node.id());
                to.addItem(node.id());
            }
            from.setEnabled(nodes.length > 0);
            to.setEnabled(nodes.length > 0);
        } finally {
            updatingControls = false;
        }
    }

    private void populateComponents(SimilarityGroup[] groups) {
        updatingControls = true;
        try {
            component.removeAllItems();
            component.addItem(new ComponentChoice(null, "All components"));
            for (SimilarityGroup group : groups) {
                component.addItem(new ComponentChoice(group.id(), group.id() + " ("
                        + group.documentIds().length + " documents)"));
            }
            component.setEnabled(groups.length > 0);
        } finally {
            updatingControls = false;
        }
    }

    private void restoreNodeSelection(JComboBox<String> box, String requested, int fallback) {
        if (requested != null) {
            for (int i = 0; i < box.getItemCount(); i++) {
                if (requested.equals(box.getItemAt(i))) {
                    box.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (box.getItemCount() > 0) box.setSelectedIndex(fallback);
    }

    private String selectedComponentId() {
        ComponentChoice choice = (ComponentChoice) component.getSelectedItem();
        return choice == null ? null : choice.id;
    }

    private void restoreComponent(String componentId) {
        if (componentId == null) {
            if (component.getItemCount() > 0) component.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < component.getItemCount(); i++) {
            ComponentChoice choice = component.getItemAt(i);
            if (componentId.equals(choice.id)) {
                component.setSelectedIndex(i);
                return;
            }
        }
        component.setSelectedIndex(0);
    }

    private static final class ComponentChoice {
        private final String id;
        private final String label;

        private ComponentChoice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
