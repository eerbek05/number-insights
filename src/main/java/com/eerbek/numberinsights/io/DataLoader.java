package com.eerbek.numberinsights.io;

import com.eerbek.numberinsights.model.Dataset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads a {@link Dataset} from a text or CSV source.
 *
 * <p>This is the modernised successor of the original {@code lab13.readData}
 * method. Where the lab version read one integer per line and silently swallowed
 * every error, this loader:</p>
 * <ul>
 *     <li>accepts both newline-separated and delimiter-separated input
 *         (spaces, commas, tabs, semicolons), so plain {@code .txt} and
 *         {@code .csv} files both work;</li>
 *     <li>skips blank lines and {@code #} comment lines;</li>
 *     <li>reports the offending token and line number instead of hiding
 *         parse failures.</li>
 * </ul>
 */
public final class DataLoader {

    /** Splits a line on commas, semicolons, tabs or runs of whitespace. */
    private static final Pattern DELIMITER = Pattern.compile("[,;\\t ]+");

    private DataLoader() {
        // Utility class; not instantiable.
    }

    /**
     * Loads a dataset from the file at the given path.
     *
     * @param path the file to read
     * @return the parsed dataset
     * @throws UncheckedIOException     if the file cannot be read
     * @throws IllegalArgumentException if a token cannot be parsed as an integer
     */
    public static Dataset fromFile(Path path) {
        try {
            return fromLines(Files.readAllLines(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read data file: " + path, e);
        }
    }

    /**
     * Parses a dataset from already-read lines of text. Exposed separately so it
     * can be unit-tested without touching the file system.
     *
     * @param lines the raw lines of the source
     * @return the parsed dataset
     * @throws IllegalArgumentException if a token cannot be parsed as an integer
     */
    public static Dataset fromLines(List<String> lines) {
        List<Integer> values = new ArrayList<>();
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String token : DELIMITER.split(trimmed)) {
                if (token.isEmpty()) {
                    continue;
                }
                try {
                    values.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid integer '" + token + "' on line " + lineNumber, e);
                }
            }
        }
        return Dataset.of(values);
    }
}
