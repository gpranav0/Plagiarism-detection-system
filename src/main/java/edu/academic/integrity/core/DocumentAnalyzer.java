package edu.academic.integrity.core;

import edu.academic.integrity.algorithms.greedy.GreedyEvidenceSelector;
import edu.academic.integrity.algorithms.text.AhoCorasick;
import edu.academic.integrity.algorithms.text.EditDistance;
import edu.academic.integrity.algorithms.text.FuzzyAlignment;
import edu.academic.integrity.algorithms.text.KMP;
import edu.academic.integrity.algorithms.text.LCS;
import edu.academic.integrity.algorithms.text.RabinKarp;
import edu.academic.integrity.algorithms.text.ShingleGenerator;
import edu.academic.integrity.algorithms.text.TextNormalizer;
import edu.academic.integrity.algorithms.text.ZAlgorithm;
import edu.academic.integrity.config.Settings;
import edu.academic.integrity.model.AnalysisResult;
import edu.academic.integrity.model.Document;
import edu.academic.integrity.model.MatchType;
import edu.academic.integrity.model.PassageMatch;
import edu.academic.integrity.model.ScoreBreakdown;
import edu.academic.integrity.structures.DynamicArray;
import edu.academic.integrity.structures.HashSet;
import edu.academic.integrity.structures.HashTable;
import edu.academic.integrity.structures.SinglyLinkedList;

/** Runs the explainable exact, shingle, and fuzzy comparison for one pair. */
public final class DocumentAnalyzer {
    private static final int MAX_FUZZY_TOKENS = 900;
    private static final int MAX_CHARACTER_WINDOW = 100_000;
    private static final int MAX_AHO_PATTERNS = 512;

    private final Settings settings;
    private final String[] stopwords;

    public DocumentAnalyzer(Settings settings, String[] stopwords) {
        this.settings = settings;
        this.stopwords = stopwords == null ? new String[0] : copy(stopwords);
    }

    public void prepare(Document document) {
        checkInterrupted();
        if (document.normalizedText().isEmpty() && !document.content().isEmpty()) {
            TextPreparation.prepare(document, stopwords, settings.removeStopwords);
        }
        checkInterrupted();
    }

    public AnalysisResult analyze(String caseId, Document submission, Document reference) {
        return analyze(caseId, submission, reference, 0.0);
    }

