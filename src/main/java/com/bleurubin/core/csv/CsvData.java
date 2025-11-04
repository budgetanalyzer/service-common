package com.bleurubin.core.csv;

import java.util.List;
import java.util.Objects;

/**
 * Represents parsed CSV file data with metadata.
 *
 * <p>This record encapsulates the result of parsing a CSV file, including:
 *
 * <ul>
 *   <li>{@code fileName} - The original filename (for logging and error messages)
 *   <li>{@code format} - A label describing the CSV format/data type
 *   <li>{@code rows} - The parsed data rows with header-based column access
 * </ul>
 *
 * <p>The first row of the CSV file is treated as headers, and subsequent rows are converted to
 * {@link CsvRow} objects where columns can be accessed by header name.
 *
 * <p>Example usage:
 *
 * <pre>
 * CsvData csvData = csvParser.parseCsvFile(file, "transaction");
 *
 * for (CsvRow row : csvData.rows()) {
 *     String date = row.values().get("Transaction Date");
 *     String amount = row.values().get("Amount");
 *     // ... process row data
 * }
 * </pre>
 *
 * @param fileName the name of the CSV file
 * @param format a label describing the CSV format/data type (e.g., "capital-one")
 * @param rows the list of parsed data rows (empty list if file is empty)
 */
public record CsvData(String fileName, String format, List<CsvRow> rows) {
  /**
   * Canonical constructor with validation.
   *
   * <p>Ensures that fileName and format are non-null, and converts null rows to an empty list.
   *
   * @throws NullPointerException if fileName or format is null
   */
  public CsvData {
    Objects.requireNonNull(fileName, "fileName cannot be null");
    Objects.requireNonNull(format, "format cannot be null");
    rows = Objects.requireNonNullElse(rows, List.of());
  }
}
