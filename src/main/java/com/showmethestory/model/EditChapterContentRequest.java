package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for surgical chapter content editing (replace lines, replace text, insert, append).
 */
public class EditChapterContentRequest {

    @JsonProperty("num")
    private int chapterNum;

    private String operation;

    @JsonProperty("start_line")
    private int startLine;

    @JsonProperty("end_line")
    private int endLine;

    @JsonProperty("old_text")
    private String oldText;

    private int line;

    @JsonProperty("new_text")
    private String newText;

    public EditChapterContentRequest() {}

    public int getChapterNum() { return chapterNum; }
    public void setChapterNum(int chapterNum) { this.chapterNum = chapterNum; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }

    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }

    public String getOldText() { return oldText; }
    public void setOldText(String oldText) { this.oldText = oldText; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getNewText() { return newText; }
    public void setNewText(String newText) { this.newText = newText; }
}
