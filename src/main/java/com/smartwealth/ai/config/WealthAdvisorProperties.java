package com.smartwealth.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wealth-advisor")
public class WealthAdvisorProperties {

    private final Rag rag = new Rag();
    private final Recommendation recommendation = new Recommendation();

    public Rag getRag() {
        return rag;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public static class Rag {

        private boolean bootstrapEnabled = true;
        private int topK = 4;
        private double similarityThreshold = 0.35d;
        private int vectorDimensions = 1536;

        public boolean isBootstrapEnabled() {
            return bootstrapEnabled;
        }

        public void setBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getVectorDimensions() {
            return vectorDimensions;
        }

        public void setVectorDimensions(int vectorDimensions) {
            this.vectorDimensions = vectorDimensions;
        }
    }

    public static class Recommendation {

        private int maxProducts = 3;

        public int getMaxProducts() {
            return maxProducts;
        }

        public void setMaxProducts(int maxProducts) {
            this.maxProducts = maxProducts;
        }
    }
}
