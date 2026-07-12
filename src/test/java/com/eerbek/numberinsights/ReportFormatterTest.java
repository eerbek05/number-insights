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
        assertTrue(out.contains("\"skewness\":"));
        assertTrue(out.contains("\"excessKurtosis\":"));
        assertTrue(out.contains("\"ci95Low\":"));
        assertTrue(out.contains("\"outlierCount\":"));
        assertTrue(out.trim().startsWith("{"));
        assertTrue(out.trim().endsWith("}"));
    }

    @Test
    void undefinedMeasuresRenderAsNullInJsonAndNaInTable() {
        // A single value: sample variance, skewness, kurtosis are all undefined
        StatisticsResult single = new DescriptiveStatistics(
                com.eerbek.numberinsights.model.Dataset.of(List.of(42))).summary();

        String json = new ReportFormatter(ReportFormatter.Format.JSON).format(single);
        assertTrue(json.contains("\"skewness\": null"));
        assertTrue(json.contains("\"sampleVariance\": null"));

        String table = new ReportFormatter(ReportFormatter.Format.TABLE).format(single);
        assertTrue(table.contains("n/a"));
    }
}
