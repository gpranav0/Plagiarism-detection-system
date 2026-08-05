# Project Explanation

## Advanced Plagiarism Detection and Academic Integrity Analysis System

This document explains the purpose, design, execution, data structures, algorithms, scoring method, interfaces, testing, and limitations of the project. It is written as a technical explanation suitable for a project report, demonstration, viva, or team handover.

---

## 1. Project purpose

The project compares student submissions with reference documents and identifies cases containing significant textual similarity. It does more than display a single percentage: it records the algorithms that found the similarity, the matched passages, source line and column locations, individual score components, graph relationships, and reviewer assignments.

The system is a decision-support tool. A high score means that a case deserves human review; it does not prove copying, intent, or academic misconduct. A reviewer must consider citations, quotations, common terminology, assignment templates, permitted collaboration, and the student's explanation before making a decision.

## 2. Main objectives

The project has the following objectives:

1. Import and validate student and reference documents safely.
2. Continue processing valid files even when another file is malformed.
3. Normalize and tokenize text while preserving original source locations.
4. Detect exact, shingle-based, and fuzzy similarities.
5. Reduce unnecessary comparisons using deterministic MinHash signatures.
6. Execute shortlisted comparisons using bounded parallel workers.
7. Preserve separate, explainable score components.
8. Rank suspicious submissions using custom tree and heap indexes.
9. Represent document relationships using a custom weighted graph.
10. Assign reviewable cases without exceeding reviewer capacities.
11. Export readable and Huffman-compressed reports.
12. Demonstrate DSA concepts through live application features and verified tests.

## 3. Interfaces provided

### 3.1 Desktop interface

Running the application without arguments starts the Java Swing desktop interface. Its major screens are:

- **Dashboard:** corpus counts, analysis status, risk totals, and recent activity.
- **Documents:** import, validate, search, and preview submissions and references.
- **Run analysis:** select a submission or batch mode, configure thresholds and algorithms, and choose sequential or parallel execution.
- **Evidence and results:** inspect component scores and highlighted passages side by side.
- **Risk ranking:** view submissions ordered by their strongest reference match.
- **Similarity graph:** inspect graph nodes, relationships, groups, and paths.
- **Reviewer routing:** calculate and inspect capacity-safe assignments.
- **Reports and logs:** preview reports, export files, decode Huffman output, and inspect activity or error logs.
- **Settings and benchmarks:** edit validated settings and run measured algorithm comparisons.

Swing and AWT code is confined to the `ui` package. The UI calls a controller and headless service layer instead of placing algorithm logic inside button handlers.

### 3.2 Console interface

The legacy console exposes 13 options:

| Option | Operation | Main DSA or service involved |
|---:|---|---|
| 1 | Import student submissions | Buffered I/O, custom corpus store, B-tree/B+ indexes |
| 2 | Import reference documents | Buffered I/O, validation, duplicate detection |
| 3 | Validate files and metadata | Custom hash set and index invariant checks |
| 4 | Analyze one submission | Exact, shingle, and fuzzy comparison without MinHash filtering |
| 5 | Run batch analysis | MinHash shortlisting and bounded parallel workers |
| 6 | Display matched passages | Evidence arrays and normalized-to-source offset mapping |
| 7 | Rank suspicious submissions | BST, AVL tree, max-heap, segment tree, and Fenwick tree |
| 8 | Display groups and paths | BFS, DFS, union-find, Kruskal, and Dijkstra |
| 9 | Assign reviewers | Merge ranking and Edmonds–Karp maximum flow |
| 10 | Export reports | Atomic text output and Huffman coding |
| 11 | Display benchmarks | Five manual sorts, trees, MinHash, and sequential/parallel comparison |
| 12 | View logs | Buffered tail reading |
| 13 | Exit safely | Controlled shutdown and activity logging |

### 3.3 Deterministic demo

The `--demo` mode imports the bundled corpus, validates it, performs batch analysis, assigns reviewers, exports reports, runs benchmarks, checks index invariants, and prints `DEMO_SUCCESS` when every required stage completes.

## 4. High-level architecture

```text
Swing UI or console
        |
        v
ApplicationController
        |
        v
ApplicationService / AcademicIntegritySystem
        |
        +--> Corpus import, validation, settings, and logging
        +--> Text preparation and comparison engine
        +--> Custom document and result indexes
        +--> Similarity-network analytics
        +--> Reviewer maximum-flow service
        +--> Report, Huffman, and benchmark services
        |
        v
Custom structures and manually implemented algorithms
```

