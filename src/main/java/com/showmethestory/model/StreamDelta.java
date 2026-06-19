package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A streaming delta chunk from the LLM API.
 */
public class StreamDelta {

    private List<DeltaChoice> choices;

    @JsonProperty("usage")
    private ChatResponse.TokenUsage usage;

    public StreamDelta() {}

    public List<DeltaChoice> getChoices() { return choices; }
    public void setChoices(List<DeltaChoice> choices) { this.choices = choices; }

    public ChatResponse.TokenUsage getUsage() { return usage; }
    public void setUsage(ChatResponse.TokenUsage usage) { this.usage = usage; }

    /**
     * A choice within a streaming delta.
     */
    public static class DeltaChoice {
        private DeltaContent delta;

        public DeltaChoice() {}

        public DeltaContent getDelta() { return delta; }
        public void setDelta(DeltaContent delta) { this.delta = delta; }
    }

    /**
     * The incremental content delta within a stream chunk.
     */
    public static class DeltaContent {
        private String content;

        public DeltaContent() {}

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
