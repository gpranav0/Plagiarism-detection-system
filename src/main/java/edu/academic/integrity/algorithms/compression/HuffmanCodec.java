package edu.academic.integrity.algorithms.compression;

// StandardCharsets is required to make report text encoding deterministic on every OS.
import java.nio.charset.StandardCharsets;

/**
 * A self-contained Huffman codec. The binary representation stores a compact
 * frequency table, so decompression does not require the original tree.
 */
public final class HuffmanCodec {
    private static final byte MAGIC_H = 0x48;
    private static final byte MAGIC_P = 0x50;
    private static final byte MAGIC_D = 0x44;
    private static final byte FORMAT_VERSION = 0x01;
    private static final int FIXED_HEADER_BYTES = 10;
    private static final int ALPHABET_SIZE = 256;

    public byte[] compress(byte[] input) {
        return compressDetailed(input).compressedBytes();
    }

    public CompressionResult compressDetailed(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        int[] frequencies = frequencies(input);
        int symbolCount = countSymbols(frequencies);
        Node root = buildTree(frequencies);
        String[] codes = new String[ALPHABET_SIZE];
        if (root != null) {
            buildCodes(root, "", codes);
        }

        long payloadBits = 0L;
        for (int symbol = 0; symbol < ALPHABET_SIZE; symbol++) {
            if (frequencies[symbol] > 0 && codes[symbol] != null) {
                long contribution = (long) frequencies[symbol] * codes[symbol].length();
                if (Long.MAX_VALUE - payloadBits < contribution) {
                    throw new ArithmeticException("Encoded bit length overflow");
                }
                payloadBits += contribution;
            }
        }
        long payloadBytesLong = (payloadBits + 7L) / 8L;
        long headerBytesLong = FIXED_HEADER_BYTES + (long) symbolCount * 5L;
        if (headerBytesLong + payloadBytesLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Compressed representation is too large for a Java array");
        }
        int headerBytes = (int) headerBytesLong;
        byte[] encoded = new byte[(int) (headerBytesLong + payloadBytesLong)];
        encoded[0] = MAGIC_H;
        encoded[1] = MAGIC_P;
        encoded[2] = MAGIC_D;
        encoded[3] = FORMAT_VERSION;
        writeInt(encoded, 4, input.length);
        writeUnsignedShort(encoded, 8, symbolCount);
        int cursor = FIXED_HEADER_BYTES;
        for (int symbol = 0; symbol < ALPHABET_SIZE; symbol++) {
            if (frequencies[symbol] > 0) {
                encoded[cursor++] = (byte) symbol;
                writeInt(encoded, cursor, frequencies[symbol]);
                cursor += 4;
            }
        }

        long bitIndex = 0L;
        for (int i = 0; i < input.length; i++) {
            String code = codes[input[i] & 0xff];
            for (int bit = 0; bit < code.length(); bit++) {
                if (code.charAt(bit) == '1') {
                    int byteIndex = headerBytes + (int) (bitIndex >>> 3);
                    int shift = 7 - (int) (bitIndex & 7L);
                    encoded[byteIndex] = (byte) (encoded[byteIndex] | (1 << shift));
                }
                bitIndex++;
            }
        }
        return new CompressionResult(encoded, input.length, headerBytes, payloadBits);
    }

    public byte[] decompress(byte[] encoded) {
        Header header = parseHeader(encoded);
        if (header.originalLength == 0) {
            return new byte[0];
        }
        Node root = buildTree(header.frequencies);
        if (root == null) {
            throw new IllegalArgumentException("Corrupt Huffman data: missing tree");
        }
        byte[] output = new byte[header.originalLength];
        if (root.isLeaf()) {
            if (encoded.length != header.payloadOffset) {
                throw new IllegalArgumentException("Corrupt Huffman data: unexpected single-symbol payload");
            }
            for (int i = 0; i < output.length; i++) {
                output[i] = (byte) root.symbol;
            }
            return output;
        }

        long expectedBits = encodedBitLength(header.frequencies, root);
        long payloadBytes = (expectedBits + 7L) / 8L;
        if ((long) header.payloadOffset + payloadBytes != encoded.length) {
            throw new IllegalArgumentException("Corrupt Huffman data: payload length mismatch");
        }

        int outputIndex = 0;
        Node current = root;
        for (long bitIndex = 0; bitIndex < expectedBits; bitIndex++) {
            int byteIndex = header.payloadOffset + (int) (bitIndex >>> 3);
            int shift = 7 - (int) (bitIndex & 7L);
            int bit = (encoded[byteIndex] >>> shift) & 1;
            current = bit == 0 ? current.left : current.right;
            if (current == null) {
                throw new IllegalArgumentException("Corrupt Huffman data: invalid code path");
            }
            if (current.isLeaf()) {
                if (outputIndex >= output.length) {
                    throw new IllegalArgumentException("Corrupt Huffman data: too many decoded symbols");
                }
                output[outputIndex++] = (byte) current.symbol;
                current = root;
            }
        }
        if (outputIndex != output.length || current != root) {
            throw new IllegalArgumentException("Corrupt Huffman data: incomplete symbol stream");
        }
        validateZeroPadding(encoded, header.payloadOffset, expectedBits);
        return output;
    }

