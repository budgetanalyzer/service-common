package org.budgetanalyzer.core.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

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
    var row = new CsvRow(2, Map.of("Name", "John", "Age", "30", "City", "NYC"));
    var rows = List.of(row);

    var csvData = new CsvData("test.csv", "user-data", headers, rows);

    assertThat(csvData.fileName()).isEqualTo("test.csv");
    assertThat(csvData.format()).isEqualTo("user-data");
    assertThat(csvData.headers()).isEqualTo(headers);
    assertThat(csvData.rows()).isEqualTo(rows);
  }

  @Test
  void shouldThrowNullPointerExceptionWhenFileNameIsNull() {
    var headers = List.of("Name");
    var rows = List.<CsvRow>of();

    assertThatThrownBy(() -> new CsvData(null, "format", headers, rows))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("fileName cannot be null");
  }

  @Test
  void shouldThrowNullPointerExceptionWhenFormatIsNull() {
    var headers = List.of("Name");
    var rows = List.<CsvRow>of();

    assertThatThrownBy(() -> new CsvData("test.csv", null, headers, rows))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("format cannot be null");
  }

  @Test
  void shouldConvertNullHeadersToEmptyList() {
    var csvData = new CsvData("test.csv", "format", null, List.of());

    assertThat(csvData.headers()).isNotNull().isEmpty();
  }

  @Test
  void shouldConvertNullRowsToEmptyList() {
    var csvData = new CsvData("test.csv", "format", List.of(), null);

    assertThat(csvData.rows()).isNotNull().isEmpty();
  }

  @Test
  void shouldHandleEmptyHeadersAndRows() {
    var csvData = new CsvData("empty.csv", "empty-format", List.of(), List.of());

    assertThat(csvData.fileName()).isEqualTo("empty.csv");
    assertThat(csvData.format()).isEqualTo("empty-format");
    assertThat(csvData.headers()).isEmpty();
    assertThat(csvData.rows()).isEmpty();
  }

  @Test
  void shouldHandleNullHeadersAndNullRows() {
    var csvData = new CsvData("empty.csv", "empty-format", null, null);

    assertThat(csvData.fileName()).isEqualTo("empty.csv");
    assertThat(csvData.format()).isEqualTo("empty-format");
    assertThat(csvData.headers()).isNotNull().isEmpty();
    assertThat(csvData.rows()).isNotNull().isEmpty();
  }

  @Test
  void shouldReturnCorrectHeadersAfterConstruction() {
    var expectedHeaders = List.of("Column1", "Column2", "Column3");
    var csvData = new CsvData("data.csv", "test", expectedHeaders, List.of());

    var actualHeaders = csvData.headers();

    assertThat(actualHeaders).containsExactly("Column1", "Column2", "Column3");
  }
}
