package com.bleurubin.core.csv;

import java.util.Map;

/**
 * Represents a single row from a parsed CSV file.
 *
 * <p>Each row contains:
 *
 * <ul>
 *   <li>{@code lineNumber} - The line number in the original CSV file
 *   <li>{@code values} - A map of column headers to cell values for this row
 * </ul>
 *
 * <p>The {@code values} map allows header-based column access instead of index-based access, making
 * the code more readable and less fragile to column reordering.
 *
 * <p>Example usage:
 *
 * <pre>
 * CsvRow row = csvData.rows().get(0);
 *
 * int lineNum = row.lineNumber();  // 2 (first data row)
 * String date = row.values().get("Transaction Date");
 * String amount = row.values().get("Amount");
 * String description = row.values().get("Description");
 * </pre>
 *
 * @param lineNumber the row number in the original CSV file
 * @param values a map of column headers to cell values for this row
 */
public record CsvRow(int lineNumber, Map<String, String> values) {}