The separation has several benefits:

- Algorithms can be tested without opening the graphical interface.
- The console, demo, and desktop UI reuse the same application logic.
- UI background tasks can report progress and cancellation without changing the algorithms.
- File handling, scoring, graph analysis, and reporting remain independently maintainable.

## 5. Package responsibilities

| Package | Responsibility |
|---|---|
| `app` | Entry point, application facade, console, lifecycle, and deterministic demo |
| `ui` | Swing screens, controls, evidence highlighting, and custom graph painting |
| `controller` | Background task coordination, progress, cancellation, and friendly error translation |
| `service` | Headless workflow operations and immutable screen projections |
| `config` | Settings model, parsing, and validation |
| `io` | Corpus parsing, importing, validation, quarantine, reviewers, stopwords, and logs |
| `model` | Documents, evidence, scores, results, assignments, and graph views |
| `core` | Text preparation, pair analysis, candidate planning, and deterministic batch execution |
| `structures` | All reusable custom collections and data structures |
| `index` | Document, result, range, and persistent indexes |
| `algorithms.text` | Exact search, tries, shingles, MinHash, and dynamic programming |
| `algorithms.graph` | Weighted graph, traversal, MST, shortest paths, and directed algorithms |
| `algorithms.flow` | Edmonds–Karp maximum flow |
| `algorithms.greedy` | Evidence compaction and general set-cover support |
| `algorithms.sort` | Manual comparison and integer sorting algorithms |
| `algorithms.compression` | Huffman compression and decompression |
| `analytics` | Similarity groups, graph signals, compact links, and paths |
| `review` | Threshold filtering, ranking, and reviewer assignment |
| `report` | Human-readable summaries and compressed report export |
| `benchmark` | User-visible timing and invariant measurements |
| `exception` | Project-specific checked and validation exceptions |

## 6. End-to-end processing flow

```text
UTF-8 text files
    -> file and metadata validation
    -> safe corpus import and duplicate-ID detection
    -> Unicode-aware normalization and tokenization
    -> optional stopword removal through a custom trie
    -> word-shingle MinHash signatures
    -> candidate shortlisting
    -> exact, shingle, and fuzzy verification
    -> deterministic result merge
    -> composite scoring and evidence compaction
    -> custom risk indexes and range analytics
    -> similarity graph construction and enrichment
    -> maximum-flow reviewer assignment
    -> readable and Huffman-compressed reports
```

Single-document analysis compares the chosen submission with every loaded reference and deliberately bypasses MinHash candidate filtering. Batch analysis uses MinHash to avoid performing expensive verification on clearly unrelated pairs.

## 7. File input and validation

### 7.1 Supported document format

Documents are strict UTF-8 `.txt` files. They may begin with optional metadata:

```text
ID: SUBMISSION_001
TITLE: Dynamic Programming Assignment
AUTHOR: Student Name

The document body begins here.
```

If metadata is absent, the filename without `.txt` becomes the document ID and title. IDs may contain letters, digits, hyphens, and underscores.

### 7.2 Validation rules

The importer checks:

- file existence and regular-file status;
- read permission;
- `.txt` extension;
- configured maximum size;
- strict UTF-8 correctness;
- nonempty body;
- at least one searchable letter or digit;
- valid metadata and document ID;
- duplicate IDs across the corpus.

A bad file does not terminate the batch. Its error is recorded, a uniquely named recovery copy is placed in `data/quarantine` when possible, and the importer continues with the next file.

### 7.3 Source-location preservation

Normalization changes case and replaces punctuation or repeated whitespace, but the engine stores a normalized-to-original UTF-16 offset map. Evidence found in normalized text can therefore be translated back to the original file's line and column. Metadata lines, indentation, blank lines, LF, CRLF, and CR line endings are accounted for.

## 8. Persistent document catalog

`PersistentDocumentIndex` stores each document's ID, type, and absolute source path in custom B-tree and B+ tree indexes. The catalog is written to `data/index/document-index.tsv`.

Persistence features include:

