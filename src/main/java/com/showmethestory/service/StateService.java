package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.i18n.PromptTemplates;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for loading and saving project state files (progress, config,
 * API config, settings). All writes go through FileSystemService for
 * atomic .tmp-then-rename semantics.
 */
@Service
public class StateService {

    private static final Logger log = LoggerFactory.getLogger(StateService.class);
    private static final int DEFAULT_CONTEXT_BUDGET_TOKENS = 300000;

    private final FileSystemService fs;
    private final ObjectMapper mapper;
    private final OpenAIClient openAIClient;

    public StateService(FileSystemService fs, OpenAIClient openAIClient) {
        this.fs = fs;
        this.openAIClient = openAIClient;
        this.mapper = new ObjectMapper();
    }

    // ---------------------------------------------------------------
    // Progress
    // ---------------------------------------------------------------

    /**
     * Load progress from disk. Returns null if the file does not exist.
     */
    public Progress loadProgress(String path) throws Exception {
        byte[] data = fs.readFile(path);
        if (data == null) return null;
        return mapper.readValue(data, Progress.class);
    }

    /**
     * Save progress to disk atomically.
     */
    public void saveProgress(String path, Progress progress) throws Exception {
        byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(progress);
        fs.writeFileAtomic(path, data);
    }

    // ---------------------------------------------------------------
    // API Config
    // ---------------------------------------------------------------

    /**
     * Load API config from disk. Creates a default config file if none exists.
     */
    public APIConfig loadAPIConfig(String path) throws Exception {
        byte[] data = fs.readFile(path);
        if (data == null) {
            APIConfig cfg = defaultAPIConfig();
            saveAPIConfig(path, cfg);
            return cfg;
        }
        APIConfig cfg = mapper.readValue(data, APIConfig.class);
        if (cfg.getHttpTimeoutSeconds() <= 0) {
            cfg.setHttpTimeoutSeconds(300);
        }
        if (cfg.getContextBudgetTokens() <= 0) {
            int window = openAIClient.fetchModelContextWindow(cfg);
            cfg.setContextBudgetTokens(window > 0 ? window : DEFAULT_CONTEXT_BUDGET_TOKENS);
        }
        return cfg;
    }

    /**
     * Save API config to disk atomically.
     */
    public void saveAPIConfig(String path, APIConfig config) throws Exception {
        byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
        fs.writeFileAtomic(path, data);
    }

    private APIConfig defaultAPIConfig() {
        APIConfig cfg = new APIConfig();
        cfg.setHttpTimeoutSeconds(300);
        cfg.setContextBudgetTokens(DEFAULT_CONTEXT_BUDGET_TOKENS);
        return cfg;
    }

    // ---------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------

    /**
     * Load project config from disk. Creates a default config file if none exists.
     * Also applies default prompts for the configured language and writes back
     * if any empty prompt fields were filled.
     */
    public Config loadConfig(String path) throws Exception {
        byte[] data = fs.readFile(path);
        if (data == null) {
            Config cfg = defaultConfig();
            saveConfig(path, cfg);
            return cfg;
        }
        Config cfg = mapper.readValue(data, Config.class);

        if (cfg.getStory().getChapterCount() <= 0) {
            cfg.getStory().setChapterCount(30);
        }
        if (cfg.getStory().getTargetWordsPerChapter() <= 0) {
            cfg.getStory().setTargetWordsPerChapter(2500);
        }

        cfg.setLanguage(LocaleHelper.normalizeLanguage(cfg.getLanguage()));

        // Snapshot prompts before applying defaults to detect changes
        PromptsConfig oldPrompts = mapper.readValue(
                mapper.writeValueAsString(cfg.getPrompts()), PromptsConfig.class);
        applyPromptDefaults(cfg.getPrompts(), cfg.getLanguage());

        // If any fields were filled, write back
        String oldJson = mapper.writeValueAsString(oldPrompts);
        String newJson = mapper.writeValueAsString(cfg.getPrompts());
        if (!oldJson.equals(newJson)) {
            saveConfig(path, cfg);
        }

        if (cfg.getSkillConfig() == null) {
            cfg.setSkillConfig(new SkillConfig());
        }

        return cfg;
    }

