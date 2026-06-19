package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Analysis result from importing existing content for continuation.
 */
public class ContinueAnalysis {

    private String title;

    @JsonProperty("story_type")
    private String storyType;

    @JsonProperty("core_prompt")
    private String corePrompt;

    @JsonProperty("story_synopsis")
    private String storySynopsis;

    @JsonProperty("writing_style")
    private String writingStyle;

    @JsonProperty("writing_pov")
    private String writingPov;

    private List<ContinueChapter> chapters;

    public ContinueAnalysis() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStoryType() { return storyType; }
    public void setStoryType(String storyType) { this.storyType = storyType; }

    public String getCorePrompt() { return corePrompt; }
    public void setCorePrompt(String corePrompt) { this.corePrompt = corePrompt; }

    public String getStorySynopsis() { return storySynopsis; }
    public void setStorySynopsis(String storySynopsis) { this.storySynopsis = storySynopsis; }

    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String writingStyle) { this.writingStyle = writingStyle; }

    public String getWritingPov() { return writingPov; }
    public void setWritingPov(String writingPov) { this.writingPov = writingPov; }

    public List<ContinueChapter> getChapters() { return chapters; }
    public void setChapters(List<ContinueChapter> chapters) { this.chapters = chapters; }
}
