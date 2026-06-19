package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single item in the post-process optimization roadmap.
 */
public class RoadmapItem {

    private String id;

    @JsonProperty("chapter_num")
    private int chapterNum;

    private String type;
    private String priority;
    private String feedback;
    private boolean selected;
    private String status;

    @JsonProperty("diff_original")
    private String diffOriginal;

    @JsonProperty("diff_revised")
    private String diffRevised;

    private String error;

    public RoadmapItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getChapterNum() { return chapterNum; }
    public void setChapterNum(int chapterNum) { this.chapterNum = chapterNum; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDiffOriginal() { return diffOriginal; }
    public void setDiffOriginal(String diffOriginal) { this.diffOriginal = diffOriginal; }

    public String getDiffRevised() { return diffRevised; }
    public void setDiffRevised(String diffRevised) { this.diffRevised = diffRevised; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
