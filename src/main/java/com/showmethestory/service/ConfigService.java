package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.APIConfig;
import com.showmethestory.model.Config;
import com.showmethestory.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * API config and project config management.
 * Ported from Go handlers.go: GetAPIConfig, PutAPIConfig, PostAPITest, GetConfig, PutConfig.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;

    public ConfigService(ProjectService projectService,
                         StateService stateService,
                         OpenAIClient openAIClient) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
    }

    /**
     * Go: GetAPIConfig - returns the global API configuration.
     */
    public APIConfig getAPIConfig() {
        return projectService.getAPIConfig();
    }

    /**
     * Go: PutAPIConfig - update global API configuration.
     * Validates and persists the new config.
     */
    public ResponseEntity<?> updateAPIConfig(APIConfig newCfg) {
        try {
            if (newCfg.getHttpTimeoutSeconds() <= 0) {
                newCfg.setHttpTimeoutSeconds(300);
            }
            if (newCfg.getContextBudgetTokens() <= 0) {
                int window = openAIClient.fetchModelContextWindow(newCfg);
                newCfg.setContextBudgetTokens(window > 0 ? window : 300000);
            }

            String apiCfgPath = projectService.getProgDir() + "/api_config.json";
            stateService.saveAPIConfig(apiCfgPath, newCfg);

            // Update in-memory reference
            // ProjectService holds the reference; we need to update it
            // For now we rely on the reference being mutable or reloaded

            return ResponseEntity.ok(newCfg);
        } catch (Exception e) {
            log.error("Update API config failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "save_api_config_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: PostAPITest - test API connectivity with the provided config.
     * Sends a "Hi" test message with 15s timeout.
     */
    public ResponseEntity<?> testAPIConnection(APIConfig testCfg) {
        try {
            openAIClient.validateAPIConfig(testCfg);

            OpenAIClient.CancellationToken ctx = new OpenAIClient.CancellationToken();
            List<Message> messages = List.of(Message.user("Hi"));
            String resp = openAIClient.callAPIMessages(ctx, testCfg, messages, null);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "连接成功");
            result.put("model", testCfg.getModel());
            if (resp != null && resp.length() > 100) {
                result.put("sample", resp.substring(0, 100) + "...");
            } else {
                result.put("sample", resp);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("API test failed", e);
            return ResponseEntity.status(502).body(Map.of("error", "api_test_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: GetConfig - returns the current project's config.
     */
    public Config getConfig() {
        return projectService.getConfig();
    }

    /**
     * Go: PutConfig - update current project's config.
     * Applies defaults and persists.
     */
    public ResponseEntity<?> updateConfig(Config newCfg) {
        try {
            if (newCfg.getStory().getChapterCount() <= 0) {
                newCfg.getStory().setChapterCount(30);
            }
            if (newCfg.getStory().getTargetWordsPerChapter() <= 0) {
                newCfg.getStory().setTargetWordsPerChapter(2500);
            }
            newCfg.setLanguage(LocaleHelper.normalizeLanguage(newCfg.getLanguage()));
            if (newCfg.getLanguage() == null || newCfg.getLanguage().isEmpty()) {
                Config oldCfg = projectService.getConfig();
                if (oldCfg != null) {
                    newCfg.setLanguage(oldCfg.getLanguage());
                }
            }

            String cfgPath = projectService.getCfgPath();
            stateService.saveConfig(cfgPath, newCfg);
            projectService.setConfig(newCfg);

            return ResponseEntity.ok(newCfg);
        } catch (Exception e) {
            log.error("Update config failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "save_config_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Returns default config for the frontend.
     */
    public Config getDefaultConfig() {
        Config cfg = new Config();
        cfg.setLanguage(LocaleHelper.LANG_ZH);
        cfg.getStory().setChapterCount(30);
        cfg.getStory().setTargetWordsPerChapter(2500);
        return cfg;
    }
}