- strict UTF-8 encoding and decoding;
- a magic header, format version, declared record count, and sorted record validation;
- escaped control characters in stored fields;
- temporary-write-then-replace behavior;
- atomic filesystem replacement when supported;
- recovery from a complete interrupted `.tmp` snapshot;
- isolation of malformed snapshots as unique invalid copies;
- rollback of the in-memory catalog mutation when a write fails;
- custom-tree and source-accessibility invariant checks.

Tree lookup or update is `O(log n)`, followed by an `O(n + bytes)` deterministic snapshot rewrite. Loading the snapshot and rebuilding its custom trees is `O(n log n + bytes)`.

The catalog does not store document bodies, prepared tokens, analysis results, assignments, or graph state. Those values are re-imported or recomputed so changed source files are validated again.

## 9. Custom data structures

The core does not use Java collection implementations. It uses arrays, generics, and custom nodes.

| Structure | Live project role | Main complexity |
|---|---|---|
| `DynamicArray<T>` | Heap storage, tree traversals, snapshots, and resizable result storage | Indexed access `O(1)`; append amortized `O(1)` |
| `SinglyLinkedList<T>` | Variable-length evidence accumulation | End insertion `O(1)`; search/indexed access `O(n)` |
| `LinkedStack<T>` | Iterative DFS | Push/pop `O(1)` |
| `LinkedQueue<T>` | BFS, component traversal, and level-order operations | Offer/poll `O(1)` |
| `HashTable<K,V>` | Base for project-owned hashed lookup | Expected `O(1)`; collision worst `O(n)` |
| `HashSet<T>` | Duplicate IDs, shingles, evidence deduplication, and ranking aggregation | Expected `O(1)` membership |
| `MinHeap<T>` | Dijkstra frontier | Insert/poll `O(log n)`; peek `O(1)` |
| `MaxHeap<T>` | Highest-risk retrieval | Insert/poll `O(log n)`; peek `O(1)` |
| `BinarySearchTree<T>` | Case-ID lookup and benchmark comparison | Average `O(log n)`; skewed worst `O(n)` |
| `AVLTree<T>` | Balanced risk-score ranking | Search/insert/delete `O(log n)` |
| `BTree<T>` | Ordered document-ID catalog | Fixed-degree operations `O(log n)` |
| `BPlusTree<K,V>` | Document lookup, ranges, file-path index, and persistent record order | Lookup/insert `O(log n)`; range `O(log n + output)` |
| `SegmentTree` | Ranked score sum, minimum, maximum, and range updates | Build `O(n)`; query/update `O(log n)` |
| `FenwickTree` | Prefix and range counts above the review threshold | Query/update `O(log n)` |
| `DisjointSet` | Similarity grouping and Kruskal's forest | Amortized `O(alpha(n))` |
| `WeightedGraph<V>` | Document similarity network using custom adjacency nodes | Storage `O(V + E)` |

All structures expose relevant operations such as insertion, lookup, update, traversal, removal, and invariant validation. Their invariants are exercised by the custom test runner.

## 10. Text preparation

`TextPreparation` performs the application-level normalization:

1. Iterate through the original content by Unicode code point.
2. Retain letters and digits.
3. Convert retained characters to lowercase.
4. Replace punctuation and whitespace runs with one space.
5. Record the original offset corresponding to every normalized UTF-16 unit.
6. Tokenize the normalized text.
7. Normalize and index configured stopwords in the custom generic trie.
8. Remove exact stopword tokens when enabled.

The prepared normalized text is used for exact and character-level algorithms. The filtered token array is used for word shingles, MinHash, and token-based dynamic programming.

## 11. Exact matching algorithms

### 11.1 Knuth–Morris–Pratt

KMP builds a prefix table for the pattern and avoids restarting from the beginning after a mismatch. It is used to locate exact phrases and map evidence positions.

- Time: `O(n + m)` for one search, plus reported matches.
- Space: `O(m)` for the prefix table.

### 11.2 Rabin–Karp

Rabin–Karp uses a rolling hash to find potential phrase matches. Every hash hit is verified character by character, so a hash collision cannot create false evidence.

- Expected time: approximately `O(n + m)` when hash hits are rare.
- Worst time: `O(nm)` when there are many genuine hits or collisions.

### 11.3 Z-algorithm

The Z-algorithm constructs a combined pattern-and-text sequence and records how much of the pattern matches at each position.