    public AnalysisResult analyze(String caseId, Document submission, Document reference,
                                  double graphSignal) {
        long started = System.nanoTime();
        settings.validate();
        checkInterrupted();
        prepare(submission);
        prepare(reference);

        String submissionText = submission.normalizedText();
        String referenceText = reference.normalizedText();
        // Resolve every token's character span once per document. Each evidence item used
        // to re-scan the whole normalized text to locate its own span, which made the
        // shingle stage quadratic in document length on exactly the near-duplicate pairs
        // this tool exists to find. The mapping is identical; it is just computed once.
        TokenOffsets submissionOffsets = TokenOffsets.of(submission);
        TokenOffsets referenceOffsets = TokenOffsets.of(reference);
        EvidenceAccumulator evidence = new EvidenceAccumulator();

        double exactScore = 0.0;
        if (settings.enableExact) {
            String[] completeSubmissionTokens = TextNormalizer.tokenizeNormalized(
                    submissionText, new String[0]);
            ExactResult exact = exactEvidence(submission, reference,
                    completeSubmissionTokens, submissionText, referenceText);
            exactScore = exact.score;
            evidence = exact.evidence;
        }
        checkInterrupted();

        double shingleScore = 0.0;
        if (settings.enableShingle) {
            String[] submissionShingles = ShingleGenerator.wordShingles(
                    submission.tokens(), settings.wordShingleSize);
            String[] referenceShingles = ShingleGenerator.wordShingles(
                    reference.tokens(), settings.wordShingleSize);
            double wordJaccard = jaccard(submissionShingles, referenceShingles);

            String submissionCharacterWindow = bounded(submissionText, MAX_CHARACTER_WINDOW);
            String referenceCharacterWindow = bounded(referenceText, MAX_CHARACTER_WINDOW);
            String[] submissionCharacterShingles = ShingleGenerator.characterShingles(
                    submissionCharacterWindow, Math.min(settings.characterShingleSize,
                            Math.max(1, submissionCharacterWindow.length())));
            String[] referenceCharacterShingles = ShingleGenerator.characterShingles(
                    referenceCharacterWindow, Math.min(settings.characterShingleSize,
                            Math.max(1, referenceCharacterWindow.length())));
            double characterJaccard = jaccard(submissionCharacterShingles,
                    referenceCharacterShingles);
            shingleScore = 0.65 * wordJaccard + 0.35 * characterJaccard;
            addShingleEvidence(evidence, submission, reference, submissionOffsets,
                    referenceOffsets, submissionShingles, referenceShingles, shingleScore);
        }
        checkInterrupted();

        double fuzzyScore = 0.0;
        if (settings.enableFuzzy) {
            String[] submissionWindow = boundedTokens(submission.tokens(), MAX_FUZZY_TOKENS);
            String[] referenceWindow = boundedTokens(reference.tokens(), MAX_FUZZY_TOKENS);
            FuzzyAlignment.Result alignment = FuzzyAlignment.align(submissionWindow,
                    referenceWindow);
            checkInterrupted();
            double alignmentScore = alignment.normalizedScore();
            int maximumTokens = Math.max(submissionWindow.length, referenceWindow.length);
            double lcsScore = maximumTokens == 0 ? 1.0
                    : (double) LCS.length(submissionWindow, referenceWindow) / maximumTokens;
            checkInterrupted();
            double editScore = EditDistance.similarity(submissionWindow, referenceWindow);
            fuzzyScore = 0.50 * alignmentScore + 0.30 * lcsScore + 0.20 * editScore;
            addFuzzyEvidence(evidence, submission, reference, submissionOffsets,
                    referenceOffsets, alignment, fuzzyScore);
        }
        checkInterrupted();

        PassageMatch[] selected = compactEvidence(evidence.toArray(), settings.maxEvidence);
        ScoreBreakdown score = new ScoreBreakdown(exactScore, shingleScore, fuzzyScore,
                settings.enableGraph ? graphSignal : 0.0,
                settings.enableExact ? settings.exactWeight : 0.0,
                settings.enableShingle ? settings.shingleWeight : 0.0,
                settings.enableFuzzy ? settings.fuzzyWeight : 0.0,
                settings.enableGraph ? settings.graphWeight : 0.0);
        return new AnalysisResult(caseId, submission, reference, score, selected,
                System.nanoTime() - started);
    }

    private ExactResult exactEvidence(Document submission, Document reference,
                                      String[] completeSubmissionTokens,
                                      String submissionText, String referenceText) {
        String[] generated = buildExactPhrases(completeSubmissionTokens);
        HashSet<String> unique = new HashSet<>();
        EvidenceAccumulator evidence = new EvidenceAccumulator();
        int matched = 0;
        for (int i = 0; i < generated.length; i++) {
            checkInterrupted();
            String phrase = generated[i];
            if (phrase.length() < settings.minExactPhraseCharacters || !unique.add(phrase)) continue;
            int referencePosition;
            String algorithm;
            if (i % 3 == 0) {
                referencePosition = KMP.indexOf(referenceText, phrase);
                algorithm = "Knuth-Morris-Pratt";
            } else if (i % 3 == 1) {
                referencePosition = RabinKarp.indexOf(referenceText, phrase);
                algorithm = "Rabin-Karp (hash verified)";
            } else {
                referencePosition = ZAlgorithm.indexOf(referenceText, phrase);
                algorithm = "Z-algorithm";
            }
            if (referencePosition < 0) continue;
            matched++;
            int submissionPosition = KMP.indexOf(submissionText, phrase);
            evidence.add(createMatch(MatchType.EXACT, submission, reference,
                    submissionPosition, submissionPosition + phrase.length(),
                    referencePosition, referencePosition + phrase.length(),
                    1.0, algorithm, phrase));
        }

        String[] patterns = firstEligiblePatterns(generated, MAX_AHO_PATTERNS);
        if (patterns.length > 0) {
            AhoCorasick.Match[] matches = new AhoCorasick(patterns).search(referenceText);
            if (matches.length > 0) {
                AhoCorasick.Match first = matches[0];
                String phrase = first.pattern();
                int submissionPosition = KMP.indexOf(submissionText, phrase);
                evidence.add(createMatch(MatchType.MULTI_PATTERN, submission, reference,
                        submissionPosition, submissionPosition + phrase.length(),
                        first.start(), first.endExclusive(), 1.0,
                        "Aho-Corasick multi-pattern search", phrase));
            }
        }
        int uniqueCount = unique.size();
        return new ExactResult(uniqueCount == 0 ? 0.0 : (double) matched / uniqueCount, evidence);
    }

