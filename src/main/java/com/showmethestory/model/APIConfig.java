package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API configuration for connecting to an OpenAI-compatible endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class APIConfig {

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("base_url")
    private String baseUrl;

    @JsonProperty("model")
    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    @JsonProperty("http_timeout_seconds")
    private int httpTimeoutSeconds = 300;

    @JsonProperty("context_budget_tokens")
    private int contextBudgetTokens = 300000;

    @JsonProperty("temperature")
    private double temperature = 0.7;

    @JsonProperty("timeout")
    private int timeout = 300;

    public APIConfig() {}

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getHttpTimeoutSeconds() { return httpTimeoutSeconds; }
    public void setHttpTimeoutSeconds(int httpTimeoutSeconds) { this.httpTimeoutSeconds = httpTimeoutSeconds; }

    public int getContextBudgetTokens() { return contextBudgetTokens; }
    public void setContextBudgetTokens(int contextBudgetTokens) { this.contextBudgetTokens = contextBudgetTokens; }
}
