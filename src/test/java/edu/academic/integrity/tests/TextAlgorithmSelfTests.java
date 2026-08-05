package edu.academic.integrity.tests;

import edu.academic.integrity.algorithms.text.AhoCorasick;
import edu.academic.integrity.algorithms.text.EditDistance;
import edu.academic.integrity.algorithms.text.FuzzyAlignment;
import edu.academic.integrity.algorithms.text.KMP;
import edu.academic.integrity.algorithms.text.LCS;
import edu.academic.integrity.algorithms.text.MinHash;
import edu.academic.integrity.algorithms.text.RabinKarp;
import edu.academic.integrity.algorithms.text.ShingleGenerator;
import edu.academic.integrity.algorithms.text.TextNormalizer;
import edu.academic.integrity.algorithms.text.Trie;
import edu.academic.integrity.algorithms.text.ZAlgorithm;

public final class TextAlgorithmSelfTests {
    private static int assertions;

    private TextAlgorithmSelfTests() {
    }

    public static void main(String[] arguments) {
        int completed = runAll();
        System.out.println("Text algorithm self-tests passed: " + completed + " assertions");
    }

    public static int runAll() {
        assertions = 0;
        testNormalizationAndShingles();
        testExactMatchers();
        testRandomizedExactMatchers();
        testTrieAndAhoCorasick();
        testDynamicProgramming();
        testFuzzyAlignment();
        testMinHash();
        return assertions;
    }

    private static void testNormalizationAndShingles() {
        String normalized = TextNormalizer.normalize("  Data, DATA! structures\nwork. ");
        equal("data data structures work", normalized, "normalization");
        String[] tokens = TextNormalizer.tokenize(normalized, new String[] {"DATA", "the"});
        equal(2, tokens.length, "stopword count");
        equal("structures", tokens[0], "first retained token");
        equal("work", tokens[1], "second retained token");

        String[] shingles = ShingleGenerator.wordShingles(
                new String[] {"one", "two", "three", "four"}, 3);
        equal(2, shingles.length, "word shingle count");
        equal("one two three", shingles[0], "word shingle value");
        equal(3, ShingleGenerator.characterShingles("abcd", 2).length, "character shingle count");
        close(1.0 / 3.0, ShingleGenerator.jaccardSimilarity(
                new String[] {"a", "a", "b"}, new String[] {"b", "c"}), "Jaccard set semantics");
    }

    private static void testExactMatchers() {
        int[] kmp = KMP.findAll("aaaa", "aa");
        equal(3, kmp.length, "KMP overlapping count");
        equal(0, kmp[0], "KMP first");
        equal(1, kmp[1], "KMP second");
        equal(2, kmp[2], "KMP third");
        equal(6, RabinKarp.indexOf("source passage source", " passage"), "Rabin-Karp position");
        int[] rabin = RabinKarp.findAll("banana", "ana");
        equal(2, rabin.length, "Rabin-Karp overlapping count");
        equal(1, rabin[0], "Rabin-Karp first");
        equal(3, rabin[1], "Rabin-Karp second");
        int[] zMatches = ZAlgorithm.findAll("aaaa", "aa");
        equal(3, zMatches.length, "Z overlapping count");
        int[] z = ZAlgorithm.compute("aabcaabxaaaz");
        equal(3, z[4], "Z value");
    }

    private static void testRandomizedExactMatchers() {
        long state = 0x1234abcdL;
        for (int trial = 0; trial < 250; trial++) {
            state = nextState(state);
            int textLength = (int) ((state >>> 8) % 30);
            state = nextState(state);
            int patternLength = (int) ((state >>> 8) % 9);
            char[] textCharacters = new char[textLength];
            char[] patternCharacters = new char[patternLength];
            for (int index = 0; index < textCharacters.length; index++) {
                state = nextState(state);
                textCharacters[index] = (char) ('a' + ((state >>> 16) % 4));
            }
            for (int index = 0; index < patternCharacters.length; index++) {
                state = nextState(state);
                patternCharacters[index] = (char) ('a' + ((state >>> 16) % 4));
            }
            String text = new String(textCharacters);
            String pattern = new String(patternCharacters);
            int[] expected = naiveMatches(text, pattern);
            equal(expected, KMP.findAll(text, pattern), "random KMP " + trial);
            equal(expected, RabinKarp.findAll(text, pattern), "random Rabin-Karp " + trial);
            equal(expected, ZAlgorithm.findAll(text, pattern), "random Z " + trial);
        }
    }

