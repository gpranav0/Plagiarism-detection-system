# Advanced Plagiarism Detection and Academic Integrity Analysis System

This Java 17 desktop application compares student submissions with reference documents and produces explainable evidence for academic-integrity review. Its primary interface is a polished Java Swing workspace; a legacy console and deterministic demo remain available for automation. The system combines exact phrase matching, word and character shingles, fuzzy passage alignment, deterministic MinHash candidate selection, graph analysis, risk ranking, capacity-aware reviewer assignment, reporting, and algorithm benchmarks.

The application is a decision-support tool, not an automated misconduct judge. A high score means that a case deserves human attention. It does not establish authorship, intent, inadequate citation, or misconduct. Reviewers should inspect the reported passages in context, consider legitimate quotation, standard terminology, shared assignment wording, collaboration rules, and citations, and give the student an appropriate opportunity to respond.

## Requirements

- Windows PowerShell
- JDK 17 or newer with both `java` and `javac` on `PATH`
- No Maven, Gradle, external JARs, database, network service, or other dependency

Confirm the installed toolchain:

```powershell
java -version
javac -version
```

The source is compiled with `javac --release 17`.

## Build, run, test, and demo

Run commands from the project root so the default data, configuration, reports, logs, and benchmark paths resolve to this project:

```powershell
Set-Location C:\Users\John\Documents\dsa3\AdvancedPlagiarismDetectionSystem
```

Build the main application into `out`:

```powershell
.\build.ps1
```

Start the graphical desktop interface:

```powershell
.\run.ps1
```

Start the legacy 13-option console:

```powershell
.\run.ps1 --console
```

Compile and run every self-test:

```powershell
.\test.ps1
```

Run the deterministic end-to-end sample workflow:

```powershell
.\run.ps1 --demo
```

The demo imports the standard sample corpus, validates it, runs bounded batch analysis, assigns reviewable cases, exports readable and Huffman-compressed reports, runs benchmarks, checks indexes, and ends with `DEMO_SUCCESS` when successful.

Show command-line help:

```powershell
.\run.ps1 --help
```

When launching the script from another working directory, explicitly select the data root:

```powershell
.\run.ps1 --root C:\Users\John\Documents\dsa3\AdvancedPlagiarismDetectionSystem
```

`run.ps1` rebuilds the main sources before launch. `test.ps1` rebuilds the main and test sources before running `edu.academic.integrity.tests.AllTests`. The build has no network step.

### Troubleshooting startup errors

- Run the scripts from the project root and confirm both `java -version` and `javac -version` succeed.
- The scripts cap Java at a 256 MB heap and serialize build/test/run operations. If another project command is active, a second command displays a waiting message instead of deleting its `out` directory.
- If PowerShell reports that script execution is disabled, enable scripts only for the current terminal with `Set-ExecutionPolicy -Scope Process Bypass`, then rerun the command.
- A failed Java process now makes the PowerShell script fail explicitly. Application-level recoverable errors are also recorded in `logs/errors.log`.

## Desktop interface

The default Swing interface provides dashboard metrics, submission/reference management, background sequential or parallel analysis, explainable side-by-side passage highlighting, custom-index risk ranking, an interactive similarity graph, maximum-flow reviewer routing, report/log/Huffman workflows, editable settings, and measured benchmarks. All domain operations pass through the `controller` and `service` layers; Swing and AWT imports are confined to `ui` and enforced by `build.ps1`.

## Legacy interactive menu

Run `.\run.ps1 --console` to use the console workflows:

