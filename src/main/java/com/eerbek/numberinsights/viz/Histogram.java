package com.eerbek.numberinsights.viz;

import com.eerbek.numberinsights.model.Dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bins a dataset into equal-width buckets and renders the result.
 *
 * <p>The binning logic ({@link #computeBins}) is independent of any output
 * medium: the CLI renders the bins as an ASCII bar chart via {@link #render},
 * while the web layer serialises the same bins to JSON and lets the browser
 * draw them as an SVG column chart. One computation, two front-ends.</p>
 */
public final class Histogram {

    /**
     * One histogram bucket.
     *
     * @param low   inclusive lower edge
     * @param high  upper edge (inclusive for the last bin)
     * @param count number of observations falling inside the bucket
     */
    public record Bin(double low, double high, int count) {
    }

    private final int bins;
    private final int maxBarWidth;

    /** Creates a histogram with sensible defaults (12 bins, 40-column bars). */
    public Histogram() {
        this(12, 40);
    }

    /**
     * @param bins        number of equal-width buckets (must be positive)
     * @param maxBarWidth width in characters of the longest ASCII bar (must be positive)
     */
    public Histogram(int bins, int maxBarWidth) {
        if (bins <= 0 || maxBarWidth <= 0) {
            throw new IllegalArgumentException("bins and maxBarWidth must be positive");
        }
        this.bins = bins;
        this.maxBarWidth = maxBarWidth;
    }

    /**
     * Splits the dataset's value range into {@code binCount} equal-width buckets.
     *
     * @param dataset  the data to bin; must not be empty
     * @param binCount the number of buckets (must be positive)
     * @return the buckets in ascending order; counts sum to {@code dataset.size()}
     * @throws IllegalArgumentException if the dataset is empty or {@code binCount < 1}
     */
    public static List<Bin> computeBins(Dataset dataset, int binCount) {
        if (dataset.isEmpty()) {
            throw new IllegalArgumentException("Cannot bin an empty dataset");
        }
        if (binCount <= 0) {
            throw new IllegalArgumentException("binCount must be positive");
        }

        int min = dataset.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = dataset.stream().mapToInt(Integer::intValue).max().orElseThrow();

        int[] counts = new int[binCount];
        double width = (max - min) / (double) binCount;
        for (int value : dataset.stream().mapToInt(Integer::intValue).toArray()) {
            int index = (width == 0) ? 0 : (int) ((value - min) / width);
            if (index >= binCount) {
                index = binCount - 1; // the maximum value falls into the last bin
            }
            counts[index]++;
        }

        List<Bin> result = new ArrayList<>(binCount);
        for (int i = 0; i < binCount; i++) {
            double lowEdge = min + i * width;
            double highEdge = (i == binCount - 1) ? max : min + (i + 1) * width;
            result.add(new Bin(lowEdge, highEdge, counts[i]));
        }
        return result;
    }

    /**
     * Builds the multi-line ASCII histogram string for the given dataset.
     *
     * @param dataset the data to plot; must not be empty
     * @return a printable, multi-line histogram
     * @throws IllegalArgumentException if the dataset is empty
     */
    public String render(Dataset dataset) {
        List<Bin> computed = computeBins(dataset, bins);

        int peak = computed.stream().mapToInt(Bin::count).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        for (Bin bin : computed) {
            int barLength = (peak == 0) ? 0
                    : (int) Math.round((bin.count() / (double) peak) * maxBarWidth);
            sb.append(String.format(Locale.ROOT, "%8.1f - %8.1f | %s %d%n",
                    bin.low(), bin.high(), "#".repeat(barLength), bin.count()));
        }
        return sb.toString();
    }
}
