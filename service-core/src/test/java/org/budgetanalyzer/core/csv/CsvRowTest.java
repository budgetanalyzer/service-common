package org.budgetanalyzer.core.csv;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(csvRow.lineNumber()).isEqualTo(5);
    assertThat(csvRow.values()).isEqualTo(values);
  }

  @Test
  void shouldAccessValuesByHeaderName() {
    var values = Map.of("Date", "2024-01-15", "Amount", "99.99", "Description", "Coffee");
    var csvRow = new CsvRow(2, values);

    assertThat(csvRow.values().get("Date")).isEqualTo("2024-01-15");
    assertThat(csvRow.values().get("Amount")).isEqualTo("99.99");
    assertThat(csvRow.values().get("Description")).isEqualTo("Coffee");
  }

  @Test
  void shouldReturnNullForNonExistentHeader() {
    var values = Map.of("Name", "Bob");
    var csvRow = new CsvRow(3, values);

    assertThat(csvRow.values().get("NonExistentColumn")).isNull();
  }

  @Test
  void shouldHandleEmptyValues() {
    var values = Map.<String, String>of();
    var csvRow = new CsvRow(10, values);

    assertThat(csvRow.lineNumber()).isEqualTo(10);
    assertThat(csvRow.values()).isEmpty();
  }

  @Test
  void shouldPreserveLineNumber() {
    var values = Map.of("Column", "Value");
    var csvRow = new CsvRow(42, values);

    assertThat(csvRow.lineNumber()).isEqualTo(42);
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

    assertThat(csvRow.values()).hasSize(5);
    assertThat(csvRow.values().get("Col1")).isEqualTo("Value1");
    assertThat(csvRow.values().get("Col3")).isEqualTo("Value3");
    assertThat(csvRow.values().get("Col5")).isEqualTo("Value5");
  }
}
