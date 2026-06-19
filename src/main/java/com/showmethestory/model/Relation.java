package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Relation {

    @JsonProperty("id")          private String id;
    @JsonProperty("source_id")   private String sourceId;
    @JsonProperty("source_type") private String sourceType;
    @JsonProperty("target_id")   private String targetId;
    @JsonProperty("target_type") private String targetType;
    @JsonProperty("label")       private String label;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
