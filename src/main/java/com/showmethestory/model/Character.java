package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Character {

    @JsonProperty("id")          private String id;
    @JsonProperty("name")        private String name;
    @JsonProperty("age")         private String age;
    @JsonProperty("appearance")  private String appearance;
    @JsonProperty("personality") private String personality;
    @JsonProperty("background")  private String background;
    @JsonProperty("motivation")  private String motivation;
    @JsonProperty("abilities")   private String abilities;
    @JsonProperty("notes")       private String notes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }
    public String getPersonality() { return personality; }
    public void setPersonality(String personality) { this.personality = personality; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getMotivation() { return motivation; }
    public void setMotivation(String motivation) { this.motivation = motivation; }
    public String getAbilities() { return abilities; }
    public void setAbilities(String abilities) { this.abilities = abilities; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
