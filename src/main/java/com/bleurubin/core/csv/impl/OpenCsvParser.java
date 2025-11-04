package com.bleurubin.core.csv.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;

import com.bleurubin.core.csv.CsvData;
import com.bleurubin.core.csv.CsvParser;
import com.bleurubin.core.csv.CsvRow;

/**
 * CSV parser implementation using the OpenCSV library.
 *
 * <p>This implementation uses OpenCSV version 3.7 to parse CSV files. It extracts headers from the
 * first row and converts subsequent rows into {@link CsvRow} objects with header-based column
 * access.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Automatic header extraction from first row
 *   <li>Whitespace trimming for headers and cell values
 *   <li>Empty header columns are skipped
 *   <li>Empty CSV files are handled gracefully
 *   <li>Line numbers for error reporting
 * </ul>
 *
 * <p>This is a Spring component and can be injected as a dependency wherever CSV parsing is needed.
 */
@Component
public class OpenCsvParser implements CsvParser {

  private static final Logger log = LoggerFactory.getLogger(OpenCsvParser.class);

  /**
   * Parses a CSV file uploaded via multipart form.
   *
   * <p>Delegates to {@link #parseCsvInputStream} using the file's input stream and original
   * filename.
   *
   * @param file the uploaded CSV file
   * @param format a label describing the CSV format or data type
   * @return parsed CSV data containing headers and rows
   * @throws IOException if an I/O error occurs reading the file
   */
  @Override
  public CsvData parseCsvFile(MultipartFile file, String format) throws IOException {
    return parseCsvInputStream(file.getInputStream(), file.getOriginalFilename(), format);
  }

  /**
   * Parses a CSV file from an input stream.
   *
   * <p>Reads all lines from the stream, treats the first line as headers, and converts subsequent
   * lines to {@link CsvRow} objects. Empty files return a {@link CsvData} with an empty row list.
   *
   * @param inputStream the input stream containing CSV data
   * @param fileName the name of the file (used for logging)
   * @param format a label describing the CSV format or data type
   * @return parsed CSV data containing headers and rows
   * @throws IOException if an I/O error occurs reading the stream
   */
  @Override
  public CsvData parseCsvInputStream(InputStream inputStream, String fileName, String format)
      throws IOException {
    try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
      var allRows = csvReader.readAll();

      if (allRows.isEmpty()) {
        log.info("Ignoring empty csv file: {}", fileName);
        return new CsvData(fileName, format, null, null);
      }

      return buildCsvData(fileName, format, allRows);
    }
  }

  /**
   * Builds CsvData from raw CSV rows.
   *
   * <p>Extracts headers from the first row, then converts remaining rows to CsvRow objects with
   * header-based column access.
   *
   * @param fileName the name of the CSV file
   * @param format the CSV format label
   * @param allRows all rows from the CSV file (including header row)
   * @return structured CSV data with headers and rows
   */
  private CsvData buildCsvData(String fileName, String format, List<String[]> allRows) {
    var headers = Arrays.stream(allRows.getFirst()).map(String::trim).toList();
    var rows = allRows.subList(1, allRows.size());
    var csvRows = new ArrayList<CsvRow>();

    for (int i = 0; i < rows.size(); i++) {
      var dataMap = buildDataMap(headers, rows.get(i));
      var csvRow = new CsvRow(i + 2, dataMap);

      csvRows.add(csvRow);
    }

    return new CsvData(fileName, format, headers, csvRows);
  }

  /**
   * Builds a map of header names to cell values for a single row.
   *
   * <p>Empty header names are skipped to avoid mapping empty strings. All cell values are trimmed
   * of leading and trailing whitespace.
   *
   * @param headers the list of column headers
   * @param row the array of cell values for this row
   * @return a map of header names to cell values
   */
  private Map<String, String> buildDataMap(List<String> headers, String[] row) {
    var rowMap = new HashMap<String, String>();

    for (int i = 0; i < row.length; i++) {
      if (!headers.get(i).isEmpty()) {
        rowMap.put(headers.get(i), row[i].trim());
      }
    }

    return rowMap;
  }
}
