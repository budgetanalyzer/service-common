package com.bleurubin.core.csv.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for {@link OpenCsvParser}.
 *
 * <p>Tests CSV parsing functionality including header extraction, row parsing, whitespace handling,
 * and edge cases.
 */
class OpenCsvParserTest {

  private OpenCsvParser parser;

  @BeforeEach
  void setUp() {
    parser = new OpenCsvParser();
  }

  @Test
  void shouldParseValidCsvWithHeaders() throws IOException {
    var csvContent = "Name,Age,City\nJohn,30,NYC\nAlice,25,Boston";
    var inputStream = createInputStream(csvContent);

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

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

    var csvData = parser.parseCsvInputStream(inputStream, "transactions.csv", "transaction");

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

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

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

    var csvData = parser.parseCsvInputStream(inputStream, "empty.csv", "empty");

    assertEquals("empty.csv", csvData.fileName());
    assertEquals("empty", csvData.format());
    assertTrue(csvData.headers().isEmpty());
    assertTrue(csvData.rows().isEmpty());
  }

  @Test
  void shouldHandleCsvWithOnlyHeaders() throws IOException {
    var csvContent = "Name,Age,City";
    var inputStream = createInputStream(csvContent);

    var csvData = parser.parseCsvInputStream(inputStream, "headers-only.csv", "test");

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

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var headers = csvData.headers();
    assertEquals("Name", headers.get(0));
    assertEquals("Age", headers.get(1));
    assertEquals("City", headers.get(2));
  }

  @Test
  void shouldTrimWhitespaceInValues() throws IOException {
    var csvContent = "Name,Age,City\n  John  ,  30  ,  NYC  ";
    var inputStream = createInputStream(csvContent);

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("30", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));
  }

  @Test
  void shouldSkipEmptyHeaderColumns() throws IOException {
    var csvContent = "Name,,Age,City\nJohn,ignored,30,NYC";
    var inputStream = createInputStream(csvContent);

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

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

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    assertEquals(2, csvData.rows().get(0).lineNumber()); // First data row is line 2
    assertEquals(3, csvData.rows().get(1).lineNumber()); // Second data row is line 3
    assertEquals(4, csvData.rows().get(2).lineNumber()); // Third data row is line 4
  }

  @Test
  void shouldParseMultipartFile() throws IOException {
    var csvContent = "Name,Age\nJohn,30";
    var file =
        new MockMultipartFile(
            "file", "test.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

    var csvData = parser.parseCsvFile(file, "user-data");

    assertEquals("test.csv", csvData.fileName());
    assertEquals("user-data", csvData.format());
    assertEquals(2, csvData.headers().size());
    assertEquals(1, csvData.rows().size());
  }

  @Test
  void shouldHandleQuotedValues() throws IOException {
    var csvContent = "Name,Description\nJohn,\"Smith, Jr.\"\nAlice,\"O'Brien\"";
    var inputStream = createInputStream(csvContent);

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

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

    var csvData = parser.parseCsvInputStream(inputStream, "large.csv", "user-data");

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

    var csvData = parser.parseCsvInputStream(inputStream, "transactions.csv", "transaction");

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

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "test");

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

    var csvData = parser.parseCsvInputStream(inputStream, "test.csv", "user-data");

    var firstRow = csvData.rows().get(0);
    assertEquals("John", firstRow.values().get("Name"));
    assertEquals("", firstRow.values().get("Age"));
    assertEquals("NYC", firstRow.values().get("City"));

    var secondRow = csvData.rows().get(1);
    assertEquals("", secondRow.values().get("Name"));
    assertEquals("25", secondRow.values().get("Age"));
    assertEquals("Boston", secondRow.values().get("City"));
  }

  private InputStream createInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
