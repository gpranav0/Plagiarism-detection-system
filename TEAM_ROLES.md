# Team Roles and Development Boundaries

This project is divided among five members by package ownership and stable Java interfaces. An owner is responsible for implementation quality, tests, and review of changes in the owned area. Other members may consume public APIs but should not edit an owned package without coordinating with its owner first.

## Member 1: corpus ingestion, file handling, validation, exceptions, and logging

### Owned code

- `edu.academic.integrity.io`
  - `ProjectPaths`, `DocumentFileParser`, `CorpusImporter`, `CorpusStore`
  - `CorpusValidationService`, `ImportSummary`, `ValidationSummary`
  - `StopwordLoader`, `ReviewerLoader`, `ActivityLogger`, `LogReader`
- `edu.academic.integrity.config`
  - `Settings`, `SettingsLoader`
- `edu.academic.integrity.exception`
  - `ProjectException`, `ValidationException`, `ImportException`, `DuplicateDocumentException`
- Persistent input layout: `data/submissions`, `data/references`, `data/reviewers`, `data/stopwords.txt`, `data/index`, `config/settings.txt`, `logs`, and quarantine handling.

### Stable boundary

Member 1 converts filesystem input into shared model objects and arrays. The principal outward-facing contracts are:

- `SettingsLoader.load(File)` -> validated `Settings`
- `CorpusImporter.importDirectory(File, DocumentType, CorpusStore)` -> `ImportSummary`
- `CorpusStore.add/find/all/size/clear`
- `CorpusValidationService.validate(File, File)` -> `ValidationSummary`
- `StopwordLoader.load(File, ActivityLogger)` -> `String[]`
- `ReviewerLoader.load(File, ActivityLogger)` -> `Reviewer[]`
- `ActivityLogger.info/error` and `LogReader.tail`

No matching, ranking, graph, or report logic belongs in this layer. A bad file must be isolated and logged without aborting the remaining batch.

### Testing responsibility

- Missing, empty, oversized, inaccessible, malformed, and unsupported files
- Duplicate document IDs and metadata validation
- Directory batch continuation after individual failures
- Quarantine behavior, stopword/settings/reviewer parsing, and log output
- Round-trip verification of all sample input files

## Member 2: custom structures, trees, indexing, and invariant tests

### Owned code

- `edu.academic.integrity.structures`
  - `Ordering`, `DynamicArray`, `SinglyLinkedList`, `LinkedStack`, `LinkedQueue`
  - `HashTable`, `HashSet`
  - `BinaryHeap`, `MinHeap`, `MaxHeap`
  - `BinarySearchTree`, `AVLTree`, `BTree`, `BPlusTree`
  - `SegmentTree`, `FenwickTree`, `DisjointSet`
- `edu.academic.integrity.index`
  - `DocumentIndex`, `PersistentDocumentIndex`, `ResultIndex`, `RangeAnalytics`
- `StructureSelfTests`

### Stable boundary

Structures expose generic operations, custom `DynamicArray` snapshots where appropriate, and `validateInvariant()` methods. Project-facing index APIs return ordinary typed arrays so consumers do not depend on internal node layouts:

- `DocumentIndex`: document ID lookup, ID/path ranges, ordered snapshots, add/replace/remove, and validation summary
- `PersistentDocumentIndex`: strict UTF-8 versioned ID/type/path snapshot, custom B-tree/B+ lookup, atomic save/recovery, and invariant validation
- `ResultIndex`: case lookup, descending risk ranking, score ranges, top-risk retrieval, and validation summary
- `RangeAnalytics`: score min/max/sum/average, point and range updates, cumulative flagged counts, and validation summary

Node classes, capacities, rotation helpers, hash buckets, heap storage, and tree separator rules are private implementation details. Consumers must not infer traversal order unless the public method explicitly promises it.

### Testing responsibility

- Dynamic-array resizing and hash collision/growth/shrink behavior
- Stack, queue, linked-list, min-heap, and max-heap ordering
- BST deletion and all AVL LL, RR, LR, and RL rotations
- AVL height/order/count invariants after deletion
- B-tree split, borrow, merge, deletion, occupancy, and equal leaf depth
- B+ tree splits, separators, linked leaves, ordered ranges, deletion, and leaf-depth invariants
- Segment-tree lazy propagation and range aggregates
- Fenwick prefix/range sums and disjoint-set compression/component sizes
- Cross-index size/reference consistency in every validation summary
- Persistent-index save/reload, interrupted temporary recovery, malformed-snapshot isolation, and tree cross-reference invariants

