package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI-suggested foreshadow plan.
 */
public class ForeshadowSuggestion {

    private String name;
    private String description;

    @JsonProperty("plant_chapter")
    private int plantChapter;

    @JsonProperty("target_chapter")
    private int targetChapter;

    public ForeshadowSuggestion() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPlantChapter() { return plantChapter; }
    public void setPlantChapter(int plantChapter) { this.plantChapter = plantChapter; }

    public int getTargetChapter() { return targetChapter; }
    public void setTargetChapter(int targetChapter) { this.targetChapter = targetChapter; }
}
