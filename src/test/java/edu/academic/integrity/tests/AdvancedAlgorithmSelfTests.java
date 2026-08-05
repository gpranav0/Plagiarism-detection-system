package edu.academic.integrity.tests;

import edu.academic.integrity.algorithms.benchmark.AlgorithmBenchmark;
import edu.academic.integrity.algorithms.benchmark.BenchmarkResult;
import edu.academic.integrity.algorithms.compression.HuffmanCodec;
import edu.academic.integrity.algorithms.flow.EdmondsKarpMaxFlow;
import edu.academic.integrity.algorithms.graph.DirectedGraphAlgorithms;
import edu.academic.integrity.algorithms.graph.MinimumSpanningTree;
import edu.academic.integrity.algorithms.graph.ShortestPaths;
import edu.academic.integrity.algorithms.graph.WeightedGraph;
import edu.academic.integrity.algorithms.greedy.GreedyEvidenceSelector;
import edu.academic.integrity.algorithms.greedy.SetCoverSolver;
import edu.academic.integrity.algorithms.sort.GenericSorts;
import edu.academic.integrity.algorithms.sort.IntegerSorts;

/** Standalone checks for graph, optimization, compression, and sorting modules. */
public final class AdvancedAlgorithmSelfTests {
    private static int assertionCount;

    private AdvancedAlgorithmSelfTests() {
    }

    public static int runAll() {
        assertionCount = 0;
        testWeightedGraphAndTraversal();
        testSpanningTreesAndShortestPaths();
        testDirectedAlgorithms();
        testMaximumFlowAndAssignment();
        testGreedySelection();
        testHuffmanCodec();
        testIntegerSorts();
        testGenericSorts();
        testBenchmarks();
        return assertionCount;
    }

    public static void main(String[] arguments) {
        int passed = runAll();
        System.out.println("Advanced algorithm self-tests passed: " + passed + " assertions");
    }

    private static void testWeightedGraphAndTraversal() {
        WeightedGraph<String> graph = sampleUndirectedGraph();
        check(graph.vertexCount() == 5, "Graph vertex count");
        check(graph.edgeCount() == 5, "Graph edge count");
        check(graph.validateInvariants(), "Graph invariants");
        check(graph.breadthFirst(0).size() == 4, "BFS reaches its component");
        check(graph.depthFirst(0).size() == 4, "DFS reaches its component");
        check(graph.breadthFirst(4).size() == 1, "BFS handles isolated vertex");
        WeightedGraph.Components components = graph.connectedComponents();
        check(components.count() == 2, "Connected component count");
        check(components.sizeOf(0) + components.sizeOf(1) == 5,
                "Components contain every vertex");
        check(graph.removeEdge(2, 3), "Existing edge removal");
        check(!graph.hasEdge(2, 3) && !graph.hasEdge(3, 2),
                "Undirected removal is symmetric");
        check(graph.validateInvariants(), "Invariants after edge removal");
    }

