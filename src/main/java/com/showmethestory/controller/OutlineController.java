package com.showmethestory.controller;

import com.showmethestory.model.Progress;
import com.showmethestory.service.OutlineService;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Outline generation, confirmation, revision, and continuation endpoints.
 * Maps to Go handlers: PostOutlineGenerate, PostOutlineConfirm, PostOutlineRevise,
 *   PostOutlineGenerateContinuation, PutChapterOutline, DeleteOutline
 */
@RestController
@RequestMapping("/api/outline")
public class OutlineController {

    private final OutlineService outlineService;
    private final ProjectService projectService;
    private final TaskManager taskManager;

    public OutlineController(OutlineService outlineService, ProjectService projectService, TaskManager taskManager) {
        this.outlineService = outlineService;
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: PostOutlineGenerate - async outline generation.
     * Checks for writing/accepted chapters (rejects if present).
     * Clears old pending chapters before generating.
     * Returns 202 Accepted immediately, work proceeds in background goroutine.
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateOutline() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }

        // Check for writing/review or accepted chapters
        Progress state = projectService.getProgress();
        for (var ch : state.getChapters()) {
            if ("writing".equals(ch.getStatus()) || "review".equals(ch.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "writing_chapter_present"));
            }
            if ("accepted".equals(ch.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "accepted_chapter_present"));
            }
        }

        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                outlineService.generateOutline(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostOutlineConfirm - confirm the generated outline, transitioning to "writing" phase.
     * Rejects if task is running, phase is not "outline", or chapters list is empty.
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmOutline() {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        Progress state = projectService.getProgress();
        if (!"outline".equals(state.getPhase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "phase_not_outline"));
        }
        if (state.getChapters() == null || state.getChapters().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "outline_empty"));
        }

        return outlineService.confirmOutline();
    }

    /**
     * Go: PostOutlineRevise - async outline revision with user feedback.
     * Body: {feedback: "..."}
     * Returns 202 Accepted, revision proceeds in background.
     */
    @PostMapping("/revise")
    public ResponseEntity<?> reviseOutline(@RequestBody Map<String, String> body) {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        String feedback = body.get("feedback");
        if (feedback == null || feedback.isEmpty()) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "missing_feedback"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                outlineService.reviseOutline(taskManager.getTaskContext(), feedback);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostOutlineGenerateContinuation - async continuation outline generation.
     * Body: {chapter_count: N} (defaults to 5)
     * Requires phase == "outline".
     */
    @PostMapping("/generate-continuation")
    public ResponseEntity<?> generateContinuation(@RequestBody(required = false) Map<String, Integer> body) {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        Progress state = projectService.getProgress();
        if (!"outline".equals(state.getPhase())) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "phase_not_outline"));
        }

        int chapterCount = (body != null && body.containsKey("chapter_count") && body.get("chapter_count") > 0)
                ? body.get("chapter_count") : 5;

        CompletableFuture.runAsync(() -> {
            try {
                outlineService.generateContinuationOutline(taskManager.getTaskContext(), chapterCount);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PutChapterOutline - edit a specific chapter's outline (title + outline text).
     * Path variable: {num} - chapter number
     * Body: {title, outline}
     * Triggers async foreshadow outline check in background.
     */
    @PutMapping("/{num}")
    public ResponseEntity<?> editChapterOutline(@PathVariable int num, @RequestBody Map<String, String> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        String title = body.get("title");
        String outline = body.get("outline");

        return outlineService.editChapterOutline(num, title, outline);
    }

    /**
     * Go: DeleteOutline - delete entire outline (all chapters, title, synopsis, etc.).
     * Rejects if task is running or any chapter is in writing/review status.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteOutline() {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "delete_outline_locked"));
        }

        return outlineService.deleteOutline();
    }
}
