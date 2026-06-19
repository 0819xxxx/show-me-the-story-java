package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Context object passed through the agent loop, holding references to all
 * project state needed by tools.
 * <p>
 * Note: In Go, this struct also carries function references (StartAsync) and
 * internal transient fields. In Java those are handled via service injection
 * and are not part of the POJO model.
 */
public class AgentContext {

    @JsonProperty("api_cfg")
    private APIConfig apiCfg;

    private ProjectSettings settings;

    @JsonProperty("settings_path")
    private String settingsPath;

    private Progress state;
    private Config config;
    private List<Skill> skills;

    @JsonProperty("context_page")
    private String contextPage;

    @JsonProperty("progress_path")
    private String progressPath;

    @JsonProperty("cfg_path")
    private String cfgPath;

    @JsonProperty("sessions_dir")
    private String sessionsDir;

    @JsonProperty("project_dir")
    private String projectDir;

    public AgentContext() {}

    public APIConfig getApiCfg() { return apiCfg; }
    public void setApiCfg(APIConfig apiCfg) { this.apiCfg = apiCfg; }

    public ProjectSettings getSettings() { return settings; }
    public void setSettings(ProjectSettings settings) { this.settings = settings; }

    public String getSettingsPath() { return settingsPath; }
    public void setSettingsPath(String settingsPath) { this.settingsPath = settingsPath; }

    public Progress getState() { return state; }
    public void setState(Progress state) { this.state = state; }

    public Config getConfig() { return config; }
    public void setConfig(Config config) { this.config = config; }

    public List<Skill> getSkills() { return skills; }
    public void setSkills(List<Skill> skills) { this.skills = skills; }

    public String getContextPage() { return contextPage; }
    public void setContextPage(String contextPage) { this.contextPage = contextPage; }

    public String getProgressPath() { return progressPath; }
    public void setProgressPath(String progressPath) { this.progressPath = progressPath; }

    public String getCfgPath() { return cfgPath; }
    public void setCfgPath(String cfgPath) { this.cfgPath = cfgPath; }

    public String getSessionsDir() { return sessionsDir; }
    public void setSessionsDir(String sessionsDir) { this.sessionsDir = sessionsDir; }

    public String getProjectDir() { return projectDir; }
    public void setProjectDir(String projectDir) { this.projectDir = projectDir; }
}
