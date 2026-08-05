package edu.academic.integrity.algorithms.benchmark;

import edu.academic.integrity.algorithms.sort.IntegerSorts;

/** Deterministic timing harness for the five required integer sorting algorithms. */
public final class AlgorithmBenchmark {
    private static final int ALGORITHM_COUNT = 5;
    private static volatile long checksumSink;

    private AlgorithmBenchmark() {
    }

    /**
     * Times all algorithms on independent copies of exactly the same input.
     * Array copying and validation are deliberately outside the timed region.
     */
    public static BenchmarkResult[] benchmarkAll(int[] input, int repetitions) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        if (repetitions < 1) {
            throw new IllegalArgumentException("Repetitions must be positive");
        }
        BenchmarkResult[] results = new BenchmarkResult[ALGORITHM_COUNT];
        for (int algorithm = 0; algorithm < ALGORITHM_COUNT; algorithm++) {
            results[algorithm] = benchmarkOne(input, repetitions, algorithm);
        }
        return results;
    }

    /** Generates plagiarism-style integer scores in the inclusive range 0..10000. */
    public static BenchmarkResult[] benchmarkGenerated(int size, int repetitions, long seed) {
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        return benchmarkAll(generateValues(size, 10_000, seed), repetitions);
    }

    /** Returns five result rows for every requested input size. */
    public static BenchmarkResult[] benchmarkSizes(int[] sizes, int repetitions, long seed) {
        if (sizes == null) {
            throw new IllegalArgumentException("Sizes cannot be null");
        }
        BenchmarkResult[] results = new BenchmarkResult[sizes.length * ALGORITHM_COUNT];
        int output = 0;
        long state = seed;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] < 0) {
                throw new IllegalArgumentException("Benchmark size cannot be negative");
            }
            BenchmarkResult[] oneSize = benchmarkGenerated(sizes[i], repetitions, state);
            for (int algorithm = 0; algorithm < oneSize.length; algorithm++) {
                results[output++] = oneSize[algorithm];
            }
            state = nextRandom(state);
        }
        return results;
    }

    public static int[] generateValues(int size, int maximumInclusive, long seed) {
        if (size < 0 || maximumInclusive < 0) {
            throw new IllegalArgumentException("Size and maximum must be non-negative");
        }
        int[] result = new int[size];
        long range = (long) maximumInclusive + 1L;
        long state = seed == 0L ? 0x9e3779b97f4a7c15L : seed;
        for (int i = 0; i < size; i++) {
            state = nextRandom(state);
            result[i] = (int) ((state & Long.MAX_VALUE) % range);
        }
        return result;
    }

    private static BenchmarkResult benchmarkOne(int[] input, int repetitions, int algorithm) {
        String name = algorithmName(algorithm);
        long total = 0L;
        long minimum = Long.MAX_VALUE;
        long maximum = 0L;
        long expectedChecksum = 0L;
        try {
            for (int run = 0; run < repetitions; run++) {
                int[] values = copy(input);
                long start = System.nanoTime();
                sort(values, algorithm);
                long elapsed = System.nanoTime() - start;
                if (!IntegerSorts.isSorted(values)) {
                    return BenchmarkResult.failure(name, input.length, repetitions,
                            "Algorithm produced unsorted output");
                }
                long checksum = checksum(values);
                if (run == 0) {
                    expectedChecksum = checksum;
                } else if (checksum != expectedChecksum) {
                    return BenchmarkResult.failure(name, input.length, repetitions,
                            "Non-deterministic output checksum");
                }
                checksumSink ^= checksum;
                if (Long.MAX_VALUE - total < elapsed) {
                    throw new ArithmeticException("Benchmark duration overflow");
                }
                total += elapsed;
                if (elapsed < minimum) {
                    minimum = elapsed;
                }
                if (elapsed > maximum) {
                    maximum = elapsed;
                }
            }
            return BenchmarkResult.success(name, input.length, repetitions, total,
                    minimum == Long.MAX_VALUE ? 0L : minimum, maximum, expectedChecksum);
        } catch (RuntimeException exception) {
            return BenchmarkResult.failure(name, input.length, repetitions,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static void sort(int[] values, int algorithm) {
        switch (algorithm) {
            case 0:
                IntegerSorts.mergeSort(values);
                break;
            case 1:
                IntegerSorts.quickSort(values);
                break;
            case 2:
                IntegerSorts.heapSort(values);
                break;
            case 3:
                IntegerSorts.countingSort(values);
                break;
            case 4:
                IntegerSorts.radixSort(values);
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm index");
        }
    }

    private static String algorithmName(int algorithm) {
        switch (algorithm) {
            case 0:
                return "Merge sort";
            case 1:
                return "Quick sort";
            case 2:
                return "Heap sort";
            case 3:
                return "Counting sort";
            case 4:
                return "Radix sort";
            default:
                throw new IllegalArgumentException("Unknown algorithm index");
        }
    }

    private static int[] copy(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    private static long checksum(int[] values) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < values.length; i++) {
            hash ^= values[i];
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long nextRandom(long state) {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        return state;
    }
}
