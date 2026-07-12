package com.eerbek.numberinsights.analysis;

import com.eerbek.numberinsights.model.Dataset;

/**
 * Stream-based queries over a {@link Dataset}.
 *
 * <p>This class preserves the original "Lab 13" exercise that the project grew
 * out of: a set of {@link java.util.stream.Stream} pipelines demonstrating
 * filtering, mapping, sorting, {@code limit}, {@code skip}, {@code distinct} and
 * {@code count}. The logic is byte-for-byte equivalent to the coursework version
 * — the accompanying regression tests still assert the exact same results — but
 * it now operates on the shared immutable {@code Dataset} model instead of owning
 * its own {@code ArrayList}.</p>
 */
public final class StreamAnalyzer {

    private final Dataset dataset;

    public StreamAnalyzer(Dataset dataset) {
        this.dataset = dataset;
    }

    /** @return the total number of observations */
    public long totalCount() {
        return dataset.stream().count();
    }

    /** @return how many observations are odd */
    public long oddCount() {
        return dataset.stream().filter(x -> x % 2 != 0).count();
    }

    /** @return how many observations are even */
    public long evenCount() {
        return dataset.stream().filter(x -> x % 2 == 0).count();
    }

    /** @return the number of distinct observations strictly greater than five */
    public long distinctGreaterThanFiveCount() {
        return dataset.stream().filter(x -> x > 5).distinct().count();
    }

    /** @return even values in {@code (5, 50)}, ascending. */
    public Integer[] evenBetweenFiveAndFiftySorted() {
        return dataset.stream()
                .filter(x -> x > 5 && x < 50 && x % 2 == 0)
                .sorted()
                .toArray(Integer[]::new);
    }

    /** @return the first fifty values, each transformed to {@code 3 * x^2}. */
    public Integer[] firstFiftySquaredTimesThree() {
        return dataset.stream()
                .map(x -> x * x * 3)
                .limit(50)
                .toArray(Integer[]::new);
    }

    /**
     * @return odd values doubled and sorted, then with the twenty smallest
     *         dropped and duplicates removed.
     */
    public Integer[] oddDoubledSortedSkippedDistinct() {
        return dataset.stream()
                .filter(x -> x % 2 != 0)
                .map(x -> x * 2)
                .sorted()
                .skip(20)
                .distinct()
                .toArray(Integer[]::new);
    }
}
