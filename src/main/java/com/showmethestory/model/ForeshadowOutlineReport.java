package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ForeshadowOutlineReport {

    @JsonProperty("has_conflicts") private boolean hasConflicts;
    @JsonProperty("conflicts")     private List<ForeshadowOutlineConflict> conflicts;
    @JsonProperty("summary")       private String summary;

    public boolean isHasConflicts() { return hasConflicts; }
    public void setHasConflicts(boolean hasConflicts) { this.hasConflicts = hasConflicts; }
    public List<ForeshadowOutlineConflict> getConflicts() { return conflicts; }
    public void setConflicts(List<ForeshadowOutlineConflict> conflicts) { this.conflicts = conflicts; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
