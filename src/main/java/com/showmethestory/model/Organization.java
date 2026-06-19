package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {

    @JsonProperty("id")          private String id;
    @JsonProperty("name")        private String name;
    @JsonProperty("type")        private String type;
    @JsonProperty("description") private String description;
    @JsonProperty("members")     private List<String> members;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
