package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ForeshadowEvent {

    @JsonProperty("chapter") private int chapter;
    @JsonProperty("note")    private String note;

    public ForeshadowEvent() {}

    public ForeshadowEvent(int chapter, String note) {
        this.chapter = chapter;
        this.note = note;
    }

    public int getChapter() { return chapter; }
    public void setChapter(int chapter) { this.chapter = chapter; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
