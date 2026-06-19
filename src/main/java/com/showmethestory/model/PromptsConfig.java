package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for all prompt templates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptsConfig {

    @JsonProperty("outline_generation")              private String outlineGeneration;
    @JsonProperty("chapter_writing")                 private String chapterWriting;
    @JsonProperty("chapter_revision")                private String chapterRevision;
    @JsonProperty("chapter_summary")                 private String chapterSummary;
    @JsonProperty("fact_check")                      private String factCheck;
    @JsonProperty("outline_revision")                private String outlineRevision;
    @JsonProperty("foreshadow_planning")             private String foreshadowPlanning;
    @JsonProperty("foreshadow_update")               private String foreshadowUpdate;
    @JsonProperty("content_analysis")                private String contentAnalysis;
    @JsonProperty("continuation_outline_generation") private String continuationOutlineGeneration;
    @JsonProperty("settings_reconciliation")         private String settingsReconciliation;
    @JsonProperty("transition_smoothing")            private String transitionSmoothing;
    @JsonProperty("outline_consistency_check")       private String outlineConsistencyCheck;
    @JsonProperty("foreshadow_outline_consistency")  private String foreshadowOutlineConsistency;
    @JsonProperty("writing_conflict_analysis")       private String writingConflictAnalysis;
    @JsonProperty("book_diagnosis")                  private String bookDiagnosis;
    @JsonProperty("book_consistency_check")          private String bookConsistencyCheck;
    @JsonProperty("book_roadmap")                    private String bookRoadmap;

    // Getters and setters
    public String getOutlineGeneration() { return outlineGeneration; }
    public void setOutlineGeneration(String v) { this.outlineGeneration = v; }

    public String getChapterWriting() { return chapterWriting; }
    public void setChapterWriting(String v) { this.chapterWriting = v; }

    public String getChapterRevision() { return chapterRevision; }
    public void setChapterRevision(String v) { this.chapterRevision = v; }

    public String getChapterSummary() { return chapterSummary; }
    public void setChapterSummary(String v) { this.chapterSummary = v; }

    public String getFactCheck() { return factCheck; }
    public void setFactCheck(String v) { this.factCheck = v; }

    public String getOutlineRevision() { return outlineRevision; }
    public void setOutlineRevision(String v) { this.outlineRevision = v; }

    public String getForeshadowPlanning() { return foreshadowPlanning; }
    public void setForeshadowPlanning(String v) { this.foreshadowPlanning = v; }

    public String getForeshadowUpdate() { return foreshadowUpdate; }
    public void setForeshadowUpdate(String v) { this.foreshadowUpdate = v; }

    public String getContentAnalysis() { return contentAnalysis; }
    public void setContentAnalysis(String v) { this.contentAnalysis = v; }

    public String getContinuationOutlineGeneration() { return continuationOutlineGeneration; }
    public void setContinuationOutlineGeneration(String v) { this.continuationOutlineGeneration = v; }

    public String getSettingsReconciliation() { return settingsReconciliation; }
    public void setSettingsReconciliation(String v) { this.settingsReconciliation = v; }

    public String getTransitionSmoothing() { return transitionSmoothing; }
    public void setTransitionSmoothing(String v) { this.transitionSmoothing = v; }

    public String getOutlineConsistencyCheck() { return outlineConsistencyCheck; }
    public void setOutlineConsistencyCheck(String v) { this.outlineConsistencyCheck = v; }

    public String getForeshadowOutlineConsistency() { return foreshadowOutlineConsistency; }
    public void setForeshadowOutlineConsistency(String v) { this.foreshadowOutlineConsistency = v; }

    public String getWritingConflictAnalysis() { return writingConflictAnalysis; }
    public void setWritingConflictAnalysis(String v) { this.writingConflictAnalysis = v; }

    public String getBookDiagnosis() { return bookDiagnosis; }
    public void setBookDiagnosis(String v) { this.bookDiagnosis = v; }

    public String getBookConsistencyCheck() { return bookConsistencyCheck; }
    public void setBookConsistencyCheck(String v) { this.bookConsistencyCheck = v; }

    public String getBookRoadmap() { return bookRoadmap; }
    public void setBookRoadmap(String v) { this.bookRoadmap = v; }
}