    private String[] buildExactPhrases(String[] tokens) {
        int width = Math.max(5, settings.wordShingleSize);
        if (tokens.length >= width) return ShingleGenerator.wordShingles(tokens, width);
        if (tokens.length == 0) return new String[0];
        return new String[]{TextNormalizer.join(tokens)};
    }

    private String[] firstEligiblePatterns(String[] phrases, int maximum) {
        String[] temporary = new String[Math.min(phrases.length, maximum)];
        int size = 0;
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < phrases.length && size < maximum; i++) {
            if (phrases[i].length() >= settings.minExactPhraseCharacters && seen.add(phrases[i])) {
                temporary[size++] = phrases[i];
            }
        }
        String[] result = new String[size];
        System.arraycopy(temporary, 0, result, 0, size);
        return result;
    }

    private void addShingleEvidence(EvidenceAccumulator evidence, Document submission,
                                    Document reference, TokenOffsets submissionOffsets,
                                    TokenOffsets referenceOffsets, String[] submissionShingles,
                                    String[] referenceShingles, double score) {
        // One pass to record where each reference shingle first occurs. The previous
        // membership set still needed a full linear re-scan of referenceShingles per hit
        // to recover that index; this keeps the same first-occurrence semantics in O(1).
        HashTable<String, Integer> referenceFirstIndex = new HashTable<>();
        for (int i = 0; i < referenceShingles.length; i++) {
            referenceFirstIndex.putIfAbsent(referenceShingles[i], i);
        }
        for (int submissionIndex = 0; submissionIndex < submissionShingles.length;
             submissionIndex++) {
            checkInterrupted();
            String shingle = submissionShingles[submissionIndex];
            Integer firstReferenceIndex = referenceFirstIndex.get(shingle);
            if (firstReferenceIndex == null) continue;
            int referenceIndex = firstReferenceIndex;
            int[] submissionSpan = submissionOffsets.span(
                    submissionIndex, submissionIndex + settings.wordShingleSize);
            int[] referenceSpan = referenceOffsets.span(
                    referenceIndex, referenceIndex + settings.wordShingleSize);
            if (submissionSpan[1] <= submissionSpan[0] || referenceSpan[1] <= referenceSpan[0]) {
                continue;
            }
            evidence.add(createMatch(MatchType.SHINGLE, submission, reference,
                    submissionSpan[0], submissionSpan[1],
                    referenceSpan[0], referenceSpan[1],
                    Math.max(score, 0.01), "word-shingle Jaccard", shingle));
        }
    }

    private void addFuzzyEvidence(EvidenceAccumulator evidence, Document submission,
                                  Document reference, TokenOffsets submissionOffsets,
                                  TokenOffsets referenceOffsets,
                                  FuzzyAlignment.Result alignment, double fuzzyScore) {
        if (alignment.firstPassageTokens().length == 0 || fuzzyScore <= 0.0) return;
        String firstPassage = alignment.firstPassage();
        int[] submissionSpan = submissionOffsets.span(
                alignment.firstStart(), alignment.firstEndExclusive());
        int[] referenceSpan = referenceOffsets.span(
                alignment.secondStart(), alignment.secondEndExclusive());
        if (submissionSpan[1] <= submissionSpan[0] || referenceSpan[1] <= referenceSpan[0]) return;
        evidence.add(createMatch(MatchType.FUZZY, submission, reference,
                submissionSpan[0], submissionSpan[1],
                referenceSpan[0], referenceSpan[1],
                fuzzyScore, "Smith-Waterman + LCS + edit distance", firstPassage));
    }

    private PassageMatch createMatch(MatchType type, Document submission, Document reference,
                                     int normalizedSubmissionStart, int normalizedSubmissionEnd,
                                     int normalizedReferenceStart, int normalizedReferenceEnd,
                                     double similarity, String algorithm, String excerpt) {
        if (normalizedSubmissionStart < 0 || normalizedReferenceStart < 0
                || normalizedSubmissionEnd <= normalizedSubmissionStart
                || normalizedReferenceEnd <= normalizedReferenceStart) {
            return null;
        }
        int submissionStart = submission.originalOffsetForNormalized(Math.max(0, normalizedSubmissionStart));
        int submissionEnd = normalizedSubmissionEnd <= normalizedSubmissionStart
                ? submissionStart : submission.originalOffsetForNormalized(normalizedSubmissionEnd - 1) + 1;
        int referenceStart = reference.originalOffsetForNormalized(Math.max(0, normalizedReferenceStart));
        int referenceEnd = normalizedReferenceEnd <= normalizedReferenceStart
                ? referenceStart : reference.originalOffsetForNormalized(normalizedReferenceEnd - 1) + 1;
        return new PassageMatch(type, submission.id(), reference.id(),
                submissionStart, Math.min(submissionEnd, submission.content().length()),
                referenceStart, Math.min(referenceEnd, reference.content().length()),
                similarity, algorithm, excerpt);
    }

    /**
     * Character spans of a document's tokens within its normalized text.
     *
     * The tokens are the filtered stream (stopwords may have been dropped) while the
     * normalized text still holds every word, so the positions are recovered by one
     * left-to-right scan with a monotonic cursor and whole-word boundaries. That scan
     * used to be repeated from scratch for every piece of evidence, which is what made
     * the shingle stage quadratic; running it once per document yields the same spans.
     */
    private static final class TokenOffsets {
        private final int[] starts;
        private final int[] ends;
        /** Tokens resolved before the scan first failed; queries beyond this yield no span. */
        private final int resolved;

        private TokenOffsets(int[] starts, int[] ends, int resolved) {
            this.starts = starts;
            this.ends = ends;
            this.resolved = resolved;
        }

        static TokenOffsets of(Document document) {
            String[] tokens = document.tokens();
            String normalizedText = document.normalizedText();
            int[] starts = new int[tokens.length];
            int[] ends = new int[tokens.length];
            int cursor = 0;
            int resolved = 0;
            while (resolved < tokens.length) {
                int next = indexOfTokenFrom(normalizedText, tokens[resolved], cursor);
                if (next < 0) break;
                starts[resolved] = next;
                cursor = next + tokens[resolved].length();
                ends[resolved] = cursor;
                resolved++;
            }
            return new TokenOffsets(starts, ends, resolved);
        }

        /** Span covering tokens [firstToken, endTokenExclusive), or {0,0} when unavailable. */
        int[] span(int firstToken, int endTokenExclusive) {
            if (firstToken < 0 || endTokenExclusive <= firstToken
                    || endTokenExclusive > starts.length || endTokenExclusive > resolved) {
                return new int[]{0, 0};
            }
            return new int[]{starts[firstToken], ends[endTokenExclusive - 1]};
        }
    }

    private static int indexOfFrom(String text, String pattern, int start) {
        if (pattern.isEmpty()) return Math.max(0, start);
        for (int i = Math.max(0, start); i + pattern.length() <= text.length(); i++) {
            int j = 0;
            while (j < pattern.length() && text.charAt(i + j) == pattern.charAt(j)) j++;
            if (j == pattern.length()) return i;
        }
        return -1;
    }

    private static int indexOfTokenFrom(String text, String token, int start) {
        int position = indexOfFrom(text, token, start);
        while (position >= 0) {
            int end = position + token.length();
            boolean leftBoundary = position == 0 || text.charAt(position - 1) == ' ';
            boolean rightBoundary = end == text.length() || text.charAt(end) == ' ';
            if (leftBoundary && rightBoundary) return position;
            position = indexOfFrom(text, token, position + 1);
        }
        return -1;
    }

    private double jaccard(String[] first, String[] second) {
        HashSet<String> firstSet = new HashSet<>();
        HashSet<String> secondSet = new HashSet<>();
        for (String value : first) firstSet.add(value);
        for (String value : second) secondSet.add(value);
        if (firstSet.isEmpty() && secondSet.isEmpty()) return 0.0;
        int intersection = 0;
        for (String value : first) {
            if (secondSet.contains(value) && firstSet.remove(value)) intersection++;
        }
        int union = firstSet.size() + secondSet.size();
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private PassageMatch[] compactEvidence(PassageMatch[] evidence, int maximum) {
        if (evidence.length <= maximum) return evidence;
        // Repeated type-units make the greedy approximation prefer one item from
        // every available evidence family before selecting near-duplicate passages.
        final int typeCoverageWeight = 64;
        GreedyEvidenceSelector.EvidenceCandidate[] candidates =
                new GreedyEvidenceSelector.EvidenceCandidate[evidence.length];
        int typeUniverse = MatchType.values().length * typeCoverageWeight;
        int universe = typeUniverse + evidence.length;
        for (int i = 0; i < evidence.length; i++) {
            int[] covered = new int[typeCoverageWeight + 1];
            int typeStart = evidence[i].type().ordinal() * typeCoverageWeight;
            for (int unit = 0; unit < typeCoverageWeight; unit++) covered[unit] = typeStart + unit;
            covered[typeCoverageWeight] = typeUniverse + i;
            double cost = Math.max(1.0, evidence[i].excerpt().length() / 50.0);
            candidates[i] = new GreedyEvidenceSelector.EvidenceCandidate(
                    "evidence-" + i, covered, Math.max(0.01, evidence[i].similarity()), cost);
        }
        GreedyEvidenceSelector.SelectionResult selection =
                GreedyEvidenceSelector.select(candidates, universe, maximum);
        PassageMatch[] selected = new PassageMatch[selection.selectedCount()];
        for (int i = 0; i < selected.length; i++) {
            selected[i] = evidence[selection.selectedIndexAt(i)];
        }
        return selected;
    }

    private String bounded(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private String[] boundedTokens(String[] tokens, int maximum) {
        if (tokens.length <= maximum) return tokens;
        String[] result = new String[maximum];
        System.arraycopy(tokens, 0, result, 0, maximum);
        return result;
    }

    private String[] copy(String[] source) {
        String[] result = new String[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("document analysis was interrupted");
        }
    }

    private static final class ExactResult {
        private final double score;
        private final EvidenceAccumulator evidence;

        private ExactResult(double score, EvidenceAccumulator evidence) {
            this.score = score;
            this.evidence = evidence;
        }
    }

    private static final class EvidenceAccumulator {
        private final SinglyLinkedList<PassageMatch> values = new SinglyLinkedList<>();

        void add(PassageMatch value) {
            if (value == null) return;
            values.addLast(value);
        }

        PassageMatch[] toArray() {
            DynamicArray<PassageMatch> ordered = values.toDynamicArray();
            PassageMatch[] result = new PassageMatch[ordered.size()];
            for (int i = 0; i < result.length; i++) result[i] = ordered.get(i);
            return result;
        }
    }
}