    private static void testSpanningTreesAndShortestPaths() {
        WeightedGraph<String> graph = sampleUndirectedGraph();
        MinimumSpanningTree.Result kruskal = MinimumSpanningTree.kruskal(graph);
        MinimumSpanningTree.Result prim = MinimumSpanningTree.prim(graph);
        check(kruskal.edgeCount() == 3, "Kruskal forest edge count");
        check(prim.edgeCount() == 3, "Prim forest edge count");
        check(close(kruskal.totalWeight(), 4.0), "Kruskal forest weight");
        check(close(prim.totalWeight(), 4.0), "Prim forest weight");
        check(kruskal.componentCount() == 2 && prim.componentCount() == 2,
                "MST algorithms report disconnected graph");

        ShortestPaths.SingleSourceResult dijkstra = ShortestPaths.dijkstra(graph, 0);
        check(close(dijkstra.distanceTo(3), 4.0), "Dijkstra distance");
        int[] expectedPath = {0, 2, 1, 3};
        check(equal(dijkstra.pathTo(3), expectedPath), "Dijkstra path reconstruction");
        check(!dijkstra.isReachable(4), "Dijkstra unreachable vertex");

        ShortestPaths.AllPairsResult allPairs = ShortestPaths.floydWarshall(graph);
        check(close(allPairs.distance(0, 3), 4.0), "Floyd-Warshall distance");
        check(equal(allPairs.path(0, 3), expectedPath), "Floyd-Warshall path");
        check(allPairs.path(0, 4).length == 0, "Floyd-Warshall unreachable pair");

        WeightedGraph<String> negative = new WeightedGraph<String>(true);
        negative.addVertex("source");
        negative.addVertex("cycle-a");
        negative.addVertex("cycle-b");
        negative.addVertex("downstream");
        negative.addEdge(0, 1, 1.0);
        negative.addEdge(1, 2, -3.0);
        negative.addEdge(2, 1, 1.0);
        negative.addEdge(2, 3, 2.0);
        ShortestPaths.SingleSourceResult bellman = ShortestPaths.bellmanFord(negative, 0);
        check(bellman.hasReachableNegativeCycle(), "Bellman-Ford negative cycle");
        check(bellman.isNegativelyUnbounded(1), "Negative-cycle member marked");
        check(bellman.isNegativelyUnbounded(3), "Negative-cycle effect propagated");
        expectFailure(new Action() {
            @Override
            public void run() {
                bellman.pathTo(3);
            }
        }, "Negative-cycle path is rejected");
        expectFailure(new Action() {
            @Override
            public void run() {
                ShortestPaths.dijkstra(negative, 0);
            }
        }, "Dijkstra rejects negative weight");
    }

    private static void testDirectedAlgorithms() {
        WeightedGraph<String> dag = new WeightedGraph<String>(true);
        for (int i = 0; i < 4; i++) {
            dag.addVertex("v" + i);
        }
        dag.addEdge(0, 1, 1.0);
        dag.addEdge(0, 2, 1.0);
        dag.addEdge(1, 3, 1.0);
        dag.addEdge(2, 3, 1.0);
        DirectedGraphAlgorithms.TopologicalResult topological =
                DirectedGraphAlgorithms.topologicalSort(dag);
        check(topological.isAcyclic(), "DAG recognized");
        int[] order = topological.order();
        check(order.length == 4, "Topological order length");
        int[] position = positions(order);
        check(position[0] < position[1] && position[0] < position[2],
                "Topological source precedes children");
        check(position[1] < position[3] && position[2] < position[3],
                "Topological children precede sink");

        WeightedGraph<String> cyclic = new WeightedGraph<String>(true);
        for (int i = 0; i < 5; i++) {
            cyclic.addVertex("c" + i);
        }
        cyclic.addEdge(0, 1, 1.0);
        cyclic.addEdge(1, 2, 1.0);
        cyclic.addEdge(2, 0, 1.0);
        cyclic.addEdge(2, 3, 1.0);
        cyclic.addEdge(3, 4, 1.0);
        cyclic.addEdge(4, 3, 1.0);
        check(!DirectedGraphAlgorithms.topologicalSort(cyclic).isAcyclic(),
                "Directed cycle recognized");
        DirectedGraphAlgorithms.StrongComponents components =
                DirectedGraphAlgorithms.stronglyConnectedComponents(cyclic);
        check(components.count() == 2, "SCC count");
        check(components.componentOf(0) == components.componentOf(2),
                "First SCC membership");
        check(components.componentOf(3) == components.componentOf(4),
                "Second SCC membership");
        check(components.componentOf(0) != components.componentOf(3),
                "Distinct SCCs remain separate");
    }

