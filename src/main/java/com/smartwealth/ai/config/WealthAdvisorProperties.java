package com.smartwealth.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wealth-advisor")
public class WealthAdvisorProperties {

    private final Rag rag = new Rag();
    private final Recommendation recommendation = new Recommendation();
    private final Llm llm = new Llm();

    public Rag getRag() {
        return rag;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public Llm getLlm() {
        return llm;
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

    public static class Llm {

        private String classificationModel = "gpt-4o-mini";
        private Double classificationTemperature = 0.0d;
        private Integer classificationMaxTokens = 120;
        private String answerModel = "gpt-4o-mini";
        private Double answerTemperature = 0.2d;
        private Integer answerMaxTokens = 900;

        public String getClassificationModel() {
            return classificationModel;
        }

        public void setClassificationModel(String classificationModel) {
            this.classificationModel = classificationModel;
        }

        public Double getClassificationTemperature() {
            return classificationTemperature;
        }

        public void setClassificationTemperature(Double classificationTemperature) {
            this.classificationTemperature = classificationTemperature;
        }

        public Integer getClassificationMaxTokens() {
            return classificationMaxTokens;
        }

        public void setClassificationMaxTokens(Integer classificationMaxTokens) {
            this.classificationMaxTokens = classificationMaxTokens;
        }

        public String getAnswerModel() {
            return answerModel;
        }

        public void setAnswerModel(String answerModel) {
            this.answerModel = answerModel;
        }

        public Double getAnswerTemperature() {
            return answerTemperature;
        }

        public void setAnswerTemperature(Double answerTemperature) {
            this.answerTemperature = answerTemperature;
        }

        public Integer getAnswerMaxTokens() {
            return answerMaxTokens;
        }

        public void setAnswerMaxTokens(Integer answerMaxTokens) {
            this.answerMaxTokens = answerMaxTokens;
        }
    }
}
