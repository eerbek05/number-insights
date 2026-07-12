package com.eerbek.numberinsights.viz;

import com.eerbek.numberinsights.model.Dataset;

import java.util.Locale;

/**
 * Renders a dataset as a text-based (ASCII) histogram.
 *
 * <p>The value range is divided into a fixed number of equal-width bins and each
 * bin is drawn as a horizontal bar scaled to the terminal width. This gives an
 * at-a-glance view of the distribution's shape (skew, modality, spread) directly
 * in the console — no GUI or plotting library required.</p>
 */
public final class Histogram {

    private final int bins;
    private final int maxBarWidth;

    /** Creates a histogram with sensible defaults (12 bins, 40-column bars). */
    public Histogram() {
        this(12, 40);
    }

    /**
     * @param bins        number of equal-width buckets (must be positive)
     * @param maxBarWidth width in characters of the longest bar (must be positive)
     */
    public Histogram(int bins, int maxBarWidth) {
        if (bins <= 0 || maxBarWidth <= 0) {
            throw new IllegalArgumentException("bins and maxBarWidth must be positive");
        }
        this.bins = bins;
        this.maxBarWidth = maxBarWidth;
    }

    /**
     * Builds the multi-line histogram string for the given dataset.
     *
     * @param dataset the data to plot; must not be empty
     * @return a printable, multi-line histogram
     * @throws IllegalArgumentException if the dataset is empty
     */
    public String render(Dataset dataset) {
        if (dataset.isEmpty()) {
            throw new IllegalArgumentException("Cannot render a histogram for an empty dataset");
        }

        int min = dataset.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = dataset.stream().mapToInt(Integer::intValue).max().orElseThrow();

        int[] counts = new int[bins];
        double width = (max - min) / (double) bins;
        for (int value : dataset.stream().mapToInt(Integer::intValue).toArray()) {
            int index = (width == 0) ? 0 : (int) ((value - min) / width);
            if (index >= bins) {
                index = bins - 1; // the maximum value falls into the last bin
            }
            counts[index]++;
        }

        int peak = 0;
        for (int c : counts) {
            peak = Math.max(peak, c);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bins; i++) {
            double lowEdge = min + i * width;
            double highEdge = (i == bins - 1) ? max : min + (i + 1) * width;
            int barLength = (peak == 0) ? 0 : (int) Math.round((counts[i] / (double) peak) * maxBarWidth);
            sb.append(String.format(Locale.ROOT, "%8.1f - %8.1f | %s %d%n",
                    lowEdge, highEdge, "#".repeat(barLength), counts[i]));
        }
        return sb.toString();
    }
}
