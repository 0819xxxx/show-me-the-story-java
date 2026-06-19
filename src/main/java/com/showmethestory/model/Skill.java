package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A skill definition loaded from a .md skill file (builtin or project-specific).
 */
public class Skill {

    private String id;
    private String name;
    private String description;
    private String category;
    private String lang;
    private String content;
    private boolean enabled;
    private String source;

    public Skill() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
