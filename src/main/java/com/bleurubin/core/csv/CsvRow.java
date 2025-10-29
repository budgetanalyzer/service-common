package com.bleurubin.core.csv;

import java.util.Map;

public record CsvRow(int lineNumber, Map<String, String> values) {}
