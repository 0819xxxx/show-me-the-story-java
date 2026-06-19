package com.showmethestory.controller;

import com.showmethestory.model.APIConfig;
import com.showmethestory.model.Config;
import com.showmethestory.service.ConfigService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Configuration endpoints (API config + project config).
 * Maps to Go handlers: GetAPIConfig, PutAPIConfig, PostAPITest, GetConfig, PutConfig
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

    private final ConfigService configService;
    private final TaskManager taskManager;

    public ConfigController(ConfigService configService, TaskManager taskManager) {
        this.configService = configService;
        this.taskManager = taskManager;
    }

    // ---- API Config (global, always available) ----

    /**
     * Go: GetAPIConfig - returns the global API configuration.
     */
    @GetMapping("/config/api")
    public ResponseEntity<APIConfig> getAPIConfig() {
        return ResponseEntity.ok(configService.getAPIConfig());
    }

    /**
     * Go: PutAPIConfig - update global API configuration.
     * Rejects if task is running. Validates and persists the new config.
     * Applies defaults for HTTPTimeoutSeconds and ContextBudgetTokens.
     */
    @PutMapping("/config/api")
    public ResponseEntity<?> updateAPIConfig(@RequestBody APIConfig newCfg) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return configService.updateAPIConfig(newCfg);
    }

    /**
     * Go: PostAPITest - test API connectivity with the provided config.
     * Rejects if task is running. Sends a "Hi" test message with 15s timeout.
     */
    @PostMapping("/config/api/test")
    public ResponseEntity<?> testAPIConfig(@RequestBody APIConfig testCfg) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return configService.testAPIConnection(testCfg);
    }

    // ---- Project Config (project-scoped) ----

    /**
     * Go: GetConfig - returns the current project's config.
     */
    @GetMapping("/config")
    public ResponseEntity<Config> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    /**
     * Go: PutConfig - update current project's config.
     * Rejects if task is running. Applies defaults for chapter count, words per chapter,
     * language normalization, and prompt defaults.
     */
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Config newCfg) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return configService.updateConfig(newCfg);
    }

    /**
     * Go: (implicit) Returns default config values for the frontend to use.
     * Added to match the user's spec: GET /api/config/defaults
     */
    @GetMapping("/config/defaults")
    public ResponseEntity<Config> getDefaultConfig() {
        return ResponseEntity.ok(configService.getDefaultConfig());
    }
}
