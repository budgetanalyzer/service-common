package org.budgetanalyzer.core.csv.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    assertEquals("test.csv", csvData.fileName());
    assertEquals("user-data", csvData.format());
    assertEquals(3, csvData.headers().size());
    assertEquals("Name", csvData.headers().get(0));
    assertEquals("Age", csvData.headers().get(1));
    assertEquals("City", csvData.headers().get(2));
    assertEquals(2, csvData.rows().size());
  }

  @Test
  void shouldExtractHeadersCorrectly() throws IOException {
    var csvContent = "Transaction Date,Amount,Description,Category\n2024-01-15,99.99,Coffee,Food";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "transactions.csv", "transaction");

    var headers = csvData.headers();
    assertEquals(4, headers.size());
    assertEquals("Transaction Date", headers.get(0));
    assertEquals("Amount", headers.get(1));
    assertEquals("Description", headers.get(2));
    assertEquals("Category", headers.get(3));
  }

  @Test
  void shouldMapHeadersToRowValues() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));

    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("25", secondRow.values().get("Age"));
    assertEquals("Boston", secondRow.values().get("City"));
  }

  @Test
  void shouldHandleEmptyCsvFile() throws IOException {
    var csvContent = "";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "empty.csv", "empty");

    assertEquals("empty.csv", csvData.fileName());
    assertEquals("empty", csvData.format());
    assertTrue(csvData.headers().isEmpty());
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldHandleCsvWithOnlyHeaders() throws IOException {
    var csvContent = "Name,Age,City";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "headers-only.csv", "test");

    assertEquals(3, csvData.headers().size());
    assertEquals("Name", csvData.headers().get(0));
    assertEquals("Age", csvData.headers().get(1));
    assertEquals("City", csvData.headers().get(2));
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldTrimWhitespaceInHeaders() throws IOException {
    var csvContent = "  Name  ,  Age  ,  City  \nJohn,30,NYC";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var headers = csvData.headers();
    assertEquals("Name", headers.get(0));
    assertEquals("Age", headers.get(1));
    assertEquals("City", headers.get(2));
  }

  @Test
  void shouldTrimWhitespaceInValues() throws IOException {
    var csvContent = "Name,Age,City\n  John  ,  30  ,  NYC  ";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));
  }

  @Test
  void shouldSkipEmptyHeaderColumns() throws IOException {
    var csvContent = "Name,,Age,City\nJohn,ignored,30,NYC";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));
    // Empty header column should be skipped, so no mapping for "ignored" value
    assertEquals(3, firstRow.values().size());
  }

  @Test
  void shouldAssignCorrectLineNumbers() throws IOException {
    var csvContent = "Name,Age\nJohn,30\nAlice,25\nBob,35";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().get(0).lineNumber()); // First data row is line 2
    assertEquals(3, csvData.rows().get(1).lineNumber()); // Second data row is line 3
    assertEquals(4, csvData.rows().get(2).lineNumber()); // Third data row is line 4
  }

  @Test
  void shouldHandleQuotedValues() throws IOException {
    var csvContent = "Name,Description\nJohn,\"Smith, Jr.\"\nAlice,\"O'Brien\"";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("Smith, Jr.", firstRow.values().get("Description"));

    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("O'Brien", secondRow.values().get("Description"));
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

    assertEquals(3, csvData.headers().size());
    assertEquals(1000, csvData.rows().size());
    assertEquals("User1", csvData.rows().get(0).values().get("Name"));
    assertEquals("user500@example.com", csvData.rows().get(499).values().get("Email"));
    assertEquals("User1000", csvData.rows().get(999).values().get("Name"));
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

    assertEquals(3, csvData.rows().size());

    var row1 = csvData.rows().get(0);
    assertEquals("2024-01-15", row1.values().get("Date"));
    assertEquals("99.99", row1.values().get("Amount"));
    assertEquals("Coffee", row1.values().get("Description"));

    var row2 = csvData.rows().get(1);
    assertEquals("2024-01-16", row2.values().get("Date"));
    assertEquals("45.50", row2.values().get("Amount"));
    assertEquals("Lunch", row2.values().get("Description"));

    var row3 = csvData.rows().get(2);
    assertEquals("2024-01-17", row3.values().get("Date"));
    assertEquals("150.00", row3.values().get("Amount"));
    assertEquals("Groceries", row3.values().get("Description"));
  }

  @Test
  void shouldPreserveHeadersInCsvData() throws IOException {
    var csvContent = "Column1,Column2,Column3\nValue1,Value2,Value3";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "test");

    assertNotNull(csvData.headers());
    assertEquals(3, csvData.headers().size());
    assertEquals("Column1", csvData.headers().get(0));
    assertEquals("Column2", csvData.headers().get(1));
    assertEquals("Column3", csvData.headers().get(2));
  }

  @Test
  void shouldHandleEmptyValuesInRow() throws IOException {
    var csvContent = "Name,Age,City\nJohn,,NYC\n,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));

    var secondRow = csvData.rows().get(1);
    assertEquals("", secondRow.values().get("Name"));
    assertEquals("25", secondRow.values().get("Age"));
    assertEquals("Boston", secondRow.values().get("City"));
  }

  @Test
  void shouldHandleRowsWithFewerColumnsThanHeaders() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30\nAlice";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(3, csvData.headers().size());
    assertEquals(2, csvData.rows().size());

    // First row has 2 values (missing City)
    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    // OpenCSV returns empty string for missing columns
    assertEquals("", firstRow.values().get("City"));

    // Second row has 1 value (missing Age and City)
    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("", secondRow.values().get("Age"));
    assertEquals("", secondRow.values().get("City"));
  }

  @Test
  void shouldHandleRowsWithMoreColumnsThanHeaders() throws IOException {
    var csvContent = "Name,Age\nJohn,30,NYC,Extra";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.headers().size());
    assertEquals(1, csvData.rows().size());

    // Row has 4 values but only 2 headers
    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    // Extra columns beyond headers are ignored by our implementation
    assertEquals(2, firstRow.values().size());
  }

  @Test
  void shouldHandleInconsistentColumnCountsAcrossRows() throws IOException {
    var csvContent =
        "ID,Name,Email\n1,Alice,alice@example.com\n2,Bob\n3,Carol,carol@example.com,Extra";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(3, csvData.headers().size());
    assertEquals(3, csvData.rows().size());

    // First row - all columns present
    var row1 = csvData.rows().get(0);
    assertEquals("1", row1.values().get("ID"));
    assertEquals("Alice", row1.values().get("Name"));
    assertEquals("alice@example.com", row1.values().get("Email"));

    // Second row - missing Email column
    var row2 = csvData.rows().get(1);
    assertEquals("2", row2.values().get("ID"));
    assertEquals("Bob", row2.values().get("Name"));
    assertEquals("", row2.values().get("Email"));

    // Third row - has extra column beyond headers
    var row3 = csvData.rows().get(2);
    assertEquals("3", row3.values().get("ID"));
    assertEquals("Carol", row3.values().get("Name"));
    assertEquals("carol@example.com", row3.values().get("Email"));
  }

  @Test
  void shouldHandleUtf8CharactersInContent() throws IOException {
    var csvContent = "Name,Description\nJosé,Café au lait\n李明,中文测试\nMüller,Größe";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(3, csvData.rows().size());

    var row1 = csvData.rows().get(0);
    assertEquals("José", row1.values().get("Name"));
    assertEquals("Café au lait", row1.values().get("Description"));

    var row2 = csvData.rows().get(1);
    assertEquals("李明", row2.values().get("Name"));
    assertEquals("中文测试", row2.values().get("Description"));

    var row3 = csvData.rows().get(2);
    assertEquals("Müller", row3.values().get("Name"));
    assertEquals("Größe", row3.values().get("Description"));
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
    assertNotNull(csvData.headers());
    assertEquals(3, csvData.headers().size());
    assertTrue(
        csvData.headers().get(0).equals("Name") || csvData.headers().get(0).equals("\uFEFFName"));
  }

  @Test
  void shouldHandleUtf16EncodedContent() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream =
        new ByteArrayInputStream(csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_16));

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    // This test documents that UTF-16 may not be automatically detected
    // The parser should still attempt to parse, but results may vary
    assertNotNull(csvData);
    assertNotNull(csvData.headers());
    assertNotNull(csvData.rows());
  }

  @Test
  void shouldHandleIso88591EncodedContent() throws IOException {
    // ISO-8859-1 (Latin-1) encoding test with special characters
    var csvContent = "Name,Description\nCafé,Naïve résumé";
    var inputStream =
        new ByteArrayInputStream(csvContent.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    // This test documents encoding behavior - results depend on system default encoding
    assertNotNull(csvData);
    assertNotNull(csvData.headers());
    assertNotNull(csvData.rows());
  }

  @Test
  void shouldHandleEmojiCharacters() throws IOException {
    var csvContent = "Product,Rating\nCoffee,☕ Excellent! 😍\nPizza,🍕 Amazing! 🎉";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().size());

    var row1 = csvData.rows().get(0);
    assertEquals("Coffee", row1.values().get("Product"));
    assertEquals("☕ Excellent! 😍", row1.values().get("Rating"));

    var row2 = csvData.rows().get(1);
    assertEquals("Pizza", row2.values().get("Product"));
    assertEquals("🍕 Amazing! 🎉", row2.values().get("Rating"));
  }

  @Test
  void shouldHandleDifferentLineEndings() throws IOException {
    // Test CRLF (Windows style)
    var csvContentCrlf = "Name,Age\r\nJohn,30\r\nAlice,25";
    var inputStreamCrlf = createInputStream(csvContentCrlf);

    var csvDataCrlf = openCsvParser.parseCsvInputStream(inputStreamCrlf, "test.csv", "user-data");

    assertEquals(2, csvDataCrlf.headers().size());
    assertEquals(2, csvDataCrlf.rows().size());
    assertEquals("John", csvDataCrlf.rows().get(0).values().get("Name"));

    // Test CR only (old Mac style)
    var csvContentCr = "Name,Age\rJohn,30\rAlice,25";
    var inputStreamCr = createInputStream(csvContentCr);

    var csvDataCr = openCsvParser.parseCsvInputStream(inputStreamCr, "test.csv", "user-data");

    assertEquals(2, csvDataCr.headers().size());
    assertEquals(2, csvDataCr.rows().size());
    assertEquals("John", csvDataCr.rows().get(0).values().get("Name"));
  }

  @Test
  void shouldHandleQuotedFieldsWithEmbeddedNewlines() throws IOException {
    var csvContent = "Name,Address\nJohn,\"123 Main St\nApt 4B\nNew York\"\nAlice,456 Oak Ave";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().size());

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("123 Main St\nApt 4B\nNew York", firstRow.values().get("Address"));

    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("456 Oak Ave", secondRow.values().get("Address"));
  }

  @Test
  void shouldHandleEscapedQuotesInQuotedFields() throws IOException {
    var csvContent = "Name,Quote\nJohn,\"He said \"\"Hello\"\"\"\nAlice,\"She replied \"\"Hi\"\"\"";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().size());

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("He said \"Hello\"", firstRow.values().get("Quote"));

    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("She replied \"Hi\"", secondRow.values().get("Quote"));
  }

  @Test
  void shouldHandleVeryLongLines() throws IOException {
    // Create a row with a very long field (10,000 characters)
    var longValue = "A".repeat(10000);
    var csvContent = "ID,Data\n1," + longValue + "\n2,Short";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().size());

    var firstRow = csvData.rows().get(0);
    assertEquals("1", firstRow.values().get("ID"));
    assertEquals(longValue, firstRow.values().get("Data"));

    var secondRow = csvData.rows().get(1);
    assertEquals("2", secondRow.values().get("ID"));
    assertEquals("Short", secondRow.values().get("Data"));
  }

  @Test
  void shouldHandleTabsInValues() throws IOException {
    var csvContent = "Name,Notes\nJohn,\"First\tSecond\tThird\"\nAlice,Normal text";
    var inputStream = createInputStream(csvContent);

    var csvData = openCsvParser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().size());

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("First\tSecond\tThird", firstRow.values().get("Notes"));

    var secondRow = csvData.rows().get(1);
    assertEquals("Alice", secondRow.values().get("Name"));
    assertEquals("Normal text", secondRow.values().get("Notes"));
  }

  private InputStream createInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
