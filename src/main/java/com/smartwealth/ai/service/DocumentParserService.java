package com.smartwealth.ai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    public String parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("File name is missing");
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(file);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName + ". Only PDF and DOCX are supported.");
        }
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("Parsed PDF '{}': {} chars", file.getOriginalFilename(), text.length());
            return text;
        }
    }

    private String parseDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            });
            log.info("Parsed DOCX '{}': {} chars", file.getOriginalFilename(), sb.length());
            return sb.toString();
        }
    }
}
