package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI-generated outline response (used for new outlines and continuations).
 */
public class OutlineResponse {

    private String title;

    @JsonProperty("core_prompt")
    private String corePrompt;

    @JsonProperty("story_synopsis")
    private String storySynopsis;

    private List<OutlineChapter> chapters;

    public OutlineResponse() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCorePrompt() { return corePrompt; }
    public void setCorePrompt(String corePrompt) { this.corePrompt = corePrompt; }

    public String getStorySynopsis() { return storySynopsis; }
    public void setStorySynopsis(String storySynopsis) { this.storySynopsis = storySynopsis; }

    public List<OutlineChapter> getChapters() { return chapters; }
    public void setChapters(List<OutlineChapter> chapters) { this.chapters = chapters; }
}
