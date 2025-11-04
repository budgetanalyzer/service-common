package com.bleurubin.core.csv;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

/**
 * Interface for parsing CSV files into structured data.
 *
 * <p>This interface provides an abstraction for CSV parsing, allowing different implementations
 * (e.g., OpenCSV, Apache Commons CSV, custom parsers) to be plugged in. The default implementation
 * uses OpenCSV via {@link com.bleurubin.core.csv.impl.OpenCsvParser}.
 *
 * <p>The parser extracts headers from the first row and converts subsequent rows into key-value
 * maps where keys are column headers. This allows for header-based column access rather than
 * index-based access.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Service
 * public class TransactionImportService {
 *     private final CsvParser csvParser;
 *
 *     public TransactionImportService(CsvParser csvParser) {
 *         this.csvParser = csvParser;
 *     }
 *
 *     public List&lt;Transaction&gt; importCsv(MultipartFile file) throws IOException {
 *         CsvData csvData = csvParser.parseCsvFile(file, "transaction");
 *
 *         return csvData.rows().stream()
 *             .map(row -&gt; {
 *                 var transaction = new Transaction();
 *                 transaction.setDate(LocalDate.parse(row.values().get("Date")));
 *                 transaction.setAmount(new BigDecimal(row.values().get("Amount")));
 *                 return transaction;
 *             })
 *             .toList();
 *     }
 * }
 * </pre>
 */
public interface CsvParser {

  /**
   * Parses a CSV file from an input stream.
   *
   * <p>This method is useful for parsing CSV data from sources other than file uploads, such as
   * HTTP responses, local files, or resources.
   *
   * @param inputStream the input stream containing CSV data
   * @param fileName the name of the file (used for logging and error messages)
   * @param format a label describing the CSV format or data type (e.g., "transaction", "user")
   * @return parsed CSV data containing headers and rows
   * @throws IOException if an I/O error occurs reading the stream
   */
  CsvData parseCsvInputStream(InputStream inputStream, String fileName, String format)
      throws IOException;

  /**
   * Parses a CSV file uploaded via multipart form.
   *
   * @param file the uploaded CSV file
   * @param format a label describing the CSV format or data type (e.g., "transaction", "user")
   * @return parsed CSV data containing headers and rows
   * @throws IOException if an I/O error occurs reading the file
   */
  default CsvData parseCsvFile(MultipartFile file, String format) throws IOException {
    return parseCsvInputStream(file.getInputStream(), file.getOriginalFilename(), format);
  }
}
