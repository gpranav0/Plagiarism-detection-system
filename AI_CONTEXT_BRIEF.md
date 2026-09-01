# AI Context Brief — Advanced Plagiarism Detection and Academic Integrity Analysis System

> **Purpose of this file.** A single self-contained orientation document for an AI assistant
> that has no access to the repository. It distills `README.md`, `PROJECT_EXPLANATION.md`,
> `ALGORITHM_MAPPING.md`, and `TEAM_ROLES.md`, and adds current-state information that is not
> recorded in any of them. Where this brief and those files disagree, the source code wins —
> a few known discrepancies are called out explicitly in §13.

---

## 1. What this project is

A **Java 17 desktop application** that compares student submissions against reference
documents and produces *explainable evidence* for academic-integrity review. It is an
academic Data Structures & Algorithms project: nearly every collection, index, and algorithm
is hand-implemented rather than taken from `java.util`, and the application exists partly to
demonstrate those implementations working together on a realistic problem.

It ships three interfaces over one shared engine:

| Interface | Entry | Notes |
|---|---|---|
| Swing desktop workspace | `.\run.ps1` | The primary interface |
| Legacy console menu (13 options) | `.\run.ps1 --console` | For automation/marking |
| Deterministic end-to-end demo | `.\run.ps1 --demo` | Fixed corpus, prints `DEMO_SUCCESS` |

**Framing that matters.** The README is explicit that this is a *decision-support tool, not an
automated misconduct judge*. A high score means a case deserves human attention; it does not
establish authorship, intent, poor citation, or misconduct. Keep this framing in any user-facing
text, report wording, or UI copy.

---

## 2. Hard constraints (read before suggesting anything)

These are non-negotiable properties of the project. Violating them breaks the build or the
assignment's rules.

1. **Zero dependencies.** No Maven, no Gradle, no external JARs, no database, no network
   service. Compiled with plain `javac --release 17 -encoding UTF-8`.
   - This rules out JavaFX (unbundled from the JDK since 11), FlatLaf and every third-party
     look-and-feel, JUnit, Jackson/Gson, logging frameworks, and any web/Electron approach.
2. **Swing/AWT imports are allowed *only* in the `ui` package.** `build.ps1` greps the sources
   before compiling and **fails the build** if `javax.swing` or `java.awt` is imported outside
   a path containing `/ui/`. This keeps the algorithmic engine headless and testable.
3. **Standard-library use is deliberately minimal and justified.** Files that do import from
   `java.util.concurrent`, `java.nio`, etc. carry an inline comment explaining why. Follow that
   convention; don't casually introduce `java.util.List`/`HashMap` where a custom structure exists.
4. **Windows PowerShell toolchain.** `build.ps1`, `run.ps1`, `test.ps1`. They coordinate through a
   named mutex (`Local\AdvancedPlagiarismDetectionSystemExecution`) so concurrent build/run/test
   invocations queue rather than corrupt `out/`.
5. **Bounded memory by design.** Both compiler and runtime are launched with
   `-Xms32m -Xmx256m -XX:+UseSerialGC -XX:ActiveProcessorCount=2` so the project stays stable on
   student laptops and lab VMs. Don't propose changes that assume a large heap.

---

## 3. Scale and layout

~21,200 lines of Java across 139 files (133 main + 6 test).

```
AdvancedPlagiarismDetectionSystem/
├── build.ps1 / run.ps1 / test.ps1     PowerShell toolchain
├── README.md                          usage & reference
├── PROJECT_EXPLANATION.md             719-line report-style deep dive
├── ALGORITHM_MAPPING.md               algorithm → usage → complexity map
├── TEAM_ROLES.md                      5-member ownership boundaries
├── config/settings.txt                runtime configuration
├── data/
│   ├── submissions/  *.txt            4 bundled student documents
│   ├── references/   *.txt            3 bundled reference documents
│   ├── reviewers/reviewers.txt        reviewer roster + capacities
│   ├── stopwords.txt
│   ├── index/document-index.tsv       persistent document catalog
│   └── quarantine/                    rejected imports
├── reports/run-<timestamp>/           generated reports (.txt, .huff)
├── logs/                              activity.log, errors.log
├── benchmarks/algorithm-benchmarks.txt
├── out/                               compile output (wiped each build)
└── src/main/java/edu/academic/integrity/...
    src/test/java/edu/academic/integrity/tests/...
```

