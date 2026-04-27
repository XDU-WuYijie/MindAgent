package com.mindagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindagent.rag")
public class RagProperties {

    private boolean enabled = true;
    /**
     * Supported values:
     * - local
     * - chroma
     */
    private String provider = "local";
    private int topK = 3;
    private int bm25TopK = 20;
    private int vectorTopK = 20;
    private int finalTopK = 5;
    private int rrfK = 60;
    private int maxContextTokens = 2500;
    private int maxChunksPerDoc = 2;
    private int minChunkLength = 40;
    private boolean fallbackToLocal = true;
    private String knowledgeDir = "backend/storage/knowledge";
    private QueryTypeWeight psychologyKnowledge = new QueryTypeWeight(0.4d, 0.6d);
    private QueryTypeWeight appointmentProcess = new QueryTypeWeight(0.7d, 0.3d);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getBm25TopK() {
        return bm25TopK;
    }

    public void setBm25TopK(int bm25TopK) {
        this.bm25TopK = bm25TopK;
    }

    public int getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(int vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public int getFinalTopK() {
        return finalTopK;
    }

    public void setFinalTopK(int finalTopK) {
        this.finalTopK = finalTopK;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public int getMaxChunksPerDoc() {
        return maxChunksPerDoc;
    }

    public void setMaxChunksPerDoc(int maxChunksPerDoc) {
        this.maxChunksPerDoc = maxChunksPerDoc;
    }

    public int getMinChunkLength() {
        return minChunkLength;
    }

    public void setMinChunkLength(int minChunkLength) {
        this.minChunkLength = minChunkLength;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToLocal() {
        return fallbackToLocal;
    }

    public void setFallbackToLocal(boolean fallbackToLocal) {
        this.fallbackToLocal = fallbackToLocal;
    }

    public String getKnowledgeDir() {
        return knowledgeDir;
    }

    public void setKnowledgeDir(String knowledgeDir) {
        this.knowledgeDir = knowledgeDir;
    }

    public QueryTypeWeight getPsychologyKnowledge() {
        return psychologyKnowledge;
    }

    public void setPsychologyKnowledge(QueryTypeWeight psychologyKnowledge) {
        this.psychologyKnowledge = psychologyKnowledge;
    }

    public QueryTypeWeight getAppointmentProcess() {
        return appointmentProcess;
    }

    public void setAppointmentProcess(QueryTypeWeight appointmentProcess) {
        this.appointmentProcess = appointmentProcess;
    }

    public QueryTypeWeight weightFor(String queryType) {
        if ("APPOINTMENT_PROCESS".equalsIgnoreCase(queryType)) {
            return appointmentProcess == null ? new QueryTypeWeight(0.7d, 0.3d) : appointmentProcess;
        }
        return psychologyKnowledge == null ? new QueryTypeWeight(0.4d, 0.6d) : psychologyKnowledge;
    }

    public static class QueryTypeWeight {

        private double bm25Weight;
        private double vectorWeight;

        public QueryTypeWeight() {
        }

        public QueryTypeWeight(double bm25Weight, double vectorWeight) {
            this.bm25Weight = bm25Weight;
            this.vectorWeight = vectorWeight;
        }

        public double getBm25Weight() {
            return bm25Weight;
        }

        public void setBm25Weight(double bm25Weight) {
            this.bm25Weight = bm25Weight;
        }

        public double getVectorWeight() {
            return vectorWeight;
        }

        public void setVectorWeight(double vectorWeight) {
            this.vectorWeight = vectorWeight;
        }
    }
}