    private static void testMaximumFlowAndAssignment() {
        EdmondsKarpMaxFlow network = new EdmondsKarpMaxFlow(6);
        network.addEdge(0, 1, 16);
        network.addEdge(0, 2, 13);
        network.addEdge(1, 2, 10);
        network.addEdge(2, 1, 4);
        network.addEdge(1, 3, 12);
        network.addEdge(3, 2, 9);
        network.addEdge(2, 4, 14);
        network.addEdge(4, 3, 7);
        network.addEdge(3, 5, 20);
        network.addEdge(4, 5, 4);
        EdmondsKarpMaxFlow.Result result = network.maximumFlow(0, 5);
        check(result.value() == 23, "Classic Edmonds-Karp maximum");
        check(result.minCutCapacity() == 23, "Max-flow/min-cut equality");

        boolean[][] eligible = {
            {true, false},
            {true, true},
            {false, true}
        };
        EdmondsKarpMaxFlow.AssignmentResult assignment =
                EdmondsKarpMaxFlow.assignCases(eligible, new int[] {1, 1});
        check(assignment.assignedCount() == 2, "Maximum reviewer assignments");
        check(assignment.reviewerLoad(0) <= 1 && assignment.reviewerLoad(1) <= 1,
                "Reviewer capacities respected");
        for (int caseIndex = 0; caseIndex < assignment.caseCount(); caseIndex++) {
            int reviewer = assignment.reviewerForCase(caseIndex);
            check(reviewer < 0 || eligible[caseIndex][reviewer],
                    "Only eligible reviewer assigned");
        }
    }

    private static void testGreedySelection() {
        GreedyEvidenceSelector.EvidenceCandidate[] candidates = {
            new GreedyEvidenceSelector.EvidenceCandidate("exact-a", new int[] {0, 1}, 2.0, 1.0),
            new GreedyEvidenceSelector.EvidenceCandidate("fuzzy-b", new int[] {1, 2}, 1.0, 1.0),
            new GreedyEvidenceSelector.EvidenceCandidate("duplicate", new int[] {0}, 1.0, 2.0)
        };
        GreedyEvidenceSelector.SelectionResult selection =
                GreedyEvidenceSelector.select(candidates, 3, 2);
        check(selection.selectedCount() == 2, "Greedy selection item limit");
        check(selection.selectedIndexAt(0) == 0, "Greedy best first item");
        check(selection.hasFullCoverage(), "Greedy evidence full coverage");
        check(close(selection.coverageRatio(), 1.0), "Greedy coverage ratio");

        SetCoverSolver.CandidateSet[] sets = {
            new SetCoverSolver.CandidateSet("left", new int[] {0, 1}, 1.0),
            new SetCoverSolver.CandidateSet("middle", new int[] {1, 2}, 1.0)
        };
        SetCoverSolver.Result cover = SetCoverSolver.cover(4, sets);
        check(!cover.isComplete(), "Incomplete set cover reported");
        check(equal(cover.uncoveredElements(), new int[] {3}), "Uncovered element reported");
    }

    private static void testHuffmanCodec() {
        HuffmanCodec codec = new HuffmanCodec();
        String[] samples = {"", "aaaaaaaa", "plagiarism evidence", "Unicode: नमस्ते 🌍"};
        for (int i = 0; i < samples.length; i++) {
            byte[] compressed = codec.compressText(samples[i]);
            check(codec.decompressText(compressed).equals(samples[i]),
                    "Huffman text round trip " + i);
        }

        byte[] everyByte = new byte[256];
        for (int i = 0; i < everyByte.length; i++) {
            everyByte[i] = (byte) i;
        }
        byte[] compressed = codec.compress(everyByte);
        check(equal(codec.decompress(compressed), everyByte), "Huffman binary round trip");
        HuffmanCodec.CompressionResult details = codec.compressDetailed(
                new byte[] {1, 1, 1, 1, 2, 2, 3});
        check(details.originalByteCount() == 7, "Compression original length");
        check(details.compressedByteCount() > 0, "Compression output length");

        byte[] truncated = new byte[compressed.length - 1];
        for (int i = 0; i < truncated.length; i++) {
            truncated[i] = compressed[i];
        }
        expectFailure(new Action() {
            @Override
            public void run() {
                codec.decompress(truncated);
            }
        }, "Truncated Huffman payload rejected");
    }

