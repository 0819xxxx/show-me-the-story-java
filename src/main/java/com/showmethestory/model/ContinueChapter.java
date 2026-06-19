package com.showmethestory.model;

/**
 * A single chapter entry in a continuation analysis result.
 */
public class ContinueChapter {

    private int num;
    private String title;
    private String outline;
    private String summary;
    private String content;

    public ContinueChapter() {}

    public int getNum() { return num; }
    public void setNum(int num) { this.num = num; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
