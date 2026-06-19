package com.showmethestory.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.model.APIConfig;
import com.showmethestory.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI-compatible API client with SSE streaming, retry logic,
 * token tracking, and context cancellation support.
 */
@Service
public class OpenAIClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectMapper getObjectMapper() { return MAPPER; }

    /** Pattern to strip markdown code fences wrapping JSON responses. */
    private static final Pattern JSON_FENCE = Pattern.compile(
            "^\\s*```(?:json)?\\s*\\n?(.*?)\\n?\\s*```\\s*$", Pattern.DOTALL);

    private final WebClient webClient;

    public OpenAIClient() {
        this.webClient = WebClient.builder().build();
    }

    // ---------------------------------------------------------------
    // Cancellation token – the Java equivalent of Go's context.Context
    // ---------------------------------------------------------------

    /**
     * Lightweight cancellation token that replaces Go's context.Context.
     * Pass to API methods so long-running operations can be aborted.
     */
    public static class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        public void cancel() { cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
    }

    // ---------------------------------------------------------------
    // URL normalisation
    // ---------------------------------------------------------------

    /**
     * Build the full /chat/completions URL from the base URL.
     * Appends /v1 if missing (unless port 11434 is detected for Ollama).
     */
    static String normalizeURL(String base) {
        base = base.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1") && !base.contains("/v1/")) {
            if (!base.contains("11434")) {
                base = base + "/v1";
            }
        }
        return base + "/chat/completions";
    }

    // ---------------------------------------------------------------
    // Error classification
    // ---------------------------------------------------------------

    /**
     * Determine if an error is fatal (should not retry).
     */
    public static boolean isFatalError(Throwable err) {
        if (err == null) return false;
        String msg = err.getMessage();
        if (msg == null) return false;
        // Connection refused, DNS failure
        if (msg.contains("connection refused") || msg.contains("no such host")
                || msg.contains("Connection refused") || msg.contains("UnknownHost")) {
            return true;
        }
        // HTTP 401/403/404
        if (msg.contains("status code: 401") || msg.contains("status code: 403")
                || msg.contains("status code: 404") || msg.contains("401")
                || msg.contains("403") || msg.contains("404")) {
            if (err instanceof WebClientResponseException wce) {
                int code = wce.getStatusCode().value();
                if (code == 401 || code == 403 || code == 404) return true;
            }
        }
        // Cancelled
        if (msg.contains("cancelled") || msg.contains("canceled")) {
            return true;
        }
        return false;
    }

    /**
     * Validate that the API configuration has the minimum required fields.
     */
    public static void validateAPIConfig(APIConfig apiCfg) throws IllegalArgumentException {
        if (apiCfg.getBaseUrl() == null || apiCfg.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("API Base URL not configured");
        }
        if (apiCfg.getModel() == null || apiCfg.getModel().trim().isEmpty()) {
            throw new IllegalArgumentException("Model not configured");
        }
    }

    // ---------------------------------------------------------------
    // Wait time for retry (exponential backoff capped at 30s)
    // ---------------------------------------------------------------

    static int getWaitTime(int retry) {
        if (retry > 6) return 30;
        return retry * 5;
    }

    // ---------------------------------------------------------------
    // Non-streaming API call (synchronous fallback)
    // ---------------------------------------------------------------

    /**
     * Call the API with system+user prompts and return the assistant content.
     */
    public String callAPI(CancellationToken ct, APIConfig apiCfg,
                          String systemPrompt, String userPrompt,
                          TokenTracker tracker) {
        return callAPIMessages(ct, apiCfg, List.of(
                Message.system(systemPrompt),
                Message.user(userPrompt)
        ), tracker);
    }

    /**
     * Call the API with a full message list.
     * Tries streaming first; falls back to sync if streaming fails.
     */
    public String callAPIMessages(CancellationToken ct, APIConfig apiCfg,
                                  List<Message> messages, TokenTracker tracker) {
        // Try streaming first
        String result = callAPIStreamMessages(ct, apiCfg, messages, null, tracker);
        if (result != null && !result.isEmpty()) {
            return result;
        }
        if (ct != null && ct.isCancelled()) {
            return result != null ? result : "";
        }
        if (result != null && !result.isEmpty()) {
            return result;
        }
        // Sync fallback
        return callAPIMessagesSync(ct, apiCfg, messages, tracker);
    }

    /**
     * Call the API with retry logic (non-streaming, system+user).
     */
    public String callAPIWithRetry(CancellationToken ct, APIConfig apiCfg,
                                   String systemPrompt, String userPrompt,
                                   LogBroadcaster logger, TokenTracker tracker) {
        int retryCount = 0;
        while (true) {
            if (ct != null && ct.isCancelled()) return "";
            String result = callAPI(ct, apiCfg, systemPrompt, userPrompt, tracker);
            if (result != null && !result.isEmpty()) return result;

            // If there was a fatal error, stop
            // (we don't have the exact exception here, so just check result)
            retryCount++;
            int waitTime = getWaitTime(retryCount);
            if (logger != null) {
                logger.warnKey("log.api_retry", "API call failed", retryCount, waitTime);
            }
            if (!sleepOrCancelled(ct, waitTime)) return "";
        }
    }

    /**
     * Call the API with streaming and retry logic (system+user).
     */
    public String callAPIStreamWithRetry(CancellationToken ct, APIConfig apiCfg,
                                         String systemPrompt, String userPrompt,
                                         Consumer<String> onChunk,
                                         LogBroadcaster logger, TokenTracker tracker) {
        int retryCount = 0;
        while (true) {
            if (ct != null && ct.isCancelled()) return "";
            String result = callAPIStream(ct, apiCfg, systemPrompt, userPrompt, onChunk, tracker);
            if (result != null && !result.isEmpty()) return result;

            retryCount++;
            int waitTime = getWaitTime(retryCount);
            if (logger != null) {
                logger.warnKey("log.api_stream_retry", "Stream API call failed", retryCount, waitTime);
            }
            if (!sleepOrCancelled(ct, waitTime)) return "";
        }
    }

    // ---------------------------------------------------------------
    // Streaming API call
    // ---------------------------------------------------------------

    /**
     * Convenience: streaming call with system+user prompts.
     */
    public String callAPIStream(CancellationToken ct, APIConfig apiCfg,
                                String systemPrompt, String userPrompt,
                                Consumer<String> onChunk, TokenTracker tracker) {
        return callAPIStreamMessages(ct, apiCfg, List.of(
                Message.system(systemPrompt),
                Message.user(userPrompt)
        ), onChunk, tracker);
    }

    /**
     * Streaming API call with a full message list.
     * This is the primary streaming method.
     */
    public String callAPIStreamMessages(CancellationToken ct, APIConfig apiCfg,
                                        List<Message> messages,
                                        Consumer<String> onChunk,
                                        TokenTracker tracker) {
        String fullURL = normalizeURL(apiCfg.getBaseUrl());
        if (tracker != null) {
            tracker.beginCall(messages);
        }

        try {
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", apiCfg.getModel());
            reqBody.put("messages", messages);
            reqBody.put("stream", true);
            reqBody.put("stream_options", Map.of("include_usage", true));
            if (apiCfg.getMaxTokens() > 0) {
                reqBody.put("max_tokens", apiCfg.getMaxTokens());
            }

            String jsonBody = MAPPER.writeValueAsString(reqBody);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiCfg.getApiKey() != null && !apiCfg.getApiKey().isEmpty()) {
                headers.setBearerAuth(apiCfg.getApiKey());
            }

            Duration timeout = Duration.ofSeconds(
                    apiCfg.getHttpTimeoutSeconds() > 0 ? apiCfg.getHttpTimeoutSeconds() : 300);

            // Use WebClient for streaming SSE
            StringBuilder fullContent = new StringBuilder();
            int[] promptTokens = {0};
            int[] completionTokens = {0};
            boolean[] hasUsage = {false};

            String responseBody = webClient.post()
                    .uri(fullURL)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (responseBody == null || responseBody.isEmpty()) {
                return "";
            }

            // Parse SSE lines
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (ct != null && ct.isCancelled()) {
                    return fullContent.toString();
                }
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;

                try {
                    JsonNode delta = MAPPER.readTree(data);

                    // Extract usage if present
                    JsonNode usageNode = delta.get("usage");
                    if (usageNode != null && !usageNode.isNull()) {
                        JsonNode pt = usageNode.get("prompt_tokens");
                        JsonNode ct2 = usageNode.get("completion_tokens");
                        if (pt != null) promptTokens[0] = pt.asInt();
                        if (ct2 != null) completionTokens[0] = ct2.asInt();
                        hasUsage[0] = true;
                    }

                    // Extract content delta
                    JsonNode choices = delta.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode deltaNode = choices.get(0).get("delta");
                        if (deltaNode != null) {
                            JsonNode contentNode = deltaNode.get("content");
                            if (contentNode != null && !contentNode.isNull()) {
                                String chunk = contentNode.asText();
                                if (!chunk.isEmpty()) {
                                    fullContent.append(chunk);
                                    if (tracker != null) {
                                        tracker.updateStreamContent(fullContent.toString());
                                    }
                                    if (onChunk != null) {
                                        onChunk.accept(chunk);
                                    }
                                }
                            }
                        }
                    }
                } catch (JsonProcessingException ignored) {
                    // Skip unparseable lines
                }
            }

            String result = fullContent.toString();
            if (result.isEmpty()) {
                throw new RuntimeException("Empty streaming response");
            }

            if (tracker != null) {
                tracker.finishCall(promptTokens[0], completionTokens[0],
                        hasUsage[0], messages, result);
            }
            return result;

        } catch (Exception e) {
            if (ct != null && ct.isCancelled()) {
                return "";
            }
            throw new RuntimeException("API streaming call failed: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Synchronous (non-streaming) fallback
    // ---------------------------------------------------------------

    private String callAPIMessagesSync(CancellationToken ct, APIConfig apiCfg,
                                       List<Message> messages, TokenTracker tracker) {
        String fullURL = normalizeURL(apiCfg.getBaseUrl());
        if (tracker != null) {
            tracker.beginCall(messages);
        }

        try {
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", apiCfg.getModel());
            reqBody.put("messages", messages);
            if (apiCfg.getMaxTokens() > 0) {
                reqBody.put("max_tokens", apiCfg.getMaxTokens());
            }

            String jsonBody = MAPPER.writeValueAsString(reqBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiCfg.getApiKey() != null && !apiCfg.getApiKey().isEmpty()) {
                headers.setBearerAuth(apiCfg.getApiKey());
            }

            Duration timeout = Duration.ofSeconds(
                    apiCfg.getHttpTimeoutSeconds() > 0 ? apiCfg.getHttpTimeoutSeconds() : 300);

            String responseBody = webClient.post()
                    .uri(fullURL)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            if (responseBody == null) {
                throw new RuntimeException("Empty API response");
            }

            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                if (tracker != null) {
                    JsonNode usageNode = root.get("usage");
                    if (usageNode != null && !usageNode.isNull()) {
                        int pt = usageNode.path("prompt_tokens").asInt();
                        int ct2 = usageNode.path("completion_tokens").asInt();
                        tracker.finishCall(pt, ct2, true, messages, content);
                    } else {
                        tracker.finishCall(0, 0, false, messages, content);
                    }
                }
                return content;
            }

            throw new RuntimeException("API returned no valid choices");

        } catch (Exception e) {
            throw new RuntimeException("API sync call failed: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Fetch model context window
    // ---------------------------------------------------------------

    /**
     * Try to fetch the model's context window from the /models endpoint.
     * Returns 0 on failure (caller should use a default).
     */
    public int fetchModelContextWindow(APIConfig apiCfg) {
        if (apiCfg == null
                || apiCfg.getBaseUrl() == null || apiCfg.getBaseUrl().trim().isEmpty()
                || apiCfg.getModel() == null || apiCfg.getModel().trim().isEmpty()) {
            return 0;
        }
        try {
            String base = apiCfg.getBaseUrl().trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (!base.endsWith("/v1") && !base.contains("/v1/")) {
                if (!base.contains("11434")) {
                    base = base + "/v1";
                }
            }
            String modelsURL = base + "/models/" + apiCfg.getModel();

            HttpHeaders headers = new HttpHeaders();
            if (apiCfg.getApiKey() != null && !apiCfg.getApiKey().isEmpty()) {
                headers.setBearerAuth(apiCfg.getApiKey());
            }

            String body = webClient.get()
                    .uri(modelsURL)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (body == null) return 0;
            JsonNode root = MAPPER.readTree(body);
            JsonNode cw = root.get("context_window");
            if (cw != null && cw.asInt() > 0) {
                return cw.asInt();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    // ---------------------------------------------------------------
    // JSON cleaning utilities
    // ---------------------------------------------------------------

    /**
     * Strip markdown code fences from a JSON response string.
     * Many LLMs wrap JSON output in ```json ... ``` blocks.
     */
    public static String cleanJSONFences(String raw) {
        if (raw == null) return null;
        Matcher m = JSON_FENCE.matcher(raw);
        if (m.matches()) {
            return m.group(1).trim();
        }
        return raw.trim();
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Sleep for the given number of seconds, returning false if cancelled.
     */
    private boolean sleepOrCancelled(CancellationToken ct, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (ct != null && ct.isCancelled()) return false;
            try {
                long remaining = deadline - System.currentTimeMillis();
                Thread.sleep(Math.min(remaining, 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