    public byte[] compressText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        return compress(text.getBytes(StandardCharsets.UTF_8));
    }

    public String decompressText(byte[] encoded) {
        return new String(decompress(encoded), StandardCharsets.UTF_8);
    }

    private static Header parseHeader(byte[] encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Encoded input cannot be null");
        }
        if (encoded.length < FIXED_HEADER_BYTES) {
            throw new IllegalArgumentException("Corrupt Huffman data: truncated header");
        }
        if (encoded[0] != MAGIC_H || encoded[1] != MAGIC_P
                || encoded[2] != MAGIC_D || encoded[3] != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported Huffman data format");
        }
        int originalLength = readInt(encoded, 4);
        if (originalLength < 0) {
            throw new IllegalArgumentException("Corrupt Huffman data: negative original length");
        }
        int symbolCount = readUnsignedShort(encoded, 8);
        if (symbolCount > ALPHABET_SIZE) {
            throw new IllegalArgumentException("Corrupt Huffman data: invalid symbol count");
        }
        int payloadOffset = FIXED_HEADER_BYTES + symbolCount * 5;
        if (payloadOffset > encoded.length) {
            throw new IllegalArgumentException("Corrupt Huffman data: truncated frequency table");
        }
        int[] frequencies = new int[ALPHABET_SIZE];
        long total = 0L;
        int cursor = FIXED_HEADER_BYTES;
        for (int entry = 0; entry < symbolCount; entry++) {
            int symbol = encoded[cursor++] & 0xff;
            int frequency = readInt(encoded, cursor);
            cursor += 4;
            if (frequency <= 0 || frequencies[symbol] != 0) {
                throw new IllegalArgumentException("Corrupt Huffman data: invalid frequency entry");
            }
            frequencies[symbol] = frequency;
            total += frequency;
        }
        if (total != originalLength || (originalLength == 0) != (symbolCount == 0)) {
            throw new IllegalArgumentException("Corrupt Huffman data: frequency total mismatch");
        }
        if (originalLength == 0 && encoded.length != payloadOffset) {
            throw new IllegalArgumentException("Corrupt Huffman data: unexpected empty payload");
        }
        return new Header(originalLength, frequencies, payloadOffset);
    }

    private static long encodedBitLength(int[] frequencies, Node root) {
        String[] codes = new String[ALPHABET_SIZE];
        buildCodes(root, "", codes);
        long bits = 0L;
        for (int symbol = 0; symbol < ALPHABET_SIZE; symbol++) {
            if (frequencies[symbol] > 0) {
                bits += (long) frequencies[symbol] * codes[symbol].length();
            }
        }
        return bits;
    }

    private static void validateZeroPadding(byte[] encoded, int payloadOffset, long bitLength) {
        if (bitLength == 0L || (bitLength & 7L) == 0L) {
            return;
        }
        int lastByteIndex = payloadOffset + (int) (bitLength >>> 3);
        int unusedBits = 8 - (int) (bitLength & 7L);
        int mask = (1 << unusedBits) - 1;
        if ((encoded[lastByteIndex] & mask) != 0) {
            throw new IllegalArgumentException("Corrupt Huffman data: non-zero padding");
        }
    }

    private static int[] frequencies(byte[] input) {
        int[] result = new int[ALPHABET_SIZE];
        for (int i = 0; i < input.length; i++) {
            result[input[i] & 0xff]++;
        }
        return result;
    }

    private static int countSymbols(int[] frequencies) {
        int count = 0;
        for (int i = 0; i < frequencies.length; i++) {
            if (frequencies[i] > 0) {
                count++;
            }
        }
        return count;
    }

    private static Node buildTree(int[] frequencies) {
        NodeHeap heap = new NodeHeap(ALPHABET_SIZE);
        for (int symbol = 0; symbol < ALPHABET_SIZE; symbol++) {
            if (frequencies[symbol] > 0) {
                heap.add(new Node(frequencies[symbol], symbol, symbol, null, null));
            }
        }
        if (heap.size() == 0) {
            return null;
        }
        while (heap.size() > 1) {
            Node first = heap.removeMinimum();
            Node second = heap.removeMinimum();
            long combined = first.frequency + second.frequency;
            if (combined > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Frequency total exceeds supported input size");
            }
            int minimumSymbol = first.minimumSymbol < second.minimumSymbol
                    ? first.minimumSymbol : second.minimumSymbol;
            heap.add(new Node((int) combined, -1, minimumSymbol, first, second));
        }
        return heap.removeMinimum();
    }

    private static void buildCodes(Node node, String prefix, String[] codes) {
        if (node.isLeaf()) {
            codes[node.symbol] = prefix;
            return;
        }
        buildCodes(node.left, prefix + '0', codes);
        buildCodes(node.right, prefix + '1', codes);
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] source, int offset) {
        return ((source[offset] & 0xff) << 24)
                | ((source[offset + 1] & 0xff) << 16)
                | ((source[offset + 2] & 0xff) << 8)
                | (source[offset + 3] & 0xff);
    }

    private static void writeUnsignedShort(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static int readUnsignedShort(byte[] source, int offset) {
        return ((source[offset] & 0xff) << 8) | (source[offset + 1] & 0xff);
    }

    public static final class CompressionResult {
        private final byte[] compressed;
        private final int originalBytes;
        private final int headerBytes;
        private final long payloadBits;

        private CompressionResult(byte[] compressed, int originalBytes,
                int headerBytes, long payloadBits) {
            this.compressed = compressed;
            this.originalBytes = originalBytes;
            this.headerBytes = headerBytes;
            this.payloadBits = payloadBits;
        }

        public byte[] compressedBytes() {
            byte[] result = new byte[compressed.length];
            for (int i = 0; i < compressed.length; i++) {
                result[i] = compressed[i];
            }
            return result;
        }

        public int originalByteCount() {
            return originalBytes;
        }

        public int compressedByteCount() {
            return compressed.length;
        }

        public int headerByteCount() {
            return headerBytes;
        }

        public long payloadBitCount() {
            return payloadBits;
        }

        /** Compressed bytes divided by original bytes; 1.0 for empty input. */
        public double compressionRatio() {
            return originalBytes == 0 ? 1.0 : (double) compressed.length / originalBytes;
        }
    }

    private static final class Header {
        private final int originalLength;
        private final int[] frequencies;
        private final int payloadOffset;

        private Header(int originalLength, int[] frequencies, int payloadOffset) {
            this.originalLength = originalLength;
            this.frequencies = frequencies;
            this.payloadOffset = payloadOffset;
        }
    }

    private static final class Node {
        private final int frequency;
        private final int symbol;
        private final int minimumSymbol;
        private final Node left;
        private final Node right;

        private Node(int frequency, int symbol, int minimumSymbol, Node left, Node right) {
            this.frequency = frequency;
            this.symbol = symbol;
            this.minimumSymbol = minimumSymbol;
            this.left = left;
            this.right = right;
        }

        private boolean isLeaf() {
            return left == null && right == null;
        }
    }

    private static final class NodeHeap {
        private final Node[] values;
        private int size;

        private NodeHeap(int capacity) {
            values = new Node[capacity];
        }

        private int size() {
            return size;
        }

        private void add(Node value) {
            int index = size++;
            values[index] = value;
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                if (compare(values[parent], values[index]) <= 0) {
                    break;
                }
                swap(parent, index);
                index = parent;
            }
        }

        private Node removeMinimum() {
            Node result = values[0];
            values[0] = values[--size];
            values[size] = null;
            int index = 0;
            while (true) {
                int left = (index << 1) + 1;
                if (left >= size) {
                    break;
                }
                int right = left + 1;
                int smaller = right < size && compare(values[right], values[left]) < 0
                        ? right : left;
                if (compare(values[index], values[smaller]) <= 0) {
                    break;
                }
                swap(index, smaller);
                index = smaller;
            }
            return result;
        }

        private void swap(int first, int second) {
            Node temporary = values[first];
            values[first] = values[second];
            values[second] = temporary;
        }

        private static int compare(Node first, Node second) {
            if (first.frequency != second.frequency) {
                return first.frequency < second.frequency ? -1 : 1;
            }
            return first.minimumSymbol == second.minimumSymbol ? 0
                    : (first.minimumSymbol < second.minimumSymbol ? -1 : 1);
        }
    }
}
