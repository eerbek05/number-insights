package com.eerbek.numberinsights;

import com.eerbek.numberinsights.io.DataLoader;
import com.eerbek.numberinsights.model.Dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helpers for loading the bundled sample data from the classpath, so tests
 * do not depend on the current working directory.
 */
final class TestData {

    static final String INPUT_RESOURCE = "/lab13_input_data.txt";
    static final String EXPECTED_RESOURCE = "/lab13_expected_results.txt";

    private TestData() {
    }

    /** Loads the 1000-value sample dataset used throughout the tests. */
    static Dataset sampleDataset() {
        try (InputStream in = TestData.class.getResourceAsStream(INPUT_RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return DataLoader.fromLines(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses the reference results file, which stores three named blocks
     * ({@code result1}, {@code result2}, {@code result3}) separated by blank
     * lines, each preceded by a header line.
     *
     * @param index 0, 1 or 2 for result1/2/3 respectively
     */
    static Integer[] expectedResult(int index) {
        List<List<Integer>> blocks = new ArrayList<>();
        try (InputStream in = TestData.class.getResourceAsStream(EXPECTED_RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<Integer> current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.startsWith("result")) {
                    current = new ArrayList<>();
                    blocks.add(current);
                } else if (!trimmed.isEmpty() && current != null) {
                    current.add(Integer.valueOf(trimmed));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return blocks.get(index).toArray(new Integer[0]);
    }
}