    /**
     * Save project config to disk atomically.
     */
    public void saveConfig(String path, Config config) throws Exception {
        byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
        fs.writeFileAtomic(path, data);
    }

    private Config defaultConfig() {
        return defaultConfigForLang(LocaleHelper.LANG_ZH);
    }

    private Config defaultConfigForLang(String lang) {
        lang = LocaleHelper.normalizeLanguage(lang);
        Config cfg = new Config();
        cfg.setLanguage(lang);
        cfg.getStory().setChapterCount(30);
        cfg.getStory().setTargetWordsPerChapter(2500);
        cfg.setSkillConfig(new SkillConfig());
        applyPromptDefaults(cfg.getPrompts(), lang);
        return cfg;
    }

    /**
     * Fill empty prompt fields with language-specific defaults.
     * Non-empty fields are NEVER overwritten.
     */
    private void applyPromptDefaults(PromptsConfig p, String lang) {
        PromptsConfig defaults = PromptTemplates.getDefaults(lang);

        if (isBlank(p.getOutlineGeneration()))
            p.setOutlineGeneration(defaults.getOutlineGeneration());
        if (isBlank(p.getChapterWriting()))
            p.setChapterWriting(defaults.getChapterWriting());
        if (isBlank(p.getChapterRevision()))
            p.setChapterRevision(defaults.getChapterRevision());
        if (isBlank(p.getChapterSummary()))
            p.setChapterSummary(defaults.getChapterSummary());
        if (isBlank(p.getFactCheck()))
            p.setFactCheck(defaults.getFactCheck());
        if (isBlank(p.getOutlineRevision()))
            p.setOutlineRevision(defaults.getOutlineRevision());
        if (isBlank(p.getForeshadowPlanning()))
            p.setForeshadowPlanning(defaults.getForeshadowPlanning());
        if (isBlank(p.getForeshadowUpdate()))
            p.setForeshadowUpdate(defaults.getForeshadowUpdate());
        if (isBlank(p.getContentAnalysis()))
            p.setContentAnalysis(defaults.getContentAnalysis());
        if (isBlank(p.getContinuationOutlineGeneration()))
            p.setContinuationOutlineGeneration(defaults.getContinuationOutlineGeneration());
        if (isBlank(p.getSettingsReconciliation()))
            p.setSettingsReconciliation(defaults.getSettingsReconciliation());
        if (isBlank(p.getTransitionSmoothing()))
            p.setTransitionSmoothing(defaults.getTransitionSmoothing());
        if (isBlank(p.getOutlineConsistencyCheck()))
            p.setOutlineConsistencyCheck(defaults.getOutlineConsistencyCheck());
        if (isBlank(p.getForeshadowOutlineConsistency()))
            p.setForeshadowOutlineConsistency(defaults.getForeshadowOutlineConsistency());
        if (isBlank(p.getWritingConflictAnalysis()))
            p.setWritingConflictAnalysis(defaults.getWritingConflictAnalysis());
        if (isBlank(p.getBookDiagnosis()))
            p.setBookDiagnosis(defaults.getBookDiagnosis());
        if (isBlank(p.getBookConsistencyCheck()))
            p.setBookConsistencyCheck(defaults.getBookConsistencyCheck());
        if (isBlank(p.getBookRoadmap()))
            p.setBookRoadmap(defaults.getBookRoadmap());
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    /**
     * Load project settings from disk. Returns an empty settings object if
     * the file does not exist.
     */
    public ProjectSettings loadSettings(String path) throws Exception {
        byte[] data = fs.readFile(path);
        if (data == null) return new ProjectSettings();
        return mapper.readValue(data, ProjectSettings.class);
    }

    /**
     * Save project settings to disk atomically.
     */
    public void saveSettings(String path, ProjectSettings settings) throws Exception {
        byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(settings);
        fs.writeFileAtomic(path, data);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
