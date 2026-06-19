package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root progress state for a novel project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Progress {

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("title")
    private String title;

    @JsonProperty("core_prompt")
    private String corePrompt;

    @JsonProperty("story_synopsis")
    private String storySynopsis;

    @JsonProperty("chapters")
    private List<ChapterState> chapters;

    @JsonProperty("current_chapter_index")
    private int currentChapterIndex;

    @JsonProperty("story_config_snapshot")
    private StoryConfig storyConfigSnapshot;

    @JsonProperty("foreshadows")
    private List<Foreshadow> foreshadows;

    @JsonProperty("last_foreshadow_outline_report")
    private ForeshadowOutlineReport lastForeshadowOutlineReport;

    @JsonProperty("pending_writing_conflict")
    private WritingConflict pendingWritingConflict;

    // Getters and setters
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCorePrompt() { return corePrompt; }
    public void setCorePrompt(String corePrompt) { this.corePrompt = corePrompt; }

    public String getStorySynopsis() { return storySynopsis; }
    public void setStorySynopsis(String storySynopsis) { this.storySynopsis = storySynopsis; }

    public List<ChapterState> getChapters() { return chapters; }
    public void setChapters(List<ChapterState> chapters) { this.chapters = chapters; }

    public int getCurrentChapterIndex() { return currentChapterIndex; }
    public void setCurrentChapterIndex(int currentChapterIndex) { this.currentChapterIndex = currentChapterIndex; }

    public StoryConfig getStoryConfigSnapshot() { return storyConfigSnapshot; }
    public void setStoryConfigSnapshot(StoryConfig storyConfigSnapshot) { this.storyConfigSnapshot = storyConfigSnapshot; }

    public List<Foreshadow> getForeshadows() { return foreshadows; }
    public void setForeshadows(List<Foreshadow> foreshadows) { this.foreshadows = foreshadows; }

    public ForeshadowOutlineReport getLastForeshadowOutlineReport() { return lastForeshadowOutlineReport; }
    public void setLastForeshadowOutlineReport(ForeshadowOutlineReport r) { this.lastForeshadowOutlineReport = r; }

    public WritingConflict getPendingWritingConflict() { return pendingWritingConflict; }
    public void setPendingWritingConflict(WritingConflict pendingWritingConflict) { this.pendingWritingConflict = pendingWritingConflict; }
}
