package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WritingConflict {

    @JsonProperty("chapter_index")     private int chapterIndex;
    @JsonProperty("chapter_num")       private int chapterNum;
    @JsonProperty("chapter_title")     private String chapterTitle;
    @JsonProperty("issues")            private List<String> issues;
    @JsonProperty("summary")           private String summary;
    @JsonProperty("root_cause")        private String rootCause;
    @JsonProperty("reconcilable")     private boolean reconcilable;
    @JsonProperty("suggested_actions") private List<ConflictActionOption> suggestedActions;

    public int getChapterIndex() { return chapterIndex; }
    public void setChapterIndex(int chapterIndex) { this.chapterIndex = chapterIndex; }
    public int getChapterNum() { return chapterNum; }
    public void setChapterNum(int chapterNum) { this.chapterNum = chapterNum; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public boolean isReconcilable() { return reconcilable; }
    public void setReconcilable(boolean reconcilable) { this.reconcilable = reconcilable; }
    public List<ConflictActionOption> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<ConflictActionOption> suggestedActions) { this.suggestedActions = suggestedActions; }
}