    private static void testTrieAndAhoCorasick() {
        Trie<Integer> trie = new Trie<Integer>();
        equal(null, trie.put("car", 1), "trie insert");
        trie.put("cat", 2);
        trie.put("", 9);
        equal(3, trie.size(), "trie size");
        equal(Integer.valueOf(1), trie.get("car"), "trie get");
        String[] keys = trie.keysWithPrefix("ca");
        equal(2, keys.length, "trie prefix count");
        equal("car", keys[0], "trie lexical first");
        equal("cat", keys[1], "trie lexical second");
        equal(Integer.valueOf(1), trie.remove("car"), "trie remove");
        check(!trie.containsKey("car") && trie.containsKey("cat"), "trie pruning");

        AhoCorasick matcher = new AhoCorasick(new String[] {"he", "she", "his", "hers"});
        AhoCorasick.Match[] matches = matcher.search("ushers");
        equal(3, matches.length, "Aho-Corasick match count");
        check(hasMatch(matches, "she", 1), "Aho-Corasick she");
        check(hasMatch(matches, "he", 2), "Aho-Corasick suffix output");
        check(hasMatch(matches, "hers", 2), "Aho-Corasick hers");
    }

    private static void testDynamicProgramming() {
        equal(4, LCS.length("AGGTAB", "GXTXAYB"), "character LCS length");
        equal("GTAB", LCS.sequence("AGGTAB", "GXTXAYB"), "character LCS sequence");
        LCS.Result tokenLcs = LCS.analyze(
                new String[] {"a", "copied", "small", "passage"},
                new String[] {"copied", "different", "small", "passage"});
        equal(3, tokenLcs.length(), "token LCS length");
        equal("copied small passage", tokenLcs.passage(), "token LCS passage");
        equal(3, EditDistance.distance("kitten", "sitting"), "character edit distance");
        equal(1, EditDistance.distance(new String[] {"a", "b"}, new String[] {"a", "c"}),
                "token edit distance");
    }

    private static void testFuzzyAlignment() {
        FuzzyAlignment.Result result = FuzzyAlignment.align(
                new String[] {"a", "b", "c", "d", "e"},
                new String[] {"x", "b", "c", "d", "y"});
        equal(9, result.score(), "token alignment score");
        equal(1, result.firstStart(), "token alignment first start");
        equal(4, result.firstEndExclusive(), "token alignment first end");
        equal(1, result.secondStart(), "token alignment second start");
        equal(4, result.secondEndExclusive(), "token alignment second end");
        equal("b c d", result.firstPassage(), "token aligned passage");

        FuzzyAlignment.CharacterResult characters = FuzzyAlignment.align("zzabcxx", "yyabcww");
        equal(9, characters.score(), "character alignment score");
        equal("abc", characters.firstPassage(), "character aligned passage");
    }

    private static void testMinHash() {
        MinHash minHash = new MinHash(64, 12345L);
        String[] first = new String[] {"alpha beta", "beta gamma", "gamma delta"};
        long[] firstSignature = minHash.signature(first);
        long[] repeatedSignature = minHash.signature(first);
        close(1.0, MinHash.signatureSimilarity(firstSignature, repeatedSignature), "deterministic MinHash");
        close(1.0, minHash.similarity(new String[0], new String[0]), "empty MinHash sets");
        MinHash.CandidateResult result = minHash.analyzeCandidates(
                new String[][] {first, new String[] {"other"}, first}, 1.0);
        equal(1, result.candidates().length, "MinHash exact-signature candidate count");
        equal(0, result.candidates()[0].firstDocumentIndex(), "MinHash first candidate index");
        equal(2, result.candidates()[0].secondDocumentIndex(), "MinHash second candidate index");
    }

    private static boolean hasMatch(AhoCorasick.Match[] matches, String pattern, int start) {
        for (int index = 0; index < matches.length; index++) {
            if (matches[index].pattern().equals(pattern) && matches[index].start() == start) {
                return true;
            }
        }
        return false;
    }

    private static int[] naiveMatches(String text, String pattern) {
        if (pattern.length() == 0) {
            return new int[] {0};
        }
        int count = 0;
        for (int start = 0; start + pattern.length() <= text.length(); start++) {
            if (regionMatches(text, pattern, start)) {
                count++;
            }
        }
        int[] matches = new int[count];
        int output = 0;
        for (int start = 0; start + pattern.length() <= text.length(); start++) {
            if (regionMatches(text, pattern, start)) {
                matches[output++] = start;
            }
        }
        return matches;
    }

    private static boolean regionMatches(String text, String pattern, int start) {
        for (int offset = 0; offset < pattern.length(); offset++) {
            if (text.charAt(start + offset) != pattern.charAt(offset)) {
                return false;
            }
        }
        return true;
    }

    private static long nextState(long state) {
        return state * 6364136223846793005L + 1442695040888963407L;
    }

    private static void equal(int expected, int actual, String label) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(String expected, String actual, String label) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(int[] expected, int[] actual, String label) {
        assertions++;
        if (expected.length != actual.length) {
            throw new AssertionError(label + ": expected length " + expected.length
                    + ", got " + actual.length);
        }
        for (int index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(label + ": mismatch at " + index
                        + ", expected " + expected[index] + ", got " + actual[index]);
            }
        }
    }

    private static void close(double expected, double actual, String label) {
        assertions++;
        if (actual < expected - 0.000000001 || actual > expected + 0.000000001) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
