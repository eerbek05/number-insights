package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.cli.CliOptions;
import com.eerbek.numberinsights.report.ReportFormatter.Format;

import org.junit.jupiter.api.Test;

class CliOptionsTest {

    @Test
    void statsIsTheDefaultView() {
        CliOptions options = CliOptions.parse(new String[] {"data.txt"});
        assertTrue(options.showStats());
        assertFalse(options.showHistogram());
        assertEquals("data.txt", options.inputFile().toString());
        assertEquals(Format.TABLE, options.format());
    }

    @Test
    void parsesBothViewFlags() {
        CliOptions options = CliOptions.parse(new String[] {"data.txt", "--stats", "--histogram"});
        assertTrue(options.showStats());
        assertTrue(options.showHistogram());
    }

    @Test
    void parsesJsonFormat() {
        CliOptions options = CliOptions.parse(new String[] {"data.txt", "--format", "json"});
        assertEquals(Format.JSON, options.format());
    }

    @Test
    void helpFlagIsRecognised() {
        assertTrue(CliOptions.parse(new String[] {"--help"}).isHelpRequested());
        assertTrue(CliOptions.parse(new String[] {"-h"}).isHelpRequested());
    }

    @Test
    void unknownOptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"data.txt", "--nope"}));
    }

    @Test
    void formatWithoutValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"data.txt", "--format"}));
    }

    @Test
    void invalidFormatValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"data.txt", "--format", "xml"}));
    }

    @Test
    void serveFlagWithDefaultPort() {
        CliOptions options = CliOptions.parse(new String[] {"--serve"});
        assertTrue(options.serve());
        assertEquals(CliOptions.DEFAULT_PORT, options.port());
        assertFalse(options.showStats()); // serve mode does not force the stats view
    }

    @Test
    void parsesCustomPort() {
        CliOptions options = CliOptions.parse(new String[] {"--serve", "--port", "9000"});
        assertEquals(9000, options.port());
    }

    @Test
    void invalidPortIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--serve", "--port", "abc"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--serve", "--port", "70000"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--serve", "--port"}));
    }

    @Test
    void extraPositionalArgumentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"a.txt", "b.txt"}));
    }
}
