package com.eerbek.numberinsights.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * An immutable, ordered collection of numeric observations.
 *
 * <p>{@code Dataset} is the central data model of the application. Values are
 * stored as {@code double}s so real-world data (measurements, prices, rates)
 * works as naturally as the whole numbers the project started with. It is
 * created once from a source (a file, a request body, or an in-memory list)
 * and then handed to the analysis components; because it is immutable it can
 * be safely shared between the statistics, inference and visualization layers.</p>
 */
public final class Dataset {

    private final List<Double> values;

    private Dataset(List<Double> values) {
        this.values = Collections.unmodifiableList(values);
    }

    /**
     * Creates a dataset from the given values. Accepts any {@link Number}
     * subtype so integer and decimal sources both work.
     *
     * @param values the observations; must not be {@code null}
     * @return a new immutable dataset
     * @throws NullPointerException     if {@code values} or any element is {@code null}
     * @throws IllegalArgumentException if a value is NaN or infinite
     */
    public static Dataset of(List<? extends Number> values) {
        List<Double> copy = new ArrayList<>(values.size());
        for (Number n : values) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Dataset values must be finite numbers");
            }
            copy.add(d);
        }
        return new Dataset(copy);
    }

    /** @return the number of observations in this dataset */
    public int size() {
        return values.size();
    }

    /** @return {@code true} if this dataset contains no observations */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** @return an unmodifiable view of the raw values in their original order */
    public List<Double> values() {
        return values;
    }

    /** @return a fresh sequential {@link Stream} over the values, in original order */
    public Stream<Double> stream() {
        return values.stream();
    }

    /** @return a fresh primitive {@link DoubleStream} over the values */
    public DoubleStream doubleStream() {
        return values.stream().mapToDouble(Double::doubleValue);
    }

    /**
     * @return a stream view of the values truncated to {@code int}s — the
     *         compatibility bridge for the original integer-based Lab 13
     *         pipelines in {@code StreamAnalyzer}
     */
    public Stream<Integer> intStream() {
        return values.stream().map(d -> (int) (double) d);
    }

    @Override
    public String toString() {
        return "Dataset(size=" + values.size() + ")";
    }
}
