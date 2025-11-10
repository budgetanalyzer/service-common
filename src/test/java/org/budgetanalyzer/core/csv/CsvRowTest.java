package org.budgetanalyzer.core.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CsvRow}.
 *
 * <p>Tests the record's basic functionality and value access.
 */
class CsvRowTest {

  @Test
  void shouldConstructWithLineNumberAndValues() {
    var values = Map.of("Name", "Alice", "Age", "25", "City", "Boston");
    var csvRow = new CsvRow(5, values);

    assertEquals(5, csvRow.lineNumber());
    assertEquals(values, csvRow.values());
  }

  @Test
  void shouldAccessValuesByHeaderName() {
    var values = Map.of("Date", "2024-01-15", "Amount", "99.99", "Description", "Coffee");
    var csvRow = new CsvRow(2, values);

    assertEquals("2024-01-15", csvRow.values().get("Date"));
    assertEquals("99.99", csvRow.values().get("Amount"));
    assertEquals("Coffee", csvRow.values().get("Description"));
  }

  @Test
  void shouldReturnNullForNonExistentHeader() {
    var values = Map.of("Name", "Bob");
    var csvRow = new CsvRow(3, values);

    assertNull(csvRow.values().get("NonExistentColumn"));
  }

  @Test
  void shouldHandleEmptyValues() {
    var values = Map.<String, String>of();
    var csvRow = new CsvRow(10, values);

    assertEquals(10, csvRow.lineNumber());
    assertEquals(0, csvRow.values().size());
  }

  @Test
  void shouldPreserveLineNumber() {
    var values = Map.of("Column", "Value");
    var csvRow = new CsvRow(42, values);

    assertEquals(42, csvRow.lineNumber());
  }

  @Test
  void shouldHandleMultipleColumns() {
    var values =
        Map.of(
            "Col1", "Value1",
            "Col2", "Value2",
            "Col3", "Value3",
            "Col4", "Value4",
            "Col5", "Value5");
    var csvRow = new CsvRow(7, values);

    assertEquals(5, csvRow.values().size());
    assertEquals("Value1", csvRow.values().get("Col1"));
    assertEquals("Value3", csvRow.values().get("Col3"));
    assertEquals("Value5", csvRow.values().get("Col5"));
  }
}
