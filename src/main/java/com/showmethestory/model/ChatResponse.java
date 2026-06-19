package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Chat completions response from the LLM API (non-streaming).
 */
public class ChatResponse {

    private List<Choice> choices;
    private TokenUsage usage;

    public ChatResponse() {}

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    public TokenUsage getUsage() { return usage; }
    public void setUsage(TokenUsage usage) { this.usage = usage; }

    /**
     * A single choice in the response.
     */
    public static class Choice {
        private Message message;

        public Choice() {}

        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    /**
     * Token usage statistics.
     */
    public static class TokenUsage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;

        public TokenUsage() {}

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }
}