---

## 4. Architecture

Strictly layered, with a genuinely clean seam between engine and UI:

```
ui (Swing)  ──►  controller  ──►  service  ──►  app facade  ──►  core / algorithms / structures
                                                    │
                                                    └──► io, index, report, review, analytics
```

- **`ApplicationService`** (757 lines) — the headless integration boundary. Every method is
  `synchronized`; all long-lived domain state stays in the swappable facade
  (`AcademicIntegritySystem`), and the service returns only immutable DTOs and freshly built
  arrays. Also owns settings persistence with atomic write + rollback.
- **`ApplicationController`** (359 lines) — thin async wrapper. Synchronous getters
  (`dashboard()`, `ranked()`, `graphSnapshot()`, …) plus `*Async` methods taking a
  `TaskCallback<T>`, an `isBusy()` guard, and `cancelCurrentTask()`.
- **`ui`** — 29 files, ~4,850 lines. `MainFrame` (CardLayout shell) + `NavigationPanel` sidebar +
  nine screens, all extending `ScreenPanel` and implementing `Refreshable`, all talking to the
  host through the `UiHost` interface.

**Consequence worth knowing:** because the controller boundary is clean and the UI is fenced off
by the build script, the entire UI layer can be redesigned or replaced without touching the
~16,000-line engine.

### Package responsibilities

| Package | Role |
|---|---|
| `app` | `Main` (arg routing), `AcademicIntegritySystem` (domain facade), `ConsoleApplication` |
| `service` | `ApplicationService` + DTOs (`AnalysisRun`, `GraphSnapshot`, `AssignmentPlan`, `DashboardSnapshot`, `SettingsSnapshot`, …) |
| `controller` | `ApplicationController`, `TaskCallback` |
| `ui` | Swing only; `Theme`, `MainFrame`, nine screens, `GraphCanvas`, small components |
| `core` | `DocumentAnalyzer` (one pair), `BatchAnalyzer` (many pairs), `TextPreparation` |
| `algorithms/*` | `text`, `graph`, `flow`, `greedy`, `sort`, `compression`, `benchmark` |
| `structures` | 17 hand-written data structures |
| `index` | `DocumentIndex`, `ResultIndex`, `RangeAnalytics`, `PersistentDocumentIndex` |
| `io` | parsing, import, validation, corpus store, logging, project paths |
| `report` | `ReportWriter`, `ReportExportSummary` |
| `review` | `ReviewerAssignmentService` |
| `analytics` | `SimilarityNetwork` |
| `model` | `Document`, `AnalysisResult`, `PassageMatch`, `ScoreBreakdown`, `Reviewer`, … |
| `config` | `Settings`, `SettingsLoader` |
| `exception` | `ProjectException`, `ValidationException`, `ImportException`, `DuplicateDocumentException` |

---

## 5. The analysis pipeline

1. **Import & validate** — `DocumentFileParser` reads `.txt` as *strict* UTF-8
   (`CodingErrorAction.REPORT`, so malformed bytes are rejected rather than silently replaced),
   parses optional metadata, and enforces validation rules (§8). Rejects go to `data/quarantine/`.
2. **Text preparation** (`TextPreparation.prepare`) — one pass that lowercases, keeps only
   letters/digits, collapses separators to single spaces, and **builds a normalized→original
   character offset map** so every later finding can be reported at its true source location.
   Optional stopword removal via a `Trie`.
3. **Candidate shortlisting** (`BatchAnalyzer`) — deterministic **MinHash** (128-element
   signature) over word shingles; pairs below `candidateThreshold` are skipped. The demo corpus
   reduces 12 pairs → 3, a 75% reduction.
