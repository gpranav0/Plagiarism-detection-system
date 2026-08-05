package edu.academic.integrity.algorithms.text;

/** Multi-pattern matching with array-backed trie nodes and failure links. */
public final class AhoCorasick {
    private final String[] patterns;
    private final Node root;
    private int nodeCount;

    public AhoCorasick(String[] patterns) {
        validatePatterns(patterns);
        this.patterns = copy(patterns);
        root = newNode();
        buildTrie();
        buildFailureLinks();
    }

    /** Finds every pattern occurrence, including overlaps and duplicate patterns. */
    public Match[] search(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        MatchAccumulator matches = new MatchAccumulator();
        Node state = root;
        for (int position = 0; position < text.length(); position++) {
            char symbol = text.charAt(position);
            Node transition = state.child(symbol);
            while (state != root && transition == null) {
                state = state.failure;
                transition = state.child(symbol);
            }
            state = transition == null ? root : transition;
            for (int output = 0; output < state.outputCount; output++) {
                int patternIndex = state.outputs[output];
                int endExclusive = position + 1;
                matches.add(new Match(patternIndex, patterns[patternIndex],
                        endExclusive - patterns[patternIndex].length(), endExclusive));
            }
        }
        return matches.toArray();
    }

    public int patternCount() {
        return patterns.length;
    }

    public String[] patterns() {
        return copy(patterns);
    }

    private void buildTrie() {
        for (int patternIndex = 0; patternIndex < patterns.length; patternIndex++) {
            Node node = root;
            String pattern = patterns[patternIndex];
            for (int index = 0; index < pattern.length(); index++) {
                char symbol = pattern.charAt(index);
                Node child = node.child(symbol);
                if (child == null) {
                    child = newNode();
                    node.addChild(symbol, child);
                }
                node = child;
            }
            node.addOutput(patternIndex);
        }
    }

    private void buildFailureLinks() {
        root.failure = root;
        Node[] queue = new Node[nodeCount];
        int head = 0;
        int tail = 0;
        for (int index = 0; index < root.childCount; index++) {
            Node child = root.children[index];
            child.failure = root;
            queue[tail++] = child;
        }

        while (head < tail) {
            Node current = queue[head++];
            for (int edge = 0; edge < current.childCount; edge++) {
                char symbol = current.labels[edge];
                Node child = current.children[edge];
                Node fallback = current.failure;
                Node candidate = fallback.child(symbol);
                while (fallback != root && candidate == null) {
                    fallback = fallback.failure;
                    candidate = fallback.child(symbol);
                }
                child.failure = candidate == null ? root : candidate;
                child.appendOutputs(child.failure);
                queue[tail++] = child;
            }
        }
    }

    private Node newNode() {
        nodeCount++;
        return new Node();
    }

    private static String[] copy(String[] source) {
        String[] result = new String[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[index];
        }
        return result;
    }

    private static void validatePatterns(String[] patterns) {
        if (patterns == null) {
            throw new IllegalArgumentException("patterns cannot be null");
        }
        for (int index = 0; index < patterns.length; index++) {
            if (patterns[index] == null || patterns[index].length() == 0) {
                throw new IllegalArgumentException("patterns must be non-null and non-empty");
            }
        }
    }

    public static final class Match {
        private final int patternIndex;
        private final String pattern;
        private final int start;
        private final int endExclusive;

        private Match(int patternIndex, String pattern, int start, int endExclusive) {
            this.patternIndex = patternIndex;
            this.pattern = pattern;
            this.start = start;
            this.endExclusive = endExclusive;
        }

        public int patternIndex() {
            return patternIndex;
        }

        public int getPatternIndex() {
            return patternIndex;
        }

        public String pattern() {
            return pattern;
        }

        public String getPattern() {
            return pattern;
        }

        public int start() {
            return start;
        }

        public int getStart() {
            return start;
        }

        public int endExclusive() {
            return endExclusive;
        }

        public int getEndExclusive() {
            return endExclusive;
        }
    }

    private static final class Node {
        private char[] labels = new char[4];
        private Node[] children = new Node[4];
        private int childCount;
        private int[] outputs = new int[2];
        private int outputCount;
        private Node failure;

        Node child(char symbol) {
            for (int index = 0; index < childCount; index++) {
                if (labels[index] == symbol) {
                    return children[index];
                }
            }
            return null;
        }

        void addChild(char symbol, Node child) {
            if (childCount == labels.length) {
                char[] grownLabels = new char[labels.length * 2];
                Node[] grownChildren = new Node[children.length * 2];
                for (int index = 0; index < childCount; index++) {
                    grownLabels[index] = labels[index];
                    grownChildren[index] = children[index];
                }
                labels = grownLabels;
                children = grownChildren;
            }
            labels[childCount] = symbol;
            children[childCount] = child;
            childCount++;
        }

        void addOutput(int patternIndex) {
            ensureOutputCapacity(outputCount + 1);
            outputs[outputCount++] = patternIndex;
        }

        void appendOutputs(Node source) {
            ensureOutputCapacity(outputCount + source.outputCount);
            for (int index = 0; index < source.outputCount; index++) {
                outputs[outputCount++] = source.outputs[index];
            }
        }

        private void ensureOutputCapacity(int required) {
            if (required <= outputs.length) {
                return;
            }
            int capacity = outputs.length * 2;
            while (capacity < required) {
                capacity *= 2;
            }
            int[] grown = new int[capacity];
            for (int index = 0; index < outputCount; index++) {
                grown[index] = outputs[index];
            }
            outputs = grown;
        }
    }

    private static final class MatchAccumulator {
        private Match[] matches = new Match[8];
        private int size;

        void add(Match match) {
            if (size == matches.length) {
                Match[] grown = new Match[matches.length * 2];
                for (int index = 0; index < matches.length; index++) {
                    grown[index] = matches[index];
                }
                matches = grown;
            }
            matches[size++] = match;
        }

        Match[] toArray() {
            Match[] result = new Match[size];
            for (int index = 0; index < size; index++) {
                result[index] = matches[index];
            }
            return result;
        }
    }
}