- Time: `O(n + m)`.
- Space: `O(n + m)`.

### 11.4 Trie and Aho–Corasick

The generic trie supports deterministic key and prefix lookup and is used for stopword filtering. A separate purpose-built Aho–Corasick automaton searches many eligible exact phrases in one pass using trie transitions and failure links.

The application limits the Aho pattern collection to 512 phrases to bound memory and processing time on large files.

## 12. Shingle similarity

A shingle is a consecutive sequence extracted from text.

- **Word shingles:** consecutive filtered tokens; the bundled width is 4.
- **Character shingles:** consecutive normalized UTF-16 units; the bundled width is 9.

For two shingle sets `A` and `B`, Jaccard similarity is:

```text
J(A, B) = |A intersect B| / |A union B|
```

The shingle score combines both views:

```text
Shingle score = 0.65 * word Jaccard + 0.35 * character Jaccard
```

Word shingles capture reordered phrase overlap, while character shingles retain some similarity when small word-level changes occur.

## 13. Fuzzy matching and dynamic programming

### 13.1 Longest common subsequence

LCS measures tokens that remain in the same relative order even when other tokens are inserted or removed.

- Score-path time: `O(mn)`.
- Score-path space: `O(min(m,n))` using rolling rows.
- Full sequence reconstruction, available through the public API, requires `O(mn)` memory.

### 13.2 Edit distance

Edit distance counts the minimum insertions, deletions, and substitutions needed to transform one token sequence into another. The value is converted into a normalized similarity.

- Time: `O(mn)`.
- Space: `O(min(m,n))`.

### 13.3 Smith–Waterman local alignment

Smith–Waterman finds the strongest locally aligned token passages rather than requiring the entire documents to match. It supplies the fuzzy evidence ranges displayed in the UI and reports.

- Time: `O(mn)`.
- Rolling-row score space: linear in the shorter sequence, plus alignment output.

The fuzzy component is:

```text
Fuzzy score = 0.50 * Smith-Waterman
            + 0.30 * LCS similarity
            + 0.20 * edit similarity
```

To control quadratic dynamic-programming cost, application analysis uses at most the first 900 filtered tokens from each document.

## 14. MinHash candidate shortlisting

Full verification of every submission-reference pair can be expensive. Batch mode therefore creates a deterministic 128-value MinHash signature from each document's word shingles.

The estimated similarity is the proportion of equal signature positions. A pair is shortlisted when its estimate reaches `candidateThreshold`, which is 0.12 in the bundled settings. Documents too short to form the configured word shingles are force-compared.

MinHash reduces expensive verification work but remains an estimate. The current implementation checks every cross-corpus signature pair, so shortlisting itself is still proportional to `submissions * references * signatureLength`; it is not an LSH band index.

If shingle analysis is disabled through settings or the desktop analysis request, the planner compares all submission-reference pairs instead of using a shingle-based shortlist.

## 15. Deterministic parallel analysis

Accepted candidate pairs can be processed by a bounded fixed worker pool. The bundled worker limit is 4.

Determinism is preserved as follows:

1. Assign every possible pair a stable row-major ordinal.
2. Submit work only for shortlisted ordinals.
3. Store each future in its ordinal slot.
4. Retrieve and merge results by ordinal rather than completion time.

Consequently, sequential and parallel modes produce the same ordered cases and scores even if worker completion order changes. Runtime measurements may naturally differ.

## 16. Composite plagiarism score

The system preserves four normalized components:

- `E`: exact phrase coverage;
- `S`: word/character shingle similarity;
- `F`: fuzzy alignment similarity;
- `G`: similarity-graph signal.

With the bundled weights, the final score is:

```text
Total = (0.35E + 0.30S + 0.25F + 0.10G)
```

If users choose weights that do not total 1, the implementation divides by their positive total. Weights must be finite and non-negative.

### Example from the bundled corpus

The highest-risk sample pair produced:

```text
Exact:     88.17%
Shingle:   84.13%
Fuzzy:     89.96%
Graph:     63.11%
Composite: 84.90%
```

The arithmetic is approximately:

```text
0.35(0.8817) + 0.30(0.8413) + 0.25(0.8996) + 0.10(0.6311)
= 0.8490
= 84.90%
```

Risk labels are display priorities:

| Score | Label |
|---:|---|
| 75% or higher | `CRITICAL` |
| 55% to below 75% | `HIGH` |
| 35% to below 55% | `MEDIUM` |
| Below 35% | `LOW` |

The configured reviewer threshold is independent of these labels. In the bundled settings, cases scoring at least 30% are eligible for reviewer routing.

## 17. Evidence selection and explainability

Every evidence record contains:

- evidence type;
- algorithm name;
- similarity value;
- submission and reference character ranges;
- translated line and column locations;
- a readable excerpt.

A pair can generate many overlapping matches. The greedy evidence selector assigns coverage units to evidence families and individual passages, then chooses a compact deterministic set based on new coverage, relevance, and excerpt cost. The bundled maximum is 8 evidence passages per case.

This is a practical greedy heuristic. It does not claim to find a mathematically unique or globally optimal explanation.

## 18. Similarity graph

Each loaded document becomes a vertex. A verified pair becomes an undirected weighted relationship when its composite score reaches `graphEdgeThreshold`, which is 0.28 in the bundled configuration.

The graph supports:

- **BFS:** level-style reachability using `LinkedQueue` in `O(V + E)`.
- **DFS:** iterative exploration using `LinkedStack` in `O(V + E)`.
- **Union-find groups:** connected similarity components using path compression and union by size.
- **Kruskal:** a compact minimum spanning forest after converting similarity to dissimilarity cost; `O(E log E)`.
- **Dijkstra:** a low-dissimilarity relationship path using the custom `MinHeap`; approximately `O((V + E) log E)` with stale heap entries.

The graph signal combines direct pair similarity and local connection density. It is association evidence only. The graph is undirected and contains no reliable chronology, so a path cannot establish who copied from whom.

Topological sort and strongly connected components are fully implemented and tested as directed-graph APIs, but the application does not fabricate a causal direction for the current similarity data. Prim, Bellman–Ford, and Floyd–Warshall are also implemented and tested alternatives; the live workflow selects Kruskal and non-negative Dijkstra for its actual graph model.

## 19. Risk indexes and range analytics

`ResultIndex` combines several structures:

- a BST for case-ID lookup;
- an AVL tree for balanced score ordering;
- a max-heap for highest-risk access.

Menu and desktop ranking aggregate cases so each submission appears once, represented by its strongest reference match.

`RangeAnalytics` builds:

- a segment tree for score sum, minimum, maximum, and average;
- a Fenwick tree for counts meeting the review threshold.

Ordinary range queries and point updates are `O(log n)`. Results are stored at fixed precision for deterministic range calculations.

## 20. Reviewer assignment with maximum flow

The reviewer workflow first filters cases whose composite score reaches `reviewThreshold` and sorts them deterministically by risk.

It then constructs a flow network:

```text
source -> case vertices -> reviewer vertices -> sink
```

Capacities are:

- source to each case: 1;
- each eligible case to each reviewer: 1;
- reviewer to sink: the reviewer's configured capacity.

Edmonds–Karp repeatedly uses BFS to find augmenting paths in the residual graph. The resulting flow guarantees that:

- no case is assigned more than once;
- no reviewer exceeds their capacity;
- the number of assigned cases is maximized under the current eligibility model.

The current model considers every valid reviewer eligible for every reviewable case. It does not optimize expertise, conflicts of interest, preferences, dates, or fairness. Cases left unassigned because capacity is insufficient are explicitly marked as awaiting assignment.

## 21. Report generation and Huffman coding

Every export creates a new directory:

```text
reports/run-<timestamp>/
```

It contains:

- one readable `.txt` report per verified case;
- one self-contained `.huff` compressed version per case;
- `analysis-summary.txt` for the entire run.

Reports include component scores, risk label, evidence algorithms, excerpts, line and column ranges, graph evidence, reviewer status, runtime, and a human-review warning.

Huffman coding counts UTF-8 byte frequencies, repeatedly merges the least frequent nodes, constructs prefix-free codes, and stores enough header information to rebuild the tree during decompression. With the fixed 256-byte alphabet, processing is effectively linear in report input and output size. A short or high-entropy report can be larger after compression because the header has a cost.

Reports are written to temporary files and replaced atomically when supported. Huffman compression is not encryption.

## 22. Sorting and benchmarks

The project manually implements:

- merge sort;
- quicksort;
- heap sort;
- counting sort;
- radix sort.