4. **Pairwise analysis** (`DocumentAnalyzer.analyze`) — three independent stages:
   - **Exact**: word phrases searched in the reference, rotating **KMP → Rabin-Karp → Z-algorithm**
     by phrase index (`i % 3`), plus one **Aho-Corasick** multi-pattern pass. Score = matched
     unique phrases / total unique phrases.
   - **Shingle**: Jaccard over word shingles and character shingles,
     combined as `0.65·word + 0.35·character`.
   - **Fuzzy**: `0.50·SmithWaterman + 0.30·LCS + 0.20·editDistance`, bounded to the first
     900 tokens.
5. **Evidence selection** — all candidate passages are reduced to `maxEvidence` (default 8) by a
   **greedy set-cover approximation** (`GreedyEvidenceSelector`) weighted so that one item from
   each match type is preferred over near-duplicate passages.
6. **Graph & grouping** (`SimilarityNetwork`) — documents become vertices, similarities above
   `graphEdgeThreshold` become weighted edges; BFS, connected components, MST, and shortest paths
   yield similarity groups and "copying paths."
7. **Indexing** — `ResultIndex` for ranking, `RangeAnalytics` (SegmentTree + FenwickTree) for
   range score statistics and cumulative flagged counts, `PersistentDocumentIndex` for the catalog.
8. **Reviewer assignment** (`ReviewerAssignmentService`) — builds a
   source→cases→reviewers→sink network and runs **Edmonds-Karp max flow** so reviewer capacities
   are respected exactly.
9. **Reporting** — `ReportWriter` emits per-case and summary text reports into
   `reports/run-<timestamp>/`; **Huffman** coding compresses them to `.huff` on request.

### Parallelism

`BatchAnalyzer.compareParallel` uses a fixed pool of `workerCount` threads. Determinism is
preserved by assigning each candidate pair a fixed ordinal slot in the results array before
submission, so output order never depends on completion order. All documents are prepared
*before* the parallel phase, so workers only read shared `Document` state.

---

## 6. Scoring model

Four normalized components combined by weight:

```
Total = 0.35·Exact + 0.30·Shingle + 0.25·Fuzzy + 0.10·Graph
```

If the weights don't sum to 1, the implementation divides by their positive total. Disabled
stages contribute weight 0 and are excluded from the normalization.

Worked example — the highest-risk pair in the bundled corpus (`PD-3-SUB-ALICE-REF-TREES`):

```
Exact 88.17% · Shingle 84.13% · Fuzzy 89.96% · Graph 63.11%
0.35(0.8817) + 0.30(0.8413) + 0.25(0.8996) + 0.10(0.6311) = 0.8490 → 84.90% CRITICAL
```

Risk labels (display only, independent of the reviewer threshold):

| Score | Label |
|---:|---|
| ≥ 75% | `CRITICAL` |
| 55–75% | `HIGH` |
| 35–55% | `MEDIUM` |
| < 35% | `LOW` |

---

## 7. Algorithm & data-structure inventory

This is the point of the project — treat these as load-bearing, not incidental.

**Structures (17)** — `DynamicArray`, `SinglyLinkedList`, `LinkedStack`, `LinkedQueue`,
`HashTable`, `HashSet`, `BinarySearchTree`, `AVLTree`, `BTree`, `BPlusTree`, `BinaryHeap`,
`MinHeap`, `MaxHeap`, `SegmentTree`, `FenwickTree`, `DisjointSet`, `Ordering`.

**Text** — `TextNormalizer`, `Trie`, `KMP`, `RabinKarp`, `ZAlgorithm`, `AhoCorasick`,
`ShingleGenerator`, `Shingler`, `MinHash`, `LCS`, `LongestCommonSubsequence`, `EditDistance`,
`SmithWaterman`, `FuzzyAlignment`.

**Graph / flow / greedy / compression / sort** — `WeightedGraph`, `DirectedGraphAlgorithms`,
`MinimumSpanningTree`, `ShortestPaths`, `EdmondsKarpMaxFlow`, `GreedyEvidenceSelector`,
`SetCoverSolver`, `HuffmanCodec`, `GenericSorts` (merge/quick), `IntegerSorts` (counting/radix),
`AlgorithmBenchmark`.

