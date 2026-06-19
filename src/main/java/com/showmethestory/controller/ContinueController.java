package com.showmethestory.controller;

import com.showmethestory.model.ContinueAnalysis;
import com.showmethestory.service.ContinueService;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Continue (import existing content) endpoints.
 * Maps to Go handlers: PostContinueImport, PostContinueConfirm
 */
@RestController
@RequestMapping("/api/continue")
public class ContinueController {

    private final ContinueService continueService;
    private final ProjectService projectService;
    private final TaskManager taskManager;

    public ContinueController(ContinueService continueService, ProjectService projectService, TaskManager taskManager) {
        this.continueService = continueService;
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: PostContinueImport - async analysis of existing content for continuation.
     * Body: {content: "..."} - the existing story text to analyze
     * Returns 202 Accepted immediately. Analysis result is broadcast via SSE.
     * The analyzed content is cached in pendingContinueContent for later confirmation.
     */
    @PostMapping("/import")
    public ResponseEntity<?> importContinue(@RequestBody Map<String, String> body) {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        String content = body.get("content");
        if (content == null || content.isEmpty()) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "missing_content"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                continueService.analyzeContent(taskManager.getTaskContext(), content);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostContinueConfirm - confirm the continue analysis and import into the project.
     * Body: ContinueAnalysis (user-confirmed analysis result)
     * Requires: phase == "outline", pending content exists from prior import.
     * Clears pending content after import.
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmContinue(@RequestBody ContinueAnalysis analysis) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        var state = projectService.getProgress();
        if (!"outline".equals(state.getPhase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "continue_reset_first"));
        }

        if (!continueService.hasPendingContent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "continue_analyze_first"));
        }

        if (analysis.getChapters() == null || analysis.getChapters().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "analysis_no_chapters"));
        }

        return continueService.confirmAndImport(analysis);
    }
}
