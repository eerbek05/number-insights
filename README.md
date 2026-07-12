# Number Insights

[![CI](https://github.com/eerbek05/lab13/actions/workflows/ci.yml/badge.svg)](https://github.com/eerbek05/lab13/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Build](https://img.shields.io/badge/build-Maven-blue.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A command-line **statistical analysis tool** for numeric datasets, written in modern Java.
Point it at a `.txt` or `.csv` file and it computes a full set of descriptive
statistics and draws the distribution as an ASCII histogram — no GUI, no external
dependencies.

> Number Insights grew out of a university stream-processing exercise ("Lab 13").
> The original coursework — a set of Java `Stream` pipelines over a 1000-value
> dataset — lives on inside the `analysis` layer, and its exact results are still
> pinned by regression tests. Everything else was built around it to turn a single
> homework file into a small, well-structured application.

---

## Features

- **Descriptive statistics** — count, sum, min, max, range, mean, median, mode(s),
  variance, standard deviation, quartiles (Q1/Q3) and inter-quartile range.
- **ASCII histogram** — see the shape of the distribution (skew, spread, modality)
  right in your terminal.
- **Flexible input** — one-number-per-line text files *or* delimited data
  (comma / semicolon / tab / space), so plain `.txt` and `.csv` both work.
  Blank lines and `#` comments are ignored.
- **Multiple output formats** — human-readable `table` or machine-readable `json`.
- **Robust CLI** — clear error messages, `--help`, and proper exit codes.
- **Zero runtime dependencies** — pure JDK; JUnit is used for tests only.

## Quick start

```bash
# Build a runnable JAR (tests run automatically)
mvn package

# Analyse a dataset
java -jar target/number-insights.jar sample-data/numbers.txt --stats --histogram
```

### Example output

```
Loaded 1000 values from sample-data/numbers.txt

Descriptive Statistics
======================
Count        : 1000
Sum          : 50191
Min          : 0
Max          : 99
Range        : 99
Mean         : 50.1910
Median       : 51.0000
Mode         : 71
Variance     : 842.6305
Std Dev      : 29.0281
Q1 (25%)     : 25.0000
Q3 (75%)     : 75.0000
IQR          : 50.0000

Distribution
============
     0.0 -      8.3 | ##################################### 97
     8.3 -     16.5 | ############################# 76
    16.5 -     24.8 | ########################### 70
    24.8 -     33.0 | ############################ 72
    33.0 -     41.3 | #################################### 94
    41.3 -     49.5 | ############################## 79
    49.5 -     57.8 | ############################# 75
    57.8 -     66.0 | ############################## 77
    66.0 -     74.3 | ######################################## 104
    74.3 -     82.5 | ############################## 79
    82.5 -     90.8 | ################################# 86
    90.8 -     99.0 | ################################### 91
```

## Usage

```
number-insights <file> [options]

OPTIONS:
  --stats           Print descriptive statistics (default)
  --histogram       Print an ASCII histogram of the distribution
  --format <type>   Statistics output format: table (default) or json
  -h, --help        Show help

EXAMPLES:
  number-insights data.txt
  number-insights data.csv --stats --histogram
  number-insights data.txt --format json
```

JSON output pipes cleanly into other tools:

```bash
java -jar target/number-insights.jar sample-data/numbers.txt --format json
```
```json
{
  "count": 1000,
  "sum": 50191,
  "mean": 50.1910,
  "median": 51.0000,
  "modes": [71],
  "stdDev": 29.0281,
  "q1": 25.0000,
  "q3": 75.0000,
  "iqr": 50.0000
}
```

## Architecture

The application is organised into small, single-responsibility layers, with data
flowing in one direction:

```
CLI (Main) → DataLoader → Dataset → { StreamAnalyzer, DescriptiveStatistics, Histogram } → ReportFormatter → stdout
```

| Package    | Responsibility                                                        |
|------------|-----------------------------------------------------------------------|
| `cli`      | Parse command-line arguments (`CliOptions`)                           |
| `io`       | Read and parse text/CSV input (`DataLoader`)                          |
| `model`    | The immutable `Dataset` shared by every analyzer                     |
| `stats`    | Descriptive statistics (`DescriptiveStatistics`, `StatisticsResult`) |
| `analysis` | The original Lab 13 stream pipelines (`StreamAnalyzer`)              |
| `viz`      | ASCII histogram rendering (`Histogram`)                              |
| `report`   | Table / JSON formatting (`ReportFormatter`)                          |

Because each layer depends only on the one beneath it and the model is immutable,
every component is independently unit-testable — see `src/test`.

## Project layout

```
number-insights/
├── pom.xml                     # Maven build (JUnit 5, shade fat-jar)
├── .github/workflows/ci.yml    # GitHub Actions: build + test on every push
├── sample-data/                # Example .txt and .csv inputs
├── src/main/java/...           # Application sources
└── src/test/java/...           # 36 JUnit 5 tests
```

## Development

```bash
mvn test        # run the test suite (36 tests)
mvn package     # run tests and build target/number-insights.jar
mvn verify      # full build used by CI
```

### Testing

The suite covers every layer: statistics correctness (against textbook values),
input parsing (delimiters, comments, error reporting), histogram binning, CLI
argument handling, and the original coursework regression tests that pin the exact
stream-pipeline results.

## Tech stack

- **Java 21** (records, switch expressions, text blocks, streams)
- **Maven** for build & dependency management
- **JUnit 5** for testing
- **GitHub Actions** for continuous integration

## License

Released under the [MIT License](LICENSE).
