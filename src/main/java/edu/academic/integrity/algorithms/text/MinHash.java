package edu.academic.integrity.algorithms.text;

/** Deterministic MinHash signatures and pair-candidate shortlisting. */
public final class MinHash {
    private static final long DEFAULT_SEED = 0x4d595df4d0f33173L;
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final long[] salts;

    public MinHash(int signatureSize) {
        this(signatureSize, DEFAULT_SEED);
    }

    public MinHash(int signatureSize, long seed) {
        if (signatureSize <= 0) {
            throw new IllegalArgumentException("signature size must be positive");
        }
        salts = new long[signatureSize];
        long state = seed;
        for (int index = 0; index < signatureSize; index++) {
            state += GOLDEN_GAMMA;
            salts[index] = mix64(state);
        }
    }

    public int signatureSize() {
        return salts.length;
    }

    /** Builds one deterministic signature. Repeated shingles do not affect it. */
    public long[] signature(String[] shingles) {
        validateShingles(shingles, "shingles");
        long[] signature = new long[salts.length];
        for (int index = 0; index < signature.length; index++) {
            signature[index] = Long.MAX_VALUE;
        }
        for (int shingle = 0; shingle < shingles.length; shingle++) {
            long baseHash = baseHash(shingles[shingle]);
            for (int function = 0; function < salts.length; function++) {
                long candidate = mix64(baseHash ^ salts[function]) & Long.MAX_VALUE;
                if (candidate < signature[function]) {
                    signature[function] = candidate;
                }
            }
        }
        return signature;
    }

    /** Estimates set Jaccard similarity from equal-length signatures. */
    public static double signatureSimilarity(long[] first, long[] second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("signatures cannot be null");
        }
        if (first.length == 0 || first.length != second.length) {
            throw new IllegalArgumentException("signatures must have the same positive length");
        }
        int equal = 0;
        for (int index = 0; index < first.length; index++) {
            if (first[index] == second[index]) {
                equal++;
            }
        }
        return (double) equal / first.length;
    }

    public double similarity(String[] firstShingles, String[] secondShingles) {
        return signatureSimilarity(signature(firstShingles), signature(secondShingles));
    }

    /** Returns candidates in ascending (first index, second index) order. */
    public Candidate[] shortlist(String[][] documentShingles, double threshold) {
        return analyzeCandidates(documentShingles, threshold).candidates();
    }

    /** Computes signatures once and returns them with all threshold candidates. */
    public CandidateResult analyzeCandidates(String[][] documentShingles, double threshold) {
        validateDocuments(documentShingles);
        if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be between zero and one");
        }
        long[][] signatures = new long[documentShingles.length][];
        for (int document = 0; document < documentShingles.length; document++) {
            signatures[document] = signature(documentShingles[document]);
        }
        CandidateAccumulator candidates = new CandidateAccumulator();
        for (int first = 0; first < signatures.length; first++) {
            for (int second = first + 1; second < signatures.length; second++) {
                double similarity = signatureSimilarity(signatures[first], signatures[second]);
                if (similarity >= threshold) {
                    candidates.add(new Candidate(first, second, similarity));
                }
            }
        }
        return new CandidateResult(signatures, candidates.toArray());
    }

    private static long baseHash(String value) {
        long hash = FNV_OFFSET;
        for (int index = 0; index < value.length(); index++) {
            char symbol = value.charAt(index);
            hash ^= symbol & 0xffL;
            hash *= FNV_PRIME;
            hash ^= symbol >>> 8;
            hash *= FNV_PRIME;
        }
        return mix64(hash ^ value.length());
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static void validateDocuments(String[][] documents) {
        if (documents == null) {
            throw new IllegalArgumentException("documentShingles cannot be null");
        }
        for (int document = 0; document < documents.length; document++) {
            validateShingles(documents[document], "documentShingles[" + document + "]");
        }
    }

    private static void validateShingles(String[] shingles, String name) {
        if (shingles == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        for (int index = 0; index < shingles.length; index++) {
            if (shingles[index] == null) {
                throw new IllegalArgumentException(name + " cannot contain null values");
            }
        }
    }

    public static final class Candidate {
        private final int firstDocumentIndex;
        private final int secondDocumentIndex;
        private final double estimatedSimilarity;

        private Candidate(int firstDocumentIndex, int secondDocumentIndex, double estimatedSimilarity) {
            this.firstDocumentIndex = firstDocumentIndex;
            this.secondDocumentIndex = secondDocumentIndex;
            this.estimatedSimilarity = estimatedSimilarity;
        }

        public int firstDocumentIndex() {
            return firstDocumentIndex;
        }

        public int getFirstDocumentIndex() {
            return firstDocumentIndex;
        }

        public int secondDocumentIndex() {
            return secondDocumentIndex;
        }

        public int getSecondDocumentIndex() {
            return secondDocumentIndex;
        }

        public double estimatedSimilarity() {
            return estimatedSimilarity;
        }

        public double getEstimatedSimilarity() {
            return estimatedSimilarity;
        }
    }

    public static final class CandidateResult {
        private final long[][] signatures;
        private final Candidate[] candidates;

        private CandidateResult(long[][] signatures, Candidate[] candidates) {
            this.signatures = deepCopy(signatures);
            this.candidates = copy(candidates);
        }

        public long[][] signatures() {
            return deepCopy(signatures);
        }

        public long[][] getSignatures() {
            return signatures();
        }

        public Candidate[] candidates() {
            return copy(candidates);
        }

        public Candidate[] getCandidates() {
            return candidates();
        }

        private static long[][] deepCopy(long[][] source) {
            long[][] result = new long[source.length][];
            for (int row = 0; row < source.length; row++) {
                result[row] = new long[source[row].length];
                for (int column = 0; column < source[row].length; column++) {
                    result[row][column] = source[row][column];
                }
            }
            return result;
        }

        private static Candidate[] copy(Candidate[] source) {
            Candidate[] result = new Candidate[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = source[index];
            }
            return result;
        }
    }

    private static final class CandidateAccumulator {
        private Candidate[] values = new Candidate[8];
        private int size;

        void add(Candidate value) {
            if (size == values.length) {
                Candidate[] grown = new Candidate[values.length * 2];
                for (int index = 0; index < values.length; index++) {
                    grown[index] = values[index];
                }
                values = grown;
            }
            values[size++] = value;
        }

        Candidate[] toArray() {
            Candidate[] result = new Candidate[size];
            for (int index = 0; index < size; index++) {
                result[index] = values[index];
            }
            return result;
        }
    }
}