1. **Import student submissions** — imports UTF-8 `.txt` files from `data/submissions` or a directory entered at the prompt. Bad files are skipped without terminating the remaining import.
2. **Import reference documents** — imports UTF-8 `.txt` files from `data/references` or a chosen directory.
3. **Validate files and metadata** — validates every source directory registered by imports in the current session (or the standard directories before any import), reports duplicate IDs and invalid files, and displays index invariants.
4. **Run plagiarism analysis on one document** — selects a loaded submission ID and compares it with every loaded reference.
5. **Run batch analysis** — uses deterministic MinHash shortlisting, a bounded fixed worker pool, and pair-ordinal result merging.
6. **Display matched passages and source locations** — shows component scores, the matching algorithm, excerpts, and line/column locations in both documents.
7. **Rank suspicious submissions** — displays one row per submission, ranked by its strongest reference match, plus strongest-submission-score range statistics and the count meeting the review threshold.
8. **Display similarity groups and copying paths** — shows union-find graph groups and compact Kruskal links, then prints BFS/DFS reachability and a custom-min-heap Dijkstra relationship path from an entered document ID.
9. **Assign cases to reviewers** — applies the review threshold and uses maximum flow to respect reviewer capacities.
10. **Export reports** — writes readable case reports, self-contained Huffman files, and a batch summary into a new isolated run directory.
11. **Display algorithm benchmarks** — benchmarks five manual sorting algorithms, tree indexes, MinHash candidate reduction, and sequential versus parallel consistency; it also saves the output.
12. **View activity and error logs** — displays the most recent 50 lines of each log.
13. **Exit safely** — leaves the loop and records a safe shutdown.

Import submissions and references before running options 4 or 5. Analysis-dependent options 6–10 require a completed analysis. Reviewer assignment also requires at least one valid reviewer in `data/reviewers/reviewers.txt`.

## Project directories

The application creates required working directories at startup. Supplied sample files can be replaced or extended.

```text
AdvancedPlagiarismDetectionSystem/
├── build.ps1                     Compiles main sources, optionally tests
├── run.ps1                       Builds and starts the desktop UI, console, or demo
├── test.ps1                      Builds and runs all self-tests
├── README.md
├── ALGORITHM_MAPPING.md          Observable feature-to-algorithm mapping
├── TEAM_ROLES.md                 Five-member responsibility split
├── config/
│   └── settings.txt              key=value runtime configuration
├── data/
│   ├── submissions/              Student UTF-8 .txt files
│   ├── references/               Reference UTF-8 .txt files
│   ├── reviewers/
│   │   └── reviewers.txt         reviewer-id,name,capacity
│   ├── index/
│   │   └── document-index.tsv    Versioned persistent B-tree/B+ document catalog
│   ├── stopwords.txt             Optional, one stopword per line
│   └── quarantine/               Recoverable copies of rejected imports
├── reports/                      Isolated run folders with case reports and summaries
├── logs/                         activity.log and errors.log
├── benchmarks/                   algorithm-benchmarks.txt
├── src/
│   ├── main/java/edu/academic/integrity/
│   └── test/java/edu/academic/integrity/tests/
└── out/                          Generated .class files; safe to rebuild
```

Rejected inputs are not deleted or moved. When possible, the importer places a copy named `<original-name>.invalid-copy` in `data/quarantine` and continues with the other files. If that name already exists, a numeric suffix preserves every earlier recovery copy.

Every successful document import attempts to update `data/index/document-index.tsv`. `PersistentDocumentIndex` loads this versioned UTF-8 catalog at startup into custom B-tree and B+ tree indexes, writes through a temporary file with atomic replacement where supported, recovers a valid interrupted `.tmp`, and isolates a malformed snapshot before starting a new one. If persistence fails, the catalog mutation is rolled back and logged while the already validated document remains available in memory. The catalog persists document IDs, types, and source paths; option 3's invariant summary also checks that cataloged sources remain readable. Document contents are still validated and explicitly imported before analysis.

## Document input format

Submission and reference inputs must be readable, non-empty, valid UTF-8 `.txt` files within `maxFileBytes`. An optional metadata block may appear at the very beginning:

```text
ID: SUBMISSION_001
TITLE: Dynamic Programming Assignment
AUTHOR: Student Name

The document body starts here.
It may span multiple lines.
```

The `ID:`, `TITLE:`, and `AUTHOR:` labels are case-insensitive. The blank line after the metadata block is recommended. Metadata fields are optional:

- With no metadata block, the filename without `.txt` becomes both the document ID and default title, and the whole file is the body.
- An ID must be nonblank and contain only letters, digits, hyphens, and underscores.
- A blank or omitted title falls back to the document ID or filename stem.
- The body must contain non-whitespace text and at least one searchable letter or digit.
- Duplicate IDs are rejected. Standard validation detects duplicates across both submissions and references.
- Malformed UTF-8, inaccessible files, unsupported extensions, oversized files, and empty files are reported and isolated without ending a directory import.

Use globally distinct IDs so reports and graph nodes are unambiguous.

### Reviewers

`data/reviewers/reviewers.txt` uses one unquoted CSV row per reviewer:

```text
# id,name,capacity
REV-01,Dr. Asha Rao,3
REV-02,Prof. John Lee,2
```

Each row must contain exactly `id,name,capacity`. The ID must be nonblank and unique, and capacity must be a non-negative integer. Commas inside fields are not supported. Invalid or duplicate rows are logged and skipped. Current assignment treats every valid reviewer as eligible for every reviewable case and uses Edmonds–Karp maximum flow to honor capacities. The console and reports identify reviewable cases left waiting when total capacity is insufficient.

### Stopwords

`data/stopwords.txt` contains one word per line. Empty lines and lines beginning with `#` are ignored. If the file is absent, the engine runs with no stopword list. Stopword removal is controlled by `removeStopwords`.

## Settings

`config/settings.txt` is a case-sensitive `key=value` file. Empty lines and `#` comments are allowed. These are the supported keys, safe built-in defaults, and values in the bundled sample configuration:

| Key | Built-in | Bundled | Meaning and validation |
|---|---:|---:|---|
| `enableExact` | `true` | `true` | Enables KMP/Rabin–Karp/Z/Aho–Corasick exact evidence |
| `enableShingle` | `true` | `true` | Enables word/character-shingle similarity |
| `enableFuzzy` | `true` | `true` | Enables Smith–Waterman/LCS/edit-distance alignment |
| `enableGraph` | `true` | `true` | Enables similarity-network enrichment |
| `wordShingleSize` | `4` | `4` | Consecutive tokens per word shingle; at least 1 |
| `characterShingleSize` | `9` | `9` | Characters per character shingle; at least 2 |
| `minExactPhraseCharacters` | `28` | `28` | Minimum exact phrase length; at least 4 |
| `candidateThreshold` | `0.18` | `0.12` | MinHash estimate required for a normal batch candidate; 0–1 |
| `reviewThreshold` | `0.32` | `0.30` | Composite score required for reviewer assignment; 0–1 |
| `graphEdgeThreshold` | `0.30` | `0.28` | Composite score required for a similarity edge; 0–1 |
| `exactWeight` | `0.35` | `0.35` | Exact-match contribution; non-negative |
| `shingleWeight` | `0.30` | `0.30` | Word/character-shingle contribution; non-negative |
| `fuzzyWeight` | `0.25` | `0.25` | Smith–Waterman/LCS/edit contribution; non-negative |
| `graphWeight` | `0.10` | `0.10` | Similarity-network contribution; non-negative |
| `maxEvidence` | `8` | `8` | Maximum compact evidence passages per case; positive |
| `workerCount` | `4` | `4` | Maximum batch comparison workers; positive |
| `maxFileBytes` | `2000000` | `2000000` | Maximum input file size in bytes; positive |
| `removeStopwords` | `true` | `true` | Must be `true` or `false` |
| `submissionDirectory` | `data/submissions` | same | Submission input directory, absolute or relative to project root |
| `referenceDirectory` | `data/references` | same | Reference input directory, absolute or relative to project root |
| `reportDirectory` | `reports` | same | Report output directory, absolute or relative to project root |
| `stopwordFile` | `data/stopwords.txt` | same | Stop-word file, absolute or relative to project root |

Score weights do not have to total 1; the score calculation divides by their positive total. Thresholds use ratios, so `0.32` means 32%. Unknown keys, malformed lines, and invalid values invalidate the settings file; initialization records the error and falls back to all safe defaults.