    private static void testIntegerSorts() {
        int[] source = {5, -1, 0, 5, -99, 42};
        int[] expected = {-99, -1, 0, 5, 5, 42};
        int[][] copies = new int[5][];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = copy(source);
        }
        IntegerSorts.mergeSort(copies[0]);
        IntegerSorts.quickSort(copies[1]);
        IntegerSorts.heapSort(copies[2]);
        IntegerSorts.countingSort(copies[3]);
        IntegerSorts.radixSort(copies[4]);
        for (int i = 0; i < copies.length; i++) {
            check(equal(copies[i], expected), "Integer sort " + i);
        }

        int[] extremes = {Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1, 1};
        IntegerSorts.radixSort(extremes);
        check(equal(extremes, new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}),
                "Radix sort complete signed domain");
        expectFailure(new Action() {
            @Override
            public void run() {
                IntegerSorts.countingSort(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE});
            }
        }, "Counting sort range guard");
    }

    private static void testGenericSorts() {
        RankedValue[] values = {
            new RankedValue("a", 3),
            new RankedValue("b", 1),
            new RankedValue("c", 2),
            new RankedValue("d", 1)
        };
        GenericSorts.Ordering<RankedValue> ordering = new GenericSorts.Ordering<RankedValue>() {
            @Override
            public int compare(RankedValue first, RankedValue second) {
                return first.score < second.score ? -1 : (first.score == second.score ? 0 : 1);
            }
        };
        GenericSorts.mergeSort(values, ordering);
        check(GenericSorts.isSorted(values, ordering), "Generic merge sort order");
        check(values[0].id.equals("b") && values[1].id.equals("d"),
                "Generic merge sort stability");

        RankedValue[] quickValues = copy(values);
        RankedValue[] heapValues = copy(values);
        GenericSorts.quickSort(quickValues, ordering);
        GenericSorts.heapSort(heapValues, ordering);
        check(GenericSorts.isSorted(quickValues, ordering), "Generic quick sort order");
        check(GenericSorts.isSorted(heapValues, ordering), "Generic heap sort order");
    }

    private static void testBenchmarks() {
        BenchmarkResult[] results = AlgorithmBenchmark.benchmarkGenerated(256, 2, 42L);
        check(results.length == 5, "Benchmark algorithm count");
        long expectedChecksum = results[0].outputChecksum();
        for (int i = 0; i < results.length; i++) {
            check(results[i].isSuccessful(), "Benchmark success " + i);
            check(results[i].inputSize() == 256, "Benchmark input size " + i);
            check(results[i].outputChecksum() == expectedChecksum,
                    "Benchmark equivalent output " + i);
            check(results[i].averageNanoseconds() >= 0.0, "Benchmark non-negative timing " + i);
        }
    }

    private static WeightedGraph<String> sampleUndirectedGraph() {
        WeightedGraph<String> graph = new WeightedGraph<String>(false);
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("isolated");
        graph.addEdge(0, 1, 4.0);
        graph.addEdge(0, 2, 1.0);
        graph.addEdge(2, 1, 2.0);
        graph.addEdge(1, 3, 1.0);
        graph.addEdge(2, 3, 5.0);
        return graph;
    }

    private static int[] positions(int[] order) {
        int[] result = new int[order.length];
        for (int position = 0; position < order.length; position++) {
            result[order[position]] = position;
        }
        return result;
    }

    private static boolean close(double first, double second) {
        double difference = first - second;
        return difference < 0.0 ? -difference < 1.0e-9 : difference < 1.0e-9;
    }

    private static boolean equal(int[] first, int[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean equal(byte[] first, byte[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                return false;
            }
        }
        return true;
    }

    private static int[] copy(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    private static RankedValue[] copy(RankedValue[] source) {
        RankedValue[] result = new RankedValue[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    private static void expectFailure(Action action, String message) {
        boolean failed = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            failed = true;
        }
        check(failed, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Advanced algorithm test failed: " + message);
        }
        assertionCount++;
    }

    private interface Action {
        void run();
    }

    private static final class RankedValue {
        private final String id;
        private final int score;

        private RankedValue(String id, int score) {
            this.id = id;
            this.score = score;
        }
    }
}
