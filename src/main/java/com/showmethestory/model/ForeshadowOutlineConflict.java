package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ForeshadowOutlineConflict {

    @JsonProperty("foreshadow_id")   private int foreshadowId;
    @JsonProperty("foreshadow_name") private String foreshadowName;
    @JsonProperty("conflict_type")   private String conflictType;
    @JsonProperty("description")     private String description;
    @JsonProperty("suggested_fix")   private String suggestedFix;

    public int getForeshadowId() { return foreshadowId; }
    public void setForeshadowId(int foreshadowId) { this.foreshadowId = foreshadowId; }
    public String getForeshadowName() { return foreshadowName; }
    public void setForeshadowName(String foreshadowName) { this.foreshadowName = foreshadowName; }
    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSuggestedFix() { return suggestedFix; }
    public void setSuggestedFix(String suggestedFix) { this.suggestedFix = suggestedFix; }
}
