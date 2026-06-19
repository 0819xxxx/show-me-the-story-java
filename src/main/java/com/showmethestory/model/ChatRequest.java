package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Chat completions request payload sent to the LLM API.
 */
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private boolean stream;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    @JsonProperty("max_tokens")
    private int maxTokens;

    public ChatRequest() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }

    public StreamOptions getStreamOptions() { return streamOptions; }
    public void setStreamOptions(StreamOptions streamOptions) { this.streamOptions = streamOptions; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    /**
     * Stream options for token usage reporting.
     */
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private boolean includeUsage;

        public StreamOptions() {}

        public boolean isIncludeUsage() { return includeUsage; }
        public void setIncludeUsage(boolean includeUsage) { this.includeUsage = includeUsage; }
    }
}
