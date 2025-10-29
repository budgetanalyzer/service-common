package com.bleurubin.core.csv;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

public interface CsvParser {

  CsvData parseCsvFile(MultipartFile file, String format) throws IOException;

  CsvData parseCsvInputStream(InputStream inputStream, String fileName, String format)
      throws IOException;
}