`ALGORITHM_MAPPING.md` maps each one to where it is actually used and its complexity.

---

## 8. Data formats

**Document (`.txt`)** — optional metadata header, blank line, then body:

```
ID: SUB-ALICE
TITLE: Tree Structures in Similarity Analysis
AUTHOR: Alice Student

An AVL tree maintains logarithmic height by applying rotations ...
```

Validation rules: must be `.txt`; non-empty; ≤ `maxFileBytes`; valid UTF-8; body must be
non-blank **and contain at least one letter or digit**; `ID` may contain only letters, digits,
`-`, `_`. Missing metadata falls back to the filename. Header line count is retained as
`sourceLineOffset` so reported line numbers match the original file.

**Reviewers (`data/reviewers/reviewers.txt`)** — `# id,name,capacity`

```
R1,Dr. Ananya Rao,2
R2,Prof. Vikram Sen,2
R3,Dr. Meera Iyer,1
```

**Settings (`config/settings.txt`)** — `key=value`, `#` comments. Keys: `enableExact`,
`enableShingle`, `enableFuzzy`, `enableGraph`, `wordShingleSize`, `characterShingleSize`,
`minExactPhraseCharacters`, `candidateThreshold`, `reviewThreshold`, `graphEdgeThreshold`,
`exactWeight`, `shingleWeight`, `fuzzyWeight`, `graphWeight`, `maxEvidence`, `workerCount`,
`maxFileBytes`, `removeStopwords`, `submissionDirectory`, `referenceDirectory`,
`reportDirectory`, `stopwordFile`. All validated by `Settings.validate()`.

---

## 9. Build, run, test

Run from the project root so relative paths resolve.

```powershell
.\build.ps1          # compile main sources into out\
.\build.ps1 -Tests   # compile main + test sources
.\run.ps1            # desktop UI
.\run.ps1 --console  # 13-option console menu
.\run.ps1 --demo     # deterministic end-to-end demo
.\test.ps1           # compile everything and run all self-tests
```

`run.ps1` and `test.ps1` both invoke `build.ps1` first.

---

## 10. Testing

Hand-written assertion harness (no JUnit — it would be an external JAR). `AllTests` is the entry
point and aggregates six suites. Current state, all passing:

```
Structure assertions:            14855
Text assertions:                   799
Advanced assertions:                89
Service/controller assertions:      19
Desktop UI assertions:               8
Integration assertions:             76
ALL_TESTS_PASSED: 15846 assertions in ~1.2 s
```

**Note the skew.** Structures are exhaustively tested (invariant checks after randomized
operation sequences); the service, controller, and UI layers are barely covered. New defects are
far more likely to be found in `service`/`controller`/`io`/`ui` than in `structures`.

---

## 11. Current state — recent fixes

Two defects were found in an audit and fixed. Both are in the working tree.

**a. `ApplicationService.reportFiles()` — crash under concurrent report writes.**
It walked the reports directory twice: `countReports()` to size an exact array, then
`fillReports()` to populate it via `entries[cursor[0]++]` with no bound check. Report *exports
write into that same directory*. A file appearing between the two walks overran the array
(`ArrayIndexOutOfBoundsException`); one disappearing left trailing `null`s that the sort
comparator on the very next line dereferenced. Replaced with a single-pass growable
`ReportCollector`.

**b. `DocumentAnalyzer` — quadratic shingle stage.**
Every piece of evidence re-scanned the entire normalized text to locate its own character span,
and each shingle hit did a linear scan of the reference shingle array to recover its index.
Measured on identical documents (the near-duplicate case the tool exists to detect):

| tokens | shingle stage before | after |
|---:|---:|---:|
| 2000 | 177 ms | 28 ms |
| 4000 | 714 ms | 38 ms |
| 8000 | 2668 ms | 57 ms |

Fixed by resolving token offsets once per document (new private `TokenOffsets` helper) and
precomputing a first-occurrence `HashTable`. Now linear — **47× faster at 8k tokens**.
Verified output-identical: a baseline of scores to 9 decimals plus every evidence passage's
spans, algorithm, and excerpt across 80 cases in both stopword modes is **byte-identical**
before and after, and the demo still reports the same 84.90% / 88.17% / 84.13% / 89.96%.

