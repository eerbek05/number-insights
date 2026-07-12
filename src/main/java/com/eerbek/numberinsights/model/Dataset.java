package com.eerbek.numberinsights.model;

import java.util.List;
import java.util.stream.Stream;

/**
 * An immutable, ordered collection of integer observations.
 *
 * <p>{@code Dataset} is the central data model of the application. It is created
 * once from a source (a file, a stream, or an in-memory list) and then handed to
 * the various analysis components. Because it is immutable, it can be safely
 * shared between the statistics, analysis and visualization layers without any
 * risk of one mutating the data seen by another.</p>
 */
public final class Dataset {

    private final List<Integer> values;

    private Dataset(List<Integer> values) {
        // Defensive copy + unmodifiable wrapper guarantees immutability.
        this.values = List.copyOf(values);
    }

    /**
     * Creates a dataset from the given values.
     *
     * @param values the observations; must not be {@code null}
     * @return a new immutable dataset
     * @throws NullPointerException if {@code values} or any element is {@code null}
     */
    public static Dataset of(List<Integer> values) {
        return new Dataset(values);
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
    public List<Integer> values() {
        return values;
    }

    /**
     * @return a fresh sequential {@link Stream} over the values, in original order
     */
    public Stream<Integer> stream() {
        return values.stream();
    }

    @Override
    public String toString() {
        return "Dataset(size=" + values.size() + ")";
    }
}
