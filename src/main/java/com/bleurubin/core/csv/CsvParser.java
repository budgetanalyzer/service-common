package com.bleurubin.core.csv;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface CsvParser {

  CsvData parseCsvFile(MultipartFile file, String format) throws IOException;
}
