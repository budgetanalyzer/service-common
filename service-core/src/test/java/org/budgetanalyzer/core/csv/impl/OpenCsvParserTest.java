package org.budgetanalyzer.core.csv.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OpenCsvParser}.
 *
 * <p>Tests CSV parsing functionality including header extraction, row parsing, whitespace handling,
 * and edge cases.
 */
class OpenCsvParserTest {

  private OpenCsvParser openCsvParser;

  @BeforeEach
  void setUp() {
    openCsvParser = new OpenCsvParser();
  }

  @Test
  void shouldParseValidCsvWithHeaders() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.fileName()).isEqualTo("test.csv");
    assertThat(csvData.format()).isEqualTo("user-data");
    assertThat(csvData.headers()).containsExactly("Name", "Age", "City");
    assertThat(csvData.rows()).hasSize(2);
  }

  @Test
  void shouldExtractHeadersCorrectly() throws IOException {
    var csvContent = "Transaction Date,Amount,Description,Category\n2024-01-15,99.99,Coffee,Food";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "transactions.csv", "transaction");

    assertThat(csvData.headers())
        .containsExactly("Transaction Date", "Amount", "Description", "Category");
  }

  @Test
  void shouldMapHeadersToRowValues() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEqualTo("30");
    assertThat(firstRow.values().get("City")).isEqualTo("NYC");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Age")).isEqualTo("25");
    assertThat(secondRow.values().get("City")).isEqualTo("Boston");
  }

  @Test
  void shouldHandleEmptyCsvFile() throws IOException {
    var csvContent = "";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "empty.csv", "empty");

    assertThat(csvData.fileName()).isEqualTo("empty.csv");
    assertThat(csvData.format()).isEqualTo("empty");
    assertThat(csvData.headers()).isEmpty();
    assertThat(csvData.rows()).isEmpty();
  }

  @Test
  void shouldHandleCsvWithOnlyHeaders() throws IOException {
    var csvContent = "Name,Age,City";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "headers-only.csv", "test");

    assertThat(csvData.headers()).containsExactly("Name", "Age", "City");
    assertThat(csvData.rows()).isEmpty();
  }

  @Test
  void shouldTrimWhitespaceInHeaders() throws IOException {
    var csvContent = "  Name  ,  Age  ,  City  \nJohn,30,NYC";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.headers()).containsExactly("Name", "Age", "City");
  }

  @Test
  void shouldTrimWhitespaceInValues() throws IOException {
    var csvContent = "Name,Age,City\n  John  ,  30  ,  NYC  ";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEqualTo("30");
    assertThat(firstRow.values().get("City")).isEqualTo("NYC");
  }

  @Test
  void shouldSkipEmptyHeaderColumns() throws IOException {
    var csvContent = "Name,,Age,City\nJohn,ignored,30,NYC";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEqualTo("30");
    assertThat(firstRow.values().get("City")).isEqualTo("NYC");
    // Empty header column should be skipped, so no mapping for "ignored" value
    assertThat(firstRow.values()).hasSize(3);
  }

  @Test
  void shouldAssignCorrectLineNumbers() throws IOException {
    var csvContent = "Name,Age\nJohn,30\nAlice,25\nBob,35";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows().get(0).lineNumber()).isEqualTo(2); // First data row is line 2
    assertThat(csvData.rows().get(1).lineNumber()).isEqualTo(3); // Second data row is line 3
    assertThat(csvData.rows().get(2).lineNumber()).isEqualTo(4); // Third data row is line 4
  }

  @Test
  void shouldHandleQuotedValues() throws IOException {
    var csvContent = "Name,Description\nJohn,\"Smith, Jr.\"\nAlice,\"O'Brien\"";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Description")).isEqualTo("Smith, Jr.");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Description")).isEqualTo("O'Brien");
  }

  @Test
  void shouldHandleLargeCsvFile() throws IOException {
    var csvBuilder = new StringBuilder("ID,Name,Email\n");
    for (int i = 1; i <= 1000; i++) {
      csvBuilder
          .append(i)
          .append(",User")
          .append(i)
          .append(",user")
          .append(i)
          .append("@example.com\n");
    }
    var inputStream = createInputStream(csvBuilder.toString());

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "large.csv", "user-data");

    assertThat(csvData.headers()).hasSize(3);
    assertThat(csvData.rows()).hasSize(1000);
    assertThat(csvData.rows().get(0).values().get("Name")).isEqualTo("User1");
    assertThat(csvData.rows().get(499).values().get("Email")).isEqualTo("user500@example.com");
    assertThat(csvData.rows().get(999).values().get("Name")).isEqualTo("User1000");
  }

  @Test
  void shouldHandleMultipleRowsWithSameStructure() throws IOException {
    var csvContent =
        "Date,Amount,Description\n"
            + "2024-01-15,99.99,Coffee\n"
            + "2024-01-16,45.50,Lunch\n"
            + "2024-01-17,150.00,Groceries";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "transactions.csv", "transaction");

    assertThat(csvData.rows()).hasSize(3);

    var row1 = csvData.rows().get(0);
    assertThat(row1.values().get("Date")).isEqualTo("2024-01-15");
    assertThat(row1.values().get("Amount")).isEqualTo("99.99");
    assertThat(row1.values().get("Description")).isEqualTo("Coffee");

    var row2 = csvData.rows().get(1);
    assertThat(row2.values().get("Date")).isEqualTo("2024-01-16");
    assertThat(row2.values().get("Amount")).isEqualTo("45.50");
    assertThat(row2.values().get("Description")).isEqualTo("Lunch");

    var row3 = csvData.rows().get(2);
    assertThat(row3.values().get("Date")).isEqualTo("2024-01-17");
    assertThat(row3.values().get("Amount")).isEqualTo("150.00");
    assertThat(row3.values().get("Description")).isEqualTo("Groceries");
  }

  @Test
  void shouldPreserveHeadersInCsvData() throws IOException {
    var csvContent = "Column1,Column2,Column3\nValue1,Value2,Value3";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "test");

    assertThat(csvData.headers()).isNotNull().containsExactly("Column1", "Column2", "Column3");
  }

  @Test
  void shouldHandleEmptyValuesInRow() throws IOException {
    var csvContent = "Name,Age,City\nJohn,,NYC\n,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEmpty();
    assertThat(firstRow.values().get("City")).isEqualTo("NYC");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEmpty();
    assertThat(secondRow.values().get("Age")).isEqualTo("25");
    assertThat(secondRow.values().get("City")).isEqualTo("Boston");
  }

  @Test
  void shouldHandleRowsWithFewerColumnsThanHeaders() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30\nAlice";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.headers()).hasSize(3);
    assertThat(csvData.rows()).hasSize(2);

    // First row has 2 values (missing City)
    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEqualTo("30");
    // OpenCSV returns empty string for missing columns
    assertThat(firstRow.values().get("City")).isEmpty();

    // Second row has 1 value (missing Age and City)
    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Age")).isEmpty();
    assertThat(secondRow.values().get("City")).isEmpty();
  }

  @Test
  void shouldHandleRowsWithMoreColumnsThanHeaders() throws IOException {
    var csvContent = "Name,Age\nJohn,30,NYC,Extra";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.headers()).hasSize(2);
    assertThat(csvData.rows()).hasSize(1);

    // Row has 4 values but only 2 headers
    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Age")).isEqualTo("30");
    // Extra columns beyond headers are ignored by our implementation
    assertThat(firstRow.values()).hasSize(2);
  }

  @Test
  void shouldHandleInconsistentColumnCountsAcrossRows() throws IOException {
    var csvContent =
        "ID,Name,Email\n1,Alice,alice@example.com\n2,Bob\n3,Carol,carol@example.com,Extra";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.headers()).hasSize(3);
    assertThat(csvData.rows()).hasSize(3);

    // First row - all columns present
    var row1 = csvData.rows().get(0);
    assertThat(row1.values().get("ID")).isEqualTo("1");
    assertThat(row1.values().get("Name")).isEqualTo("Alice");
    assertThat(row1.values().get("Email")).isEqualTo("alice@example.com");

    // Second row - missing Email column
    var row2 = csvData.rows().get(1);
    assertThat(row2.values().get("ID")).isEqualTo("2");
    assertThat(row2.values().get("Name")).isEqualTo("Bob");
    assertThat(row2.values().get("Email")).isEmpty();

    // Third row - has extra column beyond headers
    var row3 = csvData.rows().get(2);
    assertThat(row3.values().get("ID")).isEqualTo("3");
    assertThat(row3.values().get("Name")).isEqualTo("Carol");
    assertThat(row3.values().get("Email")).isEqualTo("carol@example.com");
  }

  @Test
  void shouldHandleUtf8CharactersInContent() throws IOException {
    var csvContent = "Name,Description\nJosé,Café au lait\n李明,中文测试\nMüller,Größe";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(3);

    var row1 = csvData.rows().get(0);
    assertThat(row1.values().get("Name")).isEqualTo("José");
    assertThat(row1.values().get("Description")).isEqualTo("Café au lait");

    var row2 = csvData.rows().get(1);
    assertThat(row2.values().get("Name")).isEqualTo("李明");
    assertThat(row2.values().get("Description")).isEqualTo("中文测试");

    var row3 = csvData.rows().get(2);
    assertThat(row3.values().get("Name")).isEqualTo("Müller");
    assertThat(row3.values().get("Description")).isEqualTo("Größe");
  }

  @Test
  void shouldHandleUtf8WithBomByteOrderMark() throws IOException {
    // UTF-8 BOM is EF BB BF
    var csvContentWithBom =
        "\uFEFFName,Age,City\nJohn,30,NYC"; // \uFEFF is the BOM character in Java
    var inputStream = createInputStream(csvContentWithBom);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    // OpenCSV should handle BOM, but first header might include it
    // This test documents current behavior
    assertThat(csvData.headers()).isNotNull().hasSize(3);
    assertThat(csvData.headers().get(0)).isIn("Name", "\uFEFFName");
  }

  @Test
  void shouldHandleUtf16EncodedContent() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream =
        new ByteArrayInputStream(csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_16));

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    // This test documents that UTF-16 may not be automatically detected
    // The parser should still attempt to parse, but results may vary
    assertThat(csvData).isNotNull();
    assertThat(csvData.headers()).isNotNull();
    assertThat(csvData.rows()).isNotNull();
  }

  @Test
  void shouldHandleIso88591EncodedContent() throws IOException {
    // ISO-8859-1 (Latin-1) encoding test with special characters
    var csvContent = "Name,Description\nCafé,Naïve résumé";
    var inputStream =
        new ByteArrayInputStream(csvContent.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    // This test documents encoding behavior - results depend on system default encoding
    assertThat(csvData).isNotNull();
    assertThat(csvData.headers()).isNotNull();
    assertThat(csvData.rows()).isNotNull();
  }

  @Test
  void shouldHandleEmojiCharacters() throws IOException {
    var csvContent = "Product,Rating\nCoffee,☕ Excellent! 😍\nPizza,🍕 Amazing! 🎉";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(2);

    var row1 = csvData.rows().get(0);
    assertThat(row1.values().get("Product")).isEqualTo("Coffee");
    assertThat(row1.values().get("Rating")).isEqualTo("☕ Excellent! 😍");

    var row2 = csvData.rows().get(1);
    assertThat(row2.values().get("Product")).isEqualTo("Pizza");
    assertThat(row2.values().get("Rating")).isEqualTo("🍕 Amazing! 🎉");
  }

  @Test
  void shouldHandleDifferentLineEndings() throws IOException {
    // Test CRLF (Windows style)
    var csvContentCrlf = "Name,Age\r\nJohn,30\r\nAlice,25";
    var inputStreamCrlf = createInputStream(csvContentCrlf);

    var csvDataCrlf = openCsvParser.parseCsvInputStream(inputStreamCrlf, "test.csv", "user-data");

    assertThat(csvDataCrlf.headers()).hasSize(2);
    assertThat(csvDataCrlf.rows()).hasSize(2);
    assertThat(csvDataCrlf.rows().get(0).values().get("Name")).isEqualTo("John");

    // Test CR only (old Mac style)
    var csvContentCr = "Name,Age\rJohn,30\rAlice,25";
    var inputStreamCr = createInputStream(csvContentCr);

    var csvDataCr = openCsvParser.parseCsvInputStream(inputStreamCr, "test.csv", "user-data");

    assertThat(csvDataCr.headers()).hasSize(2);
    assertThat(csvDataCr.rows()).hasSize(2);
    assertThat(csvDataCr.rows().get(0).values().get("Name")).isEqualTo("John");
  }

  @Test
  void shouldHandleQuotedFieldsWithEmbeddedNewlines() throws IOException {
    var csvContent = "Name,Address\nJohn,\"123 Main St\nApt 4B\nNew York\"\nAlice,456 Oak Ave";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(2);

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Address")).isEqualTo("123 Main St\nApt 4B\nNew York");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Address")).isEqualTo("456 Oak Ave");
  }

  @Test
  void shouldHandleEscapedQuotesInQuotedFields() throws IOException {
    var csvContent = "Name,Quote\nJohn,\"He said \"\"Hello\"\"\"\nAlice,\"She replied \"\"Hi\"\"\"";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(2);

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Quote")).isEqualTo("He said \"Hello\"");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Quote")).isEqualTo("She replied \"Hi\"");
  }

  @Test
  void shouldHandleVeryLongLines() throws IOException {
    // Create a row with a very long field (10,000 characters)
    var longValue = "A".repeat(10000);
    var csvContent = "ID,Data\n1," + longValue + "\n2,Short";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(2);

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("ID")).isEqualTo("1");
    assertThat(firstRow.values().get("Data")).isEqualTo(longValue);

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("ID")).isEqualTo("2");
    assertThat(secondRow.values().get("Data")).isEqualTo("Short");
  }

  @Test
  void shouldHandleTabsInValues() throws IOException {
    var csvContent = "Name,Notes\nJohn,\"First\tSecond\tThird\"\nAlice,Normal text";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertThat(csvData.rows()).hasSize(2);

    var firstRow = csvData.rows().get(0);
    assertThat(firstRow.values().get("Name")).isEqualTo("John");
    assertThat(firstRow.values().get("Notes")).isEqualTo("First\tSecond\tThird");

    var secondRow = csvData.rows().get(1);
    assertThat(secondRow.values().get("Name")).isEqualTo("Alice");
    assertThat(secondRow.values().get("Notes")).isEqualTo("Normal text");
  }

  private InputStream createInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
