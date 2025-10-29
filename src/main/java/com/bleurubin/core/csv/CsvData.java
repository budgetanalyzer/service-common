package com.bleurubin.core.csv;

import java.util.List;
import java.util.Objects;

public record CsvData(String fileName, String format, List<CsvRow> rows) {
  public CsvData {
    Objects.requireNonNull(fileName, "fileName cannot be null");
    Objects.requireNonNull(format, "format cannot be null");
    rows = Objects.requireNonNullElse(rows, List.of());
  }
}
