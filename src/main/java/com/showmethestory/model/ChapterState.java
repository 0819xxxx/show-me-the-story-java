package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * State of a single chapter within the progress file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChapterState {

    public static final String STATUS_PENDING  = "pending";
    public static final String STATUS_WRITING  = "writing";
    public static final String STATUS_REVIEW   = "review";
    public static final String STATUS_ACCEPTED = "accepted";

    @JsonProperty("num")
    private int num;

    @JsonProperty("title")
    private String title;

    @JsonProperty("outline")
    private String outline;

    @JsonProperty("content")
    private String content;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("status")
    private String status;

    public ChapterState() {}

    public int getNum() { return num; }
    public void setNum(int num) { this.num = num; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