---

## 12. Known open issues

1. **The exact-match stage is still quadratic** (~4.9 s at 8k tokens — now the dominant cost).
   It searches each phrase independently with a fresh linear scan, rotating KMP/Rabin-Karp/Z.
   That rotation is the deliberate pedagogical point, so it was left alone. `DocumentAnalyzer`
   already has `MAX_FUZZY_TOKENS`, `MAX_CHARACTER_WINDOW`, and `MAX_AHO_PATTERNS` bounds while
   this loop has none; adding a matching `MAX_EXACT_PHRASES` cap would fit the existing pattern
   but **would change the `matched/uniqueCount` score**, so it is a product decision, not a
   mechanical fix.
2. **Generated artifacts are under version control.** `data/index/document-index.tsv` and
   `benchmarks/algorithm-benchmarks.txt` are rewritten on every run and are committed containing
   machine-specific absolute paths. They show as modified after any run. Consider `.gitignore`.
3. **Stale absolute path in `README.md`** — it instructs `Set-Location C:\Users\John\Documents\dsa3\...`,
   which is not where the repo currently lives.

---

## 13. Discrepancies to be aware of

`config/settings.txt` and the field initializers in `Settings.java` **do not match**. The file
wins at runtime; the code defaults apply only when the file is missing or a key is absent.

| Key | `settings.txt` | `Settings.java` |
|---|---:|---:|
| `candidateThreshold` | 0.12 | 0.18 |
| `reviewThreshold` | 0.30 | 0.32 |
| `graphEdgeThreshold` | 0.28 | 0.30 |

All other keys agree. If you reason about behavior, use the `settings.txt` values.

---

## 14. UI redesign context (active work)

The owner is planning a UI redesign. Relevant findings:

- **No `setLookAndFeel` call exists anywhere in the project.** `MainApplication.launch` calls
  `Theme.install()`, which only sets `UIManager` font/color keys — so the app runs on **Metal**,
  the default cross-platform look-and-feel. Metal's beveled buttons, gradient headers, and chunky
  scrollbars are the main reason it reads as dated. Note that `UIManager.setLookAndFeel` *resets*
  UIManager defaults, so it must be called **before** `Theme.install()`.
- `Theme.java` (116 lines) is already a design-token file: a color palette, a small font ramp,
  `cardBorder()`, and `primaryButton`/`secondaryButton`/`dangerButton` factories. It is the right
  place to expand into spacing/radius/elevation tokens.
- `Theme.cardBorder()` is a hard 1px `createLineBorder` rectangle — no radius, no elevation.
- Only three panels call `setRowHeight`, and there are **no custom table cell renderers**, though
  tables are most of the UI surface.
- `RenderingHints` (antialiasing) is used **only** in `GraphCanvas`.
- `GraphCanvas` is 750 lines of custom Java2D painting — the most expensive thing to port, and it
  already works. This is a strong argument for staying in Swing.
- Because of constraint §2.1, JavaFX and FlatLaf are **off the table** unless the owner
  explicitly relaxes the zero-dependency rule. Nimbus is JDK-bundled and therefore allowed.

Recommended direction: a light, high-density "analyst console" aesthetic (reviewers read
long-form passages side by side in `PassageComparisonPanel`; the evidence highlight colors
`EXACT`/`MODIFIED`/`FUZZY` are already tuned for a light ground; and the tool needs to look sober
and credible because screenshots go into a report).

---

## 15. Honest limitations of the tool itself

Documented by the project and worth preserving in any user-facing copy: it detects *textual*
similarity only. It cannot detect paraphrase-by-meaning, translation, or commissioned writing;
it has no internet corpus (comparison is only against supplied references); it cannot distinguish
legitimate quotation, standard terminology, shared assignment wording, or permitted collaboration
from misconduct. Fuzzy analysis is bounded to the first 900 tokens per document, so very long
documents are compared on a prefix.