## Member 3: exact and fuzzy text-matching engine

### Owned code

- `edu.academic.integrity.algorithms.text`, except `MinHash` (owned by Member 4)
  - `TextNormalizer`, `ShingleGenerator`, `Shingler`
  - `RabinKarp`, `KMP`, `ZAlgorithm`
  - `Trie`, `AhoCorasick`
  - `LCS`, `LongestCommonSubsequence`, `EditDistance`
  - `FuzzyAlignment`, `SmithWaterman`
- `edu.academic.integrity.core`
  - `TextPreparation`, `DocumentAnalyzer`
- `TextAlgorithmSelfTests`

### Stable boundary

Member 3 consumes `Document`, `Settings`, and stopword arrays and produces immutable matching evidence:

- `TextPreparation.prepare(Document, String[], boolean)` prepares normalized text and tokens.
- `DocumentAnalyzer.prepare(Document)` performs configured preparation.
- `DocumentAnalyzer.analyze(...)` returns an `AnalysisResult` containing a visible `ScoreBreakdown` and `PassageMatch[]` evidence.

Character offsets returned by matchers must remain compatible with `Document.originalOffsetForNormalized` and `Document.locate`. Exact, shingle, fuzzy, and graph components must remain separately visible; this layer must not collapse them into an unexplained single percentage.

### Testing responsibility

- Normalization, tokenization, stopword removal, and original-offset mapping
- Word and character shingles
- Rabin-Karp, KMP, Z-algorithm, trie, and Aho-Corasick matches
- LCS, edit distance, fuzzy alignment, and Smith-Waterman boundaries
- Empty text, repeated patterns, Unicode text, and no-match cases
- Evidence locations, excerpts, score-component bounds, and deterministic analysis output

## Member 4: randomized, graph, flow, approximation, and parallel algorithms

### Owned code

- `edu.academic.integrity.algorithms.text.MinHash`
- `edu.academic.integrity.algorithms.graph`
  - `WeightedGraph`, `DirectedGraphAlgorithms`, `MinimumSpanningTree`, `ShortestPaths`
- `edu.academic.integrity.algorithms.flow.EdmondsKarpMaxFlow`
- `edu.academic.integrity.algorithms.greedy`
  - `GreedyEvidenceSelector`, `SetCoverSolver`
- `edu.academic.integrity.algorithms.sort`
  - `GenericSorts`, `IntegerSorts`
- `edu.academic.integrity.algorithms.benchmark`
  - `AlgorithmBenchmark`, `BenchmarkResult`
- `edu.academic.integrity.algorithms.compression.HuffmanCodec`
- `edu.academic.integrity.analytics.SimilarityNetwork`
- `edu.academic.integrity.core.BatchAnalyzer`
- `AdvancedAlgorithmSelfTests`

### Stable boundary

- `BatchAnalyzer` accepts `Document[]`, delegates pair analysis through `DocumentAnalyzer`, and returns a deterministic `BatchAnalysisResult` for both sequential and bounded-parallel execution.
- `MinHash` may shortlist candidates but must not silently replace the exact/fuzzy comparison stage.
- `SimilarityNetwork` exposes BFS/DFS order, groups, compact relationships, shortest copying paths, degrees, and graph-signal enrichment through model arrays.
- `EdmondsKarpMaxFlow` exposes flow results used by Member 5's reviewer workflow; Member 5 owns the domain-level assignment policy.
- Sorting, MST, shortest-path, SCC/topological, set-cover, greedy evidence, Huffman, and benchmark classes remain reusable algorithm services without console or file-I/O policy.

All parallel merges must use stable case/document identifiers so results are identical to sequential output regardless of worker scheduling.

### Testing responsibility

- Fixed-seed MinHash repeatability and candidate reduction
- BFS, DFS, connected grouping, SCC, topological sorting, MST, and shortest paths
- Maximum-flow conservation, capacity limits, and infeasible assignment cases
- Greedy/set-cover coverage and deterministic tie-breaking
- Sequential versus parallel result equality and bounded worker behavior
- Merge, quick, heap, counting, and radix sort correctness without library sorting
- Huffman encode/decode round trips and malformed compressed input
- Benchmark outputs and graph structural invariants

## Member 5: console interface, reviewer workflow, reports, integration, and documentation

### Owned code

