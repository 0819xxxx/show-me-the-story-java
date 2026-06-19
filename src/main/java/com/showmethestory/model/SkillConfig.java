package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Skill enable/disable configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillConfig {

    @JsonProperty("enabled_skills")
    private Map<String, Boolean> enabledSkills = new HashMap<>();

    public Map<String, Boolean> getEnabledSkills() { return enabledSkills; }
    public void setEnabledSkills(Map<String, Boolean> enabledSkills) { this.enabledSkills = enabledSkills; }
}