Example:

```text
# Candidate selection and review policy
enableExact=true
enableShingle=true
enableFuzzy=true
enableGraph=true
wordShingleSize=4
characterShingleSize=9
candidateThreshold=0.12
reviewThreshold=0.30
graphEdgeThreshold=0.28
workerCount=4
removeStopwords=true
submissionDirectory=data/submissions
referenceDirectory=data/references
reportDirectory=reports
stopwordFile=data/stopwords.txt
```

## Analysis pipeline

```text
UTF-8 files
    → validation and metadata parsing
    → normalization, tokenization, stopword filtering
    → deterministic MinHash candidate shortlisting
    → bounded exact, shingle, and fuzzy comparison
    → deterministic result merge and explainable composite score
    → risk indexes, range analytics, and similarity graph
    → reviewer assignment and report export
```

The visible score preserves four components:

- **Exact phrase coverage:** KMP, hash-verified Rabin–Karp, Z-algorithm, and Aho–Corasick evidence.
- **Shingle similarity:** 65% word-shingle Jaccard and 35% character-shingle Jaccard inside that component.
- **Fuzzy alignment:** 50% Smith–Waterman local alignment, 30% LCS similarity, and 20% edit-distance similarity inside that component.
- **Graph signal:** direct similarity and local network density after graph construction.

The configured weights combine these components into the final score. Fixed display labels are `CRITICAL` at 75% or higher, `HIGH` at 55% or higher, `MEDIUM` at 35% or higher, and `LOW` below 35%. These labels prioritize review; they are not findings of misconduct.

Batch mode creates a 128-value deterministic MinHash signature for each document’s word shingles and considers each submission–reference pair in stable row-major order. A pair is verified when its estimated similarity reaches `candidateThreshold`. Documents too short to form a word shingle are always compared. Accepted comparisons run through at most `workerCount` workers, and results are merged by original pair ordinal rather than completion time. Single-document mode compares the selected submission against every loaded reference without candidate filtering.

## Package architecture

| Package | Responsibility |
|---|---|
| `app` | Java entry point, legacy console, deterministic demo, and application lifecycle |
| `ui` | Swing windows, reusable controls, dialogs, evidence highlighting, and custom graph rendering |
| `controller` | UI-neutral background task coordination, cancellation, progress, and friendly failure translation |
| `service` | Headless workflow orchestration and immutable array-backed screen projections |
| `config` | Validated settings and safe-default loading |
| `io` | Project paths, strict file parsing, batch import, validation, quarantine, stopwords, reviewers, and logs |
| `model` | Documents, evidence, score breakdowns, results, graph views, assignments, and summaries |
| `core` | Text preparation, one-pair analysis, MinHash planning, and bounded deterministic batch execution |
| `algorithms.text` | Normalization, shingles, KMP, Rabin–Karp, Z, trie, Aho–Corasick, LCS, edit distance, Smith–Waterman, and MinHash |
| `algorithms.sort` | Manual merge, quick, heap, counting, and radix sorts |
| `algorithms.graph` | Custom weighted graph, traversal, components, shortest paths, MST, and directed graph operations |
| `algorithms.flow` | Edmonds–Karp maximum flow and capacity assignment |
| `algorithms.greedy` | Compact evidence/set-cover selection |
| `algorithms.compression` | Deterministic, self-contained Huffman codec |
| `algorithms.benchmark` / `benchmark` | Timing records and user-visible benchmark service |
| `structures` | Custom arrays, lists, stack, queue, hash table/set, heaps, BST, AVL, B-tree, B+ tree, segment tree, Fenwick tree, and disjoint set |
| `index` | In-memory and atomically persisted B-tree/B+ document catalogs, BST/AVL/heap result ranking, and range analytics |
| `analytics` | Similarity groups, compact relationships, graph enrichment, and relationship paths |
| `review` | Threshold filtering, deterministic ranking, and capacity-aware reviewer assignment |
| `report` | Atomic readable report export, summary generation, and `.huff` output |
| `exception` | Project, validation, duplicate-document, and import failures |

