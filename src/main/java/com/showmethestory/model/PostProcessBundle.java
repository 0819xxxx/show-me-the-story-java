package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bundle of material assembled for post-process analysis (not persisted).
 */
public class PostProcessBundle {

    private String settingsText;
    private String summaryIndex;
    private String fullText;
    private int totalRunes;
    private int estimatedTokens;
    private String mode;

    @JsonProperty("volume_count")
    private int volumeCount;

    public PostProcessBundle() {}

    public String getSettingsText() { return settingsText; }
    public void setSettingsText(String settingsText) { this.settingsText = settingsText; }

    public String getSummaryIndex() { return summaryIndex; }
    public void setSummaryIndex(String summaryIndex) { this.summaryIndex = summaryIndex; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public int getTotalRunes() { return totalRunes; }
    public void setTotalRunes(int totalRunes) { this.totalRunes = totalRunes; }

    public int getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(int estimatedTokens) { this.estimatedTokens = estimatedTokens; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getVolumeCount() { return volumeCount; }
    public void setVolumeCount(int volumeCount) { this.volumeCount = volumeCount; }
}
