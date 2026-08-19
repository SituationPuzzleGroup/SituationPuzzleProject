package com.situationpuzzle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {
    private boolean enabled = true;
    private String baseUrl = "https://openrouter.ai/api/v1";
    private String apiKey = "";
    private String defaultProfile = "openrouter-free";
    private String siteUrl = "http://localhost:8080";
    private String siteName = "SituationPuzzle";
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 60000;
    /**
     * 關閉 Qwen3.x 等模型的深度思考（enable_thinking=false）。
     * 預設 false：直接回答、較快、較省。
     */
    private boolean enableThinking = false;
    private Map<String, Profile> profiles = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getDefaultProfile() { return defaultProfile; }
    public void setDefaultProfile(String defaultProfile) { this.defaultProfile = defaultProfile; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public boolean isEnableThinking() { return enableThinking; }
    public void setEnableThinking(boolean enableThinking) { this.enableThinking = enableThinking; }
    public Map<String, Profile> getProfiles() { return profiles; }
    public void setProfiles(Map<String, Profile> profiles) { this.profiles = profiles; }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public Profile resolveProfile(String name) {
        String key = (name == null || name.isBlank()) ? defaultProfile : name;
        return profiles.get(key);
    }

    public static class Profile {
        private String model;
        private int maxTokens = 512;
        private double temperature = 0.5;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
    }
}
