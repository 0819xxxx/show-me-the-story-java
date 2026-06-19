package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Project settings: characters, worldview, organizations, relations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSettings {

    @JsonProperty("characters")
    private List<Character> characters = new java.util.ArrayList<>();

    @JsonProperty("worldview")
    private List<WorldviewEntry> worldview = new java.util.ArrayList<>();

    @JsonProperty("organizations")
    private List<Organization> organizations = new java.util.ArrayList<>();

    @JsonProperty("relations")
    private List<Relation> relations = new java.util.ArrayList<>();

    public List<Character> getCharacters() { return characters; }
    public void setCharacters(List<Character> characters) { this.characters = characters; }

    public List<WorldviewEntry> getWorldview() { return worldview; }
    public void setWorldview(List<WorldviewEntry> worldview) { this.worldview = worldview; }

    public List<Organization> getOrganizations() { return organizations; }
    public void setOrganizations(List<Organization> organizations) { this.organizations = organizations; }

    public List<Relation> getRelations() { return relations; }
    public void setRelations(List<Relation> relations) { this.relations = relations; }
}