The benchmark service runs these algorithms on the same deterministic inputs and also measures:

- ordered BST versus AVL insertion;
- B-tree and B+ tree invariants;
- MinHash candidate reduction;
- sequential versus parallel analysis;
- KMP exact matching versus Smith–Waterman fuzzy alignment;
- approximate JVM memory change.

These measurements demonstrate relative behavior on the current machine and data. They are not scientific JVM benchmarks because they do not use isolated forks, statistical confidence intervals, or complete warm-up control.

## 23. Configuration

The principal bundled settings are:

| Setting | Value | Meaning |
|---|---:|---|
| `enableExact` | true | Enables exact-pattern evidence and scoring |
| `enableShingle` | true | Enables word/character shingle analysis |
| `enableFuzzy` | true | Enables dynamic-programming fuzzy analysis |
| `enableGraph` | true | Enables graph construction and graph-signal scoring |
| `wordShingleSize` | 4 | Tokens in each word shingle |
| `characterShingleSize` | 9 | UTF-16 units in each character shingle |
| `minExactPhraseCharacters` | 28 | Minimum exact evidence phrase length |
| `candidateThreshold` | 0.12 | MinHash estimate needed for batch verification |
| `reviewThreshold` | 0.30 | Composite score needed for reviewer routing |
| `graphEdgeThreshold` | 0.28 | Score needed for a graph relationship |
| `exactWeight` | 0.35 | Composite exact contribution |
| `shingleWeight` | 0.30 | Composite shingle contribution |
| `fuzzyWeight` | 0.25 | Composite fuzzy contribution |
| `graphWeight` | 0.10 | Composite graph contribution |
| `maxEvidence` | 8 | Maximum compact evidence records per case |
| `workerCount` | 4 | Maximum batch comparison workers |
| `maxFileBytes` | 2,000,000 | Maximum imported document size |
| `removeStopwords` | true | Enables configured stopword filtering |

Malformed, unknown, non-finite, or out-of-range settings invalidate the file and cause logged recovery to safe built-in defaults.

## 24. Project directories

```text
AdvancedPlagiarismDetectionSystem/
|-- config/settings.txt
|-- data/
|   |-- submissions/
|   |-- references/
|   |-- reviewers/reviewers.txt
|   |-- index/document-index.tsv
|   |-- quarantine/
|   `-- stopwords.txt
|-- reports/
|-- logs/
|-- benchmarks/
|-- src/main/java/edu/academic/integrity/
|-- src/test/java/edu/academic/integrity/tests/
|-- build.ps1
|-- run.ps1
`-- test.ps1
```

## 25. Compilation and execution

Run all commands from the project root:

```powershell
cd C:\Users\John\Documents\dsa3\AdvancedPlagiarismDetectionSystem
```

### Direct Java compilation

```powershell
$files = (Get-ChildItem .\src\main\java -Recurse -Filter *.java).FullName
javac --release 17 -encoding UTF-8 -d out $files
```

The project contains many packages. Compiling only `Main.java` from its package directory will fail because its dependent package sources are outside that compilation command.

### Start the desktop UI

```powershell
java -cp out edu.academic.integrity.app.Main
```

### Start the 13-option console

```powershell
java -cp out edu.academic.integrity.app.Main --console
```

### Run the deterministic demo

```powershell
java -cp out edu.academic.integrity.app.Main --demo
```

### Run through the helper scripts

```powershell
.\build.ps1
.\run.ps1
.\test.ps1
```

The helper scripts serialize concurrent build/run operations and start Java with bounded memory. If PowerShell script execution is disabled, use `Set-ExecutionPolicy -Scope Process Bypass` for the current terminal only.

## 26. Testing and verification

The dependency-free test runner validates:

