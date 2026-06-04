package com.smartwealth.ai.service;

import com.smartwealth.ai.config.DemoProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunkingStrategy {

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunkingStrategy(DemoProperties properties) {
        this.chunkSize = properties.getRag().getChunkSize();
        this.chunkOverlap = properties.getRag().getChunkOverlap();
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

    public record TextSegment(String text, String fileName, int index) {}
}
