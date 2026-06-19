package com.showmethestory.model;

/**
 * View combining a skill definition with its current enabled state.
 * Used in API responses for the skills endpoint.
 */
public class SkillView {

    private Skill skill;
    private boolean enabled;

    public SkillView() {}

    public SkillView(Skill skill, boolean enabled) {
        this.skill = skill;
        this.enabled = enabled;
    }

    public Skill getSkill() { return skill; }
    public void setSkill(Skill skill) { this.skill = skill; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