- dynamic-array growth and removal;
- hash collisions, growth, and shrink behavior;
- stack and queue ordering;
- min-heap and max-heap invariants;
- BST operations;
- all AVL rotation families and deletion;
- B-tree and B+ tree splits, ranges, deletion, and invariants;
- segment-tree and Fenwick-tree queries and updates;
- union-find compression and component sizes;
- exact pattern matching against deterministic and randomized cases;
- trie and Aho–Corasick behavior;
- LCS, edit distance, fuzzy alignment, and MinHash;
- all five sorting implementations;
- BFS, DFS, components, MST, shortest paths, topological sort, and SCC;
- maximum-flow conservation and reviewer capacity;
- greedy evidence selection and set cover;
- Huffman round trips and corrupt-data rejection;
- invalid, empty, duplicate, BOM-prefixed, punctuation-only, and malformed UTF-8 files;
- unique quarantine recovery copies;
- persistent-index save, reload, malformed isolation, and interrupted temporary recovery;
- source-coordinate mapping and Unicode supplementary-plane content;
- zero-candidate analysis and report export;
- sequential and parallel result equality;
- service/controller progress, cancellation, projections, and friendly error boundaries;
- desktop UI construction, navigation, accessibility metadata, and package-boundary enforcement;
- end-to-end sample import, analysis, ranking, assignment, compression, and export.

The latest verified run completed:

```text
Structure assertions: 14855
Text assertions: 799
Advanced assertions: 89
Service/controller assertions: 19
Desktop UI assertions: 8
Integration assertions: 76
ALL_TESTS_PASSED: 15846 assertions
```

The bundled demo processed 4 submissions and 3 references:

```text
Total possible pairs: 12
MinHash candidates: 3
Verified comparisons: 3
Candidate reduction: 75.00%
Highest sample score: 84.90%
Reviewer assignments: 3
DEMO_SUCCESS
```

## 27. Standard-library boundary

The algorithmic core does not use `ArrayList`, `LinkedList`, `HashMap`, `HashSet`, `TreeMap`, `TreeSet`, library stack/queue/heap classes, `Collections.sort`, `Arrays.sort`, streams, or external algorithm libraries.

Standard facilities are restricted to application boundaries:

- `javax.swing` and `java.awt` for the desktop interface, confined to `ui`;
- `java.io` for console and buffered file operations;
- `java.nio.charset` for strict UTF-8 processing;
- `java.nio.file` for safe temporary replacement and atomic moves;
- `java.time` for log timestamps;
- `java.util.concurrent` for bounded analysis workers and the UI-neutral background controller.

## 28. Error handling and recovery

The system uses try-with-resources for file handles and custom exceptions for validation and project errors. Recoverable problems are logged rather than terminating an entire batch.

Important recovery behaviors include:

- safe defaults after an invalid settings file;
- skipping and quarantining individual invalid documents;
- strict rejection of corrupt UTF-8;
- duplicate document and reviewer detection;
- isolated report directories for every export;
- persistent-index rollback after a failed write;
- safe EOF and window-close shutdown;
- cancellation and friendly background-task error reporting in the desktop UI;
- invalidation of stale analysis state when the imported corpus changes.

## 29. Limitations

1. Similarity is not proof of plagiarism or intent.
2. The engine is lexical, not semantic. Translation, synonym-heavy paraphrase, and conceptual copying can evade it.
3. Only strict UTF-8 `.txt` documents are imported; PDF, DOCX, OCR, HTML, images, and source-code syntax analysis are outside scope.
4. MinHash can omit a borderline pair because it is a probabilistic estimate.
5. Candidate signature comparison still examines every submission-reference signature pair.
6. Fuzzy analysis is limited to the first 900 filtered tokens per document.
7. Character analysis is bounded to 100,000 normalized UTF-16 units and can split a surrogate pair in a character shingle.
8. Aho–Corasick application evidence is capped at 512 phrases.
9. The graph is undirected and cannot prove chronology or copying direction.
10. Similarity matrices and maximum-flow matrices limit very large graph/reviewer workloads.
11. Reviewer routing models threshold and capacity only.
12. Persistent records store metadata and absolute paths, not document content.
13. Analysis results and graph state are rebuilt after restart.
14. Reports, logs, source files, index files, and Huffman files are local and unencrypted.
15. Benchmark timings are informative rather than scientifically controlled.

## 30. Ethical use

The system should be used only with authorized academic material. Institutions should minimize retained personal data, restrict access to reports and logs, protect local output files, allow contextual human review, and provide a fair process for students to respond. Automated output must never be treated as a final misconduct verdict.

## 31. Related documentation

- `README.md` contains operating instructions and the user-facing project overview.
- `ALGORITHM_MAPPING.md` contains detailed time/space complexity and application mappings.
- `TEAM_ROLES.md` divides responsibilities among five team members.
- `config/settings.txt` contains the active thresholds and score weights.
