package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Story configuration: type, title, chapter count, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoryConfig {

    @JsonProperty("type")
    private String type;

    @JsonProperty("title")
    private String title;

    @JsonProperty("chapter_count")
    private int chapterCount;

    @JsonProperty("target_words_per_chapter")
    private int targetWordsPerChapter;

    @JsonProperty("writing_style")
    private String writingStyle;

    @JsonProperty("writing_pov")
    private String writingPov;

    @JsonProperty("story_synopsis")
    private String storySynopsis;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getChapterCount() { return chapterCount; }
    public void setChapterCount(int chapterCount) { this.chapterCount = chapterCount; }

    public int getTargetWordsPerChapter() { return targetWordsPerChapter; }
    public void setTargetWordsPerChapter(int targetWordsPerChapter) { this.targetWordsPerChapter = targetWordsPerChapter; }

    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String writingStyle) { this.writingStyle = writingStyle; }

    public String getWritingPov() { return writingPov; }
    public void setWritingPov(String writingPov) { this.writingPov = writingPov; }

    public String getStorySynopsis() { return storySynopsis; }
    public void setStorySynopsis(String storySynopsis) { this.storySynopsis = storySynopsis; }
}
