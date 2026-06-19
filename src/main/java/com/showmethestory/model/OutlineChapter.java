package com.showmethestory.model;

/**
 * A single chapter in an outline response.
 */
public class OutlineChapter {

    private int num;
    private String title;
    private String outline;

    public OutlineChapter() {}

    public OutlineChapter(int num, String title, String outline) {
        this.num = num;
        this.title = title;
        this.outline = outline;
    }

    public int getNum() { return num; }
    public void setNum(int num) { this.num = num; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; }
}