- `edu.academic.integrity.app`
  - `Main`, `ConsoleApplication`, `AcademicIntegritySystem`
- `edu.academic.integrity.review.ReviewerAssignmentService`
- `edu.academic.integrity.report`
  - `ReportWriter`, `ReportExportSummary`
- `edu.academic.integrity.benchmark.BenchmarkService`
- Shared integration contracts in `edu.academic.integrity.model`
  - Documents and evidence: `Document`, `DocumentType`, `SourceLocation`, `PassageMatch`, `MatchType`
  - Results and workflow: `ScoreBreakdown`, `AnalysisResult`, `BatchAnalysisResult`, `Reviewer`, `CaseAssignment`
  - Graph output: `RelationshipEdge`, `SimilarityGroup`, `CopyingPath`
- Root documentation, sample configuration/data, run/build/test instructions, and example reports

### Stable boundary

The console orchestrates services but does not reimplement algorithms. It passes model arrays between modules and presents errors without exposing internal nodes or mutable storage.

- `ReviewerAssignmentService.assign(...)` converts suspicious results and reviewers into `CaseAssignment[]`; `filterAndRank(...)` applies the review threshold.
- `ReportWriter.export(...)` writes text/compressed reports and returns `ReportExportSummary`; `format(...)` defines the human-readable case presentation.
- `BenchmarkService.run/runAndSave` presents algorithm measurements supplied by the benchmark layer.
- Model constructor/accessor signatures are shared contracts. Array-valued accessors must preserve defensive-copy behavior where the model promises it.

Member 5 coordinates any model change because it can affect all four other modules. Model changes require approval from every owner whose code consumes the changed type.

### Testing responsibility

- Every console option, invalid selection, recovery path, and safe exit
- Reviewer capacity enforcement, threshold filtering, ranking, and unassigned cases
- Report content, matched locations, visible component scores, compression output, and export failures
- Benchmark display/save behavior and log viewing
- End-to-end import -> prepare -> analyze -> graph/rank -> assign -> export flow
- Documentation commands on a clean checkout and sample-data reproducibility

## Shared integration duties

Every member must:

1. Preserve the restriction against built-in collection and algorithm implementations in the algorithmic core.
2. Compile with Java 17 and run the relevant self-tests before handing work to integration.
3. Add explicit validation and useful error messages at public boundaries; one invalid file or pair must not terminate a valid batch.
4. Keep outputs deterministic. Parallel code, hash traversal, and equal-score ranking must define stable tie-breaking before reaching the UI or reports.
5. Update callers and tests in the same coordinated change when an approved public interface changes.
6. Avoid modifying syllabus files, attachments, supplied presentation files, or `sources/`.

The expected dependency direction is:

```text
model + structures
       ^
       |-- io/config
       |-- text algorithms -> DocumentAnalyzer
       |-- randomized/graph/flow algorithms -> BatchAnalyzer/SimilarityNetwork
       |-- indexes and analytics
       `-- review/report/benchmark -> console application
```

Dependencies should point toward stable models and service interfaces. Lower layers must not call the console, reports, or interactive input.

## Conflict-minimizing development workflow

1. **Claim one owned unit.** Work in a member-specific branch such as `member-3/fuzzy-alignment`. Name the exact packages/classes being changed before coding.
2. **Agree on contracts first.** For cross-module work, provider and consumer owners agree on constructors, method signatures, array ordering, null/error behavior, and deterministic tie-breaking before implementation begins.
3. **Keep edits inside ownership.** Submit changes to another member's package as a small, separately reviewed commit. Do not combine them with formatting, renaming, or unrelated cleanup.
4. **Integrate provider before consumer.** Merge shared model/contract changes first, then the providing implementation and tests, then consuming modules, and finally console/report wiring.
5. **Use one owner for shared files.** Member 5 serializes changes to root documentation, build/run scripts, shared model classes, sample data, and the aggregate test runner. Other members supply focused text or patches instead of editing those files concurrently.
6. **Test locally at three levels.** Run the member's focused self-tests, compile the complete source tree, then run the aggregate suite. The integrating member also runs the end-to-end sample analysis.
7. **Resolve conflicts semantically.** Never accept a conflict side wholesale in shared contracts. Reconcile intended behavior, rerun provider and consumer tests, and record any compatibility decision in the same review.
8. **Make handoffs auditable.** A handoff states changed files, public API changes, test commands/results, known complexity or limitations, and the next owner expected to consume the work.
