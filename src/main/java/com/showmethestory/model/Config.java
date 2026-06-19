package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Root project configuration (config.json).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    @JsonProperty("language")
    private String language = "zh";

    @JsonProperty("story")
    private StoryConfig story = new StoryConfig();

    @JsonProperty("prompts")
    private PromptsConfig prompts = new PromptsConfig();

    @JsonProperty("skill_config")
    private SkillConfig skillConfig;

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public StoryConfig getStory() { return story; }
    public void setStory(StoryConfig story) { this.story = story; }

    public PromptsConfig getPrompts() { return prompts; }
    public void setPrompts(PromptsConfig prompts) { this.prompts = prompts; }

    public SkillConfig getSkillConfig() { return skillConfig; }
    public void setSkillConfig(SkillConfig skillConfig) { this.skillConfig = skillConfig; }
}
