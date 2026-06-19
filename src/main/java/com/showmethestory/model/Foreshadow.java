package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Foreshadow {

    @JsonProperty("id")              private int id;
    @JsonProperty("name")            private String name;
    @JsonProperty("description")     private String description;
    @JsonProperty("plant_chapter")   private int plantChapter;
    @JsonProperty("target_chapter")  private int targetChapter;
    @JsonProperty("status")          private String status;
    @JsonProperty("events")          private List<ForeshadowEvent> events;
    @JsonProperty("resolution")      private String resolution;

    public static final String STATUS_PLANTED     = "planted";
    public static final String STATUS_PROGRESSING = "progressing";
    public static final String STATUS_RESOLVED    = "resolved";
    public static final String STATUS_ABANDONED   = "abandoned";

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPlantChapter() { return plantChapter; }
    public void setPlantChapter(int plantChapter) { this.plantChapter = plantChapter; }
    public int getTargetChapter() { return targetChapter; }
    public void setTargetChapter(int targetChapter) { this.targetChapter = targetChapter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ForeshadowEvent> getEvents() { return events; }
    public void setEvents(List<ForeshadowEvent> events) { this.events = events; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
