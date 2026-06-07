package com.smartwealth.ai.service;

import com.smartwealth.ai.config.DemoProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunkingStrategy {

    private final int chunkSize;
    private final int chunkOverlap;
    private final int tableRowsPerChunk;

    public TextChunkingStrategy(DemoProperties properties) {
        this.chunkSize = properties.getRag().getChunkSize();
        this.chunkOverlap = properties.getRag().getChunkOverlap();
        this.tableRowsPerChunk = properties.getRag().getTableRowsPerChunk();
    }

    public List<TextSegment> chunk(String text, String fileName) {
        List<TextSegment> segments = new ArrayList<>();
        String cleaned = text.replaceAll("\\s+", " ").trim();

        if (cleaned.isEmpty()) {
            return segments;
        }

        int start = 0;
        int index = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            segments.add(new TextSegment(cleaned.substring(start, end), fileName, index));
            start += (chunkSize - chunkOverlap);
            index++;
        }

        return segments;
    }

    public List<TextSegment> chunkStructured(String content, String fileName) {
        List<TextSegment> segments = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return segments;
        }

        String[] sheetBlocks = content.split("(?=## Sheet:)");
        int globalIndex = 0;

        for (String block : sheetBlocks) {
            if (block.isBlank()) {
                continue;
            }

            String[] lines = block.split("\\R");
            StringBuilder sheetHeader = new StringBuilder();
            String columnsLine = "";
            List<String> dataRows = new ArrayList<>();

            for (String line : lines) {
                if (line.startsWith("## Sheet:")) {
                    sheetHeader.append(line).append("\n");
                } else if (line.startsWith("Columns:")) {
                    columnsLine = line;
                } else if (line.startsWith("  ") && !line.isBlank()) {
                    dataRows.add(line);
                }
            }

            if (columnsLine.isEmpty()) {
                continue;
            }

            String headerBlock = sheetHeader + columnsLine + "\n\n";

            if (dataRows.isEmpty()) {
                segments.add(new TextSegment(headerBlock, fileName, globalIndex++));
            } else {
                for (int i = 0; i < dataRows.size(); i += tableRowsPerChunk) {
                    int end = Math.min(i + tableRowsPerChunk, dataRows.size());
                    StringBuilder chunk = new StringBuilder(headerBlock);
                    for (int j = i; j < end; j++) {
                        chunk.append(dataRows.get(j)).append("\n");
                    }
                    segments.add(new TextSegment(chunk.toString(), fileName, globalIndex++));
                }
            }
        }

        return segments;
    }

    public record TextSegment(String text, String fileName, int index) {}
}
