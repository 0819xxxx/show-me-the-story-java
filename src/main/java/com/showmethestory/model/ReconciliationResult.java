package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of settings reconciliation between new config and existing accepted chapters.
 */
public class ReconciliationResult {

    private String type;

    @JsonProperty("writing_style")
    private String writingStyle;

    @JsonProperty("writing_pov")
    private String writingPov;

    @JsonProperty("story_synopsis")
    private String storySynopsis;

    private String explanation;

    public ReconciliationResult() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String writingStyle) { this.writingStyle = writingStyle; }

    public String getWritingPov() { return writingPov; }
    public void setWritingPov(String writingPov) { this.writingPov = writingPov; }

    public String getStorySynopsis() { return storySynopsis; }
    public void setStorySynopsis(String storySynopsis) { this.storySynopsis = storySynopsis; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
