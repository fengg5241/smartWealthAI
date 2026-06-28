package com.smartwealth.ai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    public String parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("File name is missing");
        }
        return parse(file.getBytes(), fileName);
    }

    public String parse(byte[] fileBytes, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(fileBytes, fileName);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(fileBytes, fileName);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(fileBytes, fileName);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName + ". Supported: PDF, DOCX, XLSX, XLS.");
        }
    }

    private final OcrService ocrService;

    public DocumentParserService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    private String parsePdf(byte[] fileBytes, String fileName) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (!text.isBlank()) {
                log.info("Parsed PDF '{}': {} chars", fileName, text.length());
                return text;
            }
        }
        // PDFBox extracted nothing — fallback to OCR
        log.info("PDF '{}' has no selectable text, falling back to OCR...", fileName);
        String ocrText = ocrService.ocrPdf(fileBytes, fileName);
        log.info("Parsed PDF '{}': {} chars (OCR)", fileName, ocrText.length());
        return ocrText;
    }

    private String parseDocx(byte[] fileBytes, String fileName) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            });
            document.getTables().forEach(table -> {
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> {
                        String text = cell.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    });
                });
            });
            log.info("Parsed DOCX '{}': {} chars", fileName, sb.length());
            return sb.toString();
        }
    }

    private String parseExcel(byte[] fileBytes, String fileName) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder result = new StringBuilder();
            int totalSheets = workbook.getNumberOfSheets();
            int nonEmptySheets = 0;
            int totalRows = 0;

            for (int s = 0; s < totalSheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                int lastRowNum = sheet.getLastRowNum();

                if (lastRowNum < 0) {
                    log.warn("Sheet '{}' is empty, skipping", sheetName);
                    continue;
                }

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    log.warn("Sheet '{}' has no header row, skipping", sheetName);
                    continue;
                }

                int colCount = headerRow.getLastCellNum();
                if (colCount <= 0) {
                    log.warn("Sheet '{}' header row has no cells, skipping", sheetName);
                    continue;
                }

                List<String> headers = new ArrayList<>();
                for (int c = 0; c < colCount; c++) {
                    Cell cell = headerRow.getCell(c);
                    headers.add(cell == null ? "Col" + (c + 1) : sanitize(formatter.formatCellValue(cell)));
                }

                result.append("## Sheet: ").append(sheetName).append("\n");
                result.append("Columns: ").append(String.join(", ", headers)).append("\n\n");

                int dataRowCount = 0;
                for (int r = 1; r <= lastRowNum; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    StringBuilder rowLine = new StringBuilder("  ");
                    for (int c = 0; c < colCount; c++) {
                        if (c > 0) rowLine.append(", ");
                        Cell cell = row.getCell(c);
                        String val = cell == null ? "" : sanitize(formatter.formatCellValue(cell));
                        rowLine.append(headers.get(c)).append(": ").append(val);
                    }
                    result.append(rowLine).append("\n");
                    dataRowCount++;
                }

                if (dataRowCount > 0) {
                    nonEmptySheets++;
                    totalRows += dataRowCount;
                }
                result.append("\n");
            }

            if (nonEmptySheets == 0) {
                throw new IllegalArgumentException("Excel file contains no readable data");
            }

            log.info("Parsed XLSX/XLS '{}': {} sheets, {} data rows, {} chars",
                    fileName, nonEmptySheets, totalRows, result.length());
            return result.toString();
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace("|", ";").replace("\n", " ").replace("\r", " ");
    }
}
