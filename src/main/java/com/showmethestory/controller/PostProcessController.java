package com.showmethestory.controller;

import com.showmethestory.service.PostProcessService;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Post-process (whole-book optimization) endpoints.
 * Maps to Go handlers: GetPostProcess, DeletePostProcess, PutPostProcessRoadmap,
 *   PostPostProcessDiagnose, PostPostProcessConsistency, PostPostProcessRoadmap,
 *   PostPostProcessExecute
 */
@RestController
@RequestMapping("/api/postprocess")
public class PostProcessController {

    private final PostProcessService postProcessService;
    private final ProjectService projectService;
    private final TaskManager taskManager;

    public PostProcessController(PostProcessService postProcessService, ProjectService projectService, TaskManager taskManager) {
        this.postProcessService = postProcessService;
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetPostProcess - returns the post-process state including book_complete flag.
     * Response: {book_complete: bool, state: PostProcessState}
     */
    @GetMapping
    public ResponseEntity<?> getPostProcess() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        return ResponseEntity.ok(postProcessService.getPostProcessState());
    }

    /**
     * Go: DeletePostProcess - clear the post-process report and roadmap.
     * Resets to default state with RunSmoothTransitionsFirst=true.
     */
    @DeleteMapping
    public ResponseEntity<?> deletePostProcess() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return postProcessService.clearPostProcess();
    }

    /**
     * Go: PutPostProcessRoadmap - update roadmap items (toggle selection, edit feedback).
     * Body: {roadmap: [...], execute_options: {...}}
     * Partial update: only non-null fields are applied.
     */
    @PutMapping("/roadmap")
    public ResponseEntity<?> updateRoadmap(@RequestBody Map<String, Object> body) {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return postProcessService.updateRoadmap(body);
    }

    /**
     * Go: PostPostProcessDiagnose - async full-book diagnosis + consistency check + roadmap generation.
     * Requires all chapters to be in "accepted" status (book complete).
     * Returns 202 Accepted immediately.
     */
    @PostMapping("/diagnose")
    public ResponseEntity<?> diagnoseBook() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (!postProcessService.isBookComplete()) {
            return ResponseEntity.badRequest().body(Map.of("error", "book_not_complete"));
        }
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                postProcessService.fullDiagnose(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostPostProcessConsistency - async consistency-only check.
     * Requires book complete. Returns 202 Accepted.
     */
    @PostMapping("/consistency")
    public ResponseEntity<?> consistencyCheck() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (!postProcessService.isBookComplete()) {
            return ResponseEntity.badRequest().body(Map.of("error", "book_not_complete"));
        }
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                postProcessService.consistencyCheck(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostPostProcessRoadmap - async roadmap generation from existing reports.
     * Requires at least one of diagnosis_report or consistency_report to be present.
     * Returns 202 Accepted.
     */
    @PostMapping("/roadmap")
    public ResponseEntity<?> buildRoadmap() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (!postProcessService.hasReports()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_diagnosis_or_consistency"));
        }
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                postProcessService.buildRoadmap(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostPostProcessExecute - async execution of selected roadmap items.
     * Requires book complete and at least one selected pending roadmap item.
     * Body (optional): {execute_options: PostProcessExecuteOptions}
     * Returns 202 Accepted.
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeRoadmap(@RequestBody(required = false) Map<String, Object> body) {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        if (!postProcessService.isBookComplete()) {
            return ResponseEntity.badRequest().body(Map.of("error", "book_not_complete"));
        }
        if (!postProcessService.hasSelectedItems()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_at_least_one_item"));
        }

        // Apply execute_options from body if present
        if (body != null && body.containsKey("execute_options")) {
            postProcessService.applyExecuteOptions(body.get("execute_options"));
        }

        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                postProcessService.executeRoadmap(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