Algorithmic core classes use primitives, arrays, custom nodes, custom data structures, and manual algorithms rather than Java collection implementations, built-in sorting, streams, or external libraries. See `ALGORITHM_MAPPING.md` for feature mappings and complexities and `TEAM_ROLES.md` for the five-member development split.

## Reports, compression, benchmarks, and logs

Option 10 creates a fresh `reports/run-<epoch-milliseconds>/` directory on every export so files from different analyses are never mixed. It writes:

The repository may also contain root-level reports produced as bundled example output; new application exports always use an isolated run directory.

- `<case-id>.txt` — human-readable report with document metadata, risk label, composite and component scores, runtime, evidence type and algorithm, similarity, line/column ranges, excerpts, reviewer assignment, and the human-review policy.
- `<case-id>.huff` — Huffman-compressed UTF-8 bytes for the same report.
- `analysis-summary.txt` — one line per case with the document pair, score, risk, and reviewer status.

Report writes use a temporary file followed by replace; an atomic move is used when the filesystem supports it. Case IDs are sanitized for filenames. The Huffman format contains its magic/version, original length, and frequency table, so `HuffmanCodec` can decode it without the original tree. A `.huff` file is compression, not encryption or access control, and its header can make a short report larger than the readable original.

The benchmark screen (or console option 11) writes `benchmarks/algorithm-benchmarks.txt`. It covers merge, quick, heap, counting, and radix sort on identical deterministic inputs; ordered BST versus AVL insertion; B-tree and B+ tree invariants; MinHash candidate reduction; sequential and bounded-parallel timings; KMP exact matching versus Smith-Waterman fuzzy alignment; deterministic result equality; and an approximate JVM memory delta. Timings depend on JVM warm-up, hardware, background activity, corpus size, and candidate count and should not be treated as laboratory-grade performance measurements.

`logs/activity.log` records lifecycle and successful operations. `logs/errors.log` records rejected inputs and recoverable failures. The console displays the most recent 50 lines. Logs, reports, and the persistent index can contain document IDs, paths, excerpts, reviewer names, and error details; protect the project directory according to institutional privacy policy.

## Suggested desktop workflow

1. Put authorized student files in `data/submissions` and reference material in `data/references`.
2. Review `config/settings.txt`, `data/stopwords.txt`, and reviewer capacities.
3. Start with `.\run.ps1`.
4. Use **Documents** to import, validate, search, and preview both corpora.
5. Configure a submission, reference scope, thresholds, algorithms, and execution mode under **Run analysis**.
6. Inspect **Evidence & results**, **Risk ranking**, and **Similarity graph**. Read source passages and citations, not only the percentage.
7. Use **Reviewer routing** to compute or manually adjust capacity-safe assignments.
8. Preview/export the review packet and inspect errors under **Reports & logs**.
9. Run actual machine/corpus benchmarks under **Settings & benchmarks**, then exit using the window close control for safe shutdown.

The imported document contents, result indexes, assignments, and graph state are in memory for the current process. After restarting, import the corpus again before analysis. The custom B-tree/B+ document catalog, files, logs, reports, and benchmarks remain on disk.
If a successful import changes the corpus after an analysis, the application clears the old results, graph, range analytics, and reviewer assignments so stale evidence cannot be displayed or exported.

## Standard-library import rationale

The project has no third-party dependency and does not use Java collection implementations or library sorting in the algorithmic core. The remaining standard-library imports are boundary facilities that Java cannot reasonably replace with an academic data-structure implementation:

| Standard-library area | Why it is used |
|---|---|
| `javax.swing` / `java.awt` | Desktop widgets, accessibility, layout, styled passage highlighting, and custom graph painting; build-enforced inside `ui` only |
| `java.io` | Console input; `File` path access; buffered submission, configuration, reviewer, stopword, persistent-index, report, benchmark, quarantine, and log I/O; checked I/O failures |
| `java.nio.charset` | Explicit UTF-8 persistent-index/report/compression encoding and decoders configured to reject malformed or unmappable input rather than silently replace it |
| `java.nio.file` | Temporary-write-then-replace persistent-index/report recovery, atomic moves when supported, reading saved benchmark text, and isolated temporary workspaces in integration tests |
| `java.time` | Human-readable timestamps in activity and error logs |
| `java.util.concurrent` | The bounded batch worker pool and UI-neutral controller worker, ordinal-indexed futures, cooperative interruption, and worker-failure propagation |

Essential `java.lang` facilities such as `String`, `StringBuilder`, primitive arrays, `Math`, `System`, `Runtime`, and `Thread` are available automatically. No `ArrayList`, `LinkedList`, `HashMap`, `HashSet`, `TreeMap`, `TreeSet`, built-in stack/queue/heap, `Collections.sort`, `Arrays.sort`, or streams are used to implement required algorithms.

## Testing

`test.ps1` compiles the application and custom test runners without a third-party framework. The suite checks custom structure invariants and edge cases, exact and randomized text matching, LCS/edit/fuzzy behavior, MinHash determinism, sorting, compression round trips and corruption handling, graph algorithms, flow and greedy algorithms, persistent-index save/reload, validation, and sequential/parallel consistency.

Always run:

```powershell
.\test.ps1
.\run.ps1 --demo
```

after changing algorithms, settings behavior, parsing, concurrency, or reports.

## Honest limitations

- Scores are similarity indicators, not proof of copying or intent. Common phrases, required templates, citations, and legitimate collaboration can produce strong matches.
- The engine is lexical rather than semantic. Translation, synonym-heavy paraphrase, reordered ideas, code-specific renaming, images, formulas, and concept copying can evade it.
- Only strict UTF-8 plain-text `.txt` inputs are imported. PDF, DOCX, HTML, source-code syntax, OCR, and citation-format parsing are outside the current scope.
- Character coordinates and character shingles use Java UTF-16 positions. Normalization is language-agnostic but does not perform stemming, lemmatization, synonym expansion, or language detection.
- MinHash is a deterministic probabilistic estimate. Candidate filtering can omit a pair below the configured estimate even if it contains a locally interesting passage; short documents with no word shingles are force-compared to reduce that risk.
- Fuzzy analysis is intentionally bounded to the first 900 tokens per document, character-shingle analysis to the first 100,000 normalized characters, and Aho–Corasick evidence to 512 phrases. Very long documents can therefore have later evidence omitted. LCS reconstruction outside the bounded engine still requires quadratic memory.
- Evidence compaction can retain only `maxEvidence` passages. Inspect original files when a report suggests broader overlap.
- Graph links are undirected similarity relationships. A displayed “copying path” does not establish direction, chronology, or who copied from whom. Topological sort and strongly connected components are implemented and tested for future directed provenance data, but the current corpus has no meaningful chronology or causal edge direction.
- Reviewer assignment currently models only case threshold and reviewer capacity. It does not model expertise, conflicts of interest, availability dates, or case-specific eligibility.
- The B-tree/B+ catalog persists IDs, document types, and source paths, but not document bodies or prepared tokens. Corpus contents, result indexes, assignments, and graph state are intentionally rebuilt after restart so changed source files are revalidated.
- Persistent catalog paths are absolute. Moving or deleting a source can leave a stale catalog record until that document ID is successfully imported from its new location; option 3 marks the persistent index invalid while such a path is inaccessible. The catalog is an index, not a backup of document content.
- Parallel execution improves throughput only when enough candidates and CPU resources exist. It is bounded and deterministically merged, but runtime and per-case timing naturally vary.
- Reports, logs, the persistent index, source documents, and `.huff` files are local unencrypted files. The application does not implement authentication, authorization, secure deletion, or retention policy.
- The current console has no GUI, web service, database, distributed worker, or live learning model.

Use this system on authorized material, minimize retained personal data, restrict access to outputs, and keep final academic decisions with accountable human reviewers.
