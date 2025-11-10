package org.budgetanalyzer.core.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CsvData}.
 *
 * <p>Tests the record's validation logic, null handling, and accessor methods.
 */
class CsvDataTest {

  @Test
  void shouldConstructWithAllValidFields() {
    var headers = List.of("Name", "Age", "City");
    var row = new CsvRow(2, java.util.Map.of("Name", "John", "Age", "30", "City", "NYC"));
    var rows = List.of(row);

    var csvData = new CsvData("test.csv", "user-data", headers, rows);

    assertEquals("test.csv", csvData.fileName());
    assertEquals("user-data", csvData.format());
    assertEquals(headers, csvData.headers());
    assertEquals(rows, csvData.rows());
  }

  @Test
  void shouldThrowNullPointerExceptionWhenFileNameIsNull() {
    var headers = List.of("Name");
    var rows = List.<CsvRow>of();

    var exception =
        assertThrows(NullPointerException.class, () -> new CsvData(null, "format", headers, rows));

    assertEquals("fileName cannot be null", exception.getMessage());
  }

  @Test
  void shouldThrowNullPointerExceptionWhenFormatIsNull() {
    var headers = List.of("Name");
    var rows = List.<CsvRow>of();

    var exception =
        assertThrows(
            NullPointerException.class, () -> new CsvData("test.csv", null, headers, rows));

    assertEquals("format cannot be null", exception.getMessage());
  }

  @Test
  void shouldConvertNullHeadersToEmptyList() {
    var csvData = new CsvData("test.csv", "format", null, List.of());

    assertNotNull(csvData.headers());
    assertTrue(csvData.headers().isEmpty());
  }

  @Test
  void shouldConvertNullRowsToEmptyList() {
    var csvData = new CsvData("test.csv", "format", List.of(), null);

    assertNotNull(csvData.rows());
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldHandleEmptyHeadersAndRows() {
    var csvData = new CsvData("empty.csv", "empty-format", List.of(), List.of());

    assertEquals("empty.csv", csvData.fileName());
    assertEquals("empty-format", csvData.format());
    assertTrue(csvData.headers().isEmpty());
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldHandleNullHeadersAndNullRows() {
    var csvData = new CsvData("empty.csv", "empty-format", null, null);

    assertEquals("empty.csv", csvData.fileName());
    assertEquals("empty-format", csvData.format());
    assertNotNull(csvData.headers());
    assertNotNull(csvData.rows());
    assertTrue(csvData.headers().isEmpty());
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldReturnCorrectHeadersAfterConstruction() {
    var expectedHeaders = List.of("Column1", "Column2", "Column3");
    var csvData = new CsvData("data.csv", "test", expectedHeaders, List.of());

    var actualHeaders = csvData.headers();

    assertEquals(3, actualHeaders.size());
    assertEquals("Column1", actualHeaders.get(0));
    assertEquals("Column2", actualHeaders.get(1));
    assertEquals("Column3", actualHeaders.get(2));
  }
}
