package com.eerbek.numberinsights;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eerbek.numberinsights.model.Dataset;
import com.eerbek.numberinsights.report.ReportFormatter;
import com.eerbek.numberinsights.stats.DescriptiveStatistics;
import com.eerbek.numberinsights.stats.StatisticsResult;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    private StatisticsResult sample() {
        return new DescriptiveStatistics(Dataset.of(List.of(2, 4, 4, 4, 5, 5, 7, 9))).summary();
    }

    @Test
    void tableContainsLabelsAndValues() {
        String out = new ReportFormatter(ReportFormatter.Format.TABLE).format(sample());
        assertTrue(out.contains("Mean"));
        assertTrue(out.contains("Std Dev"));
        assertTrue(out.contains("Median"));
    }

    @Test
    void jsonContainsExpectedKeys() {
        String out = new ReportFormatter(ReportFormatter.Format.JSON).format(sample());
        assertTrue(out.contains("\"count\": 8"));
        assertTrue(out.contains("\"mean\":"));
        assertTrue(out.contains("\"stdDev\":"));
        assertTrue(out.trim().startsWith("{"));
        assertTrue(out.trim().endsWith("}"));
    }
}
