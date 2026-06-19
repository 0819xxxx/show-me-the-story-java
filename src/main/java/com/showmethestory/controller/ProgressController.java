package com.showmethestory.controller;

import com.showmethestory.model.Progress;
import com.showmethestory.service.ProgressService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Progress, status, and version endpoints.
 * Maps to Go handlers: GetProgress, DeleteProgress, GetStatus, GetVersion
 */
@RestController
@RequestMapping("/api")
public class ProgressController {

    private final ProgressService progressService;
    private final TaskManager taskManager;

    public ProgressController(ProgressService progressService, TaskManager taskManager) {
        this.progressService = progressService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetProgress - returns the full Progress state (phase, title, chapters, etc.).
     */
    @GetMapping("/progress")
    public ResponseEntity<Progress> getProgress() {
        return ResponseEntity.ok(progressService.getProgress());
    }

    /**
     * Go: DeleteProgress - resets progress by deleting the progress file.
     * Rejects if task is running. Resets to initial state {phase: "outline"}.
     */
    @DeleteMapping("/progress")
    public ResponseEntity<?> deleteProgress() {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "reset_progress_locked"));
        }
        return progressService.deleteProgress();
    }

    /**
     * Go: GetStatus - returns a lightweight status summary:
     * {phase, title, total_chapters, is_task_running, auto_confirm, project_language, token_usage?}
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(progressService.getStatus());
    }

    /**
     * Go: GetVersion - returns {version: "..."}.
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of("version", progressService.getVersion()));
    }
}
