package com.smartwealth.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private final Rag rag = new Rag();
    private final Chat chat = new Chat();

    public Rag getRag() { return rag; }
    public Chat getChat() { return chat; }

    public static class Rag {
        private int topK = 4;
        private double similarityThreshold = 0.35;
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        private int tableRowsPerChunk = 10;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

        public int getTableRowsPerChunk() { return tableRowsPerChunk; }
        public void setTableRowsPerChunk(int tableRowsPerChunk) { this.tableRowsPerChunk = tableRowsPerChunk; }
    }

    public static class Chat {
        private String model = "gpt-4.1-mini";
        private double temperature = 0.2;
        private int maxTokens = 900;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }
}
