package com.showmethestory.controller;

import com.showmethestory.model.EditChapterContentRequest;
import com.showmethestory.model.Progress;
import com.showmethestory.service.ChapterService;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Chapter generation, confirmation, revision, editing, polishing, and deletion endpoints.
 * Maps to Go handlers: PostChapterGenerate, GetChapterConflict, PostChapterConflictResolve,
 *   PostChapterConfirm, PostChapterEdit, PostChapterRevise, PostChapterReviseSpecific,
 *   PostChapterPolish, PostChaptersSmoothTransitions, DeleteChapter, DeleteChaptersFrom
 */
@RestController
@RequestMapping("/api")
public class ChapterController {

    private final ChapterService chapterService;
    private final ProjectService projectService;
    private final TaskManager taskManager;

    public ChapterController(ChapterService chapterService, ProjectService projectService, TaskManager taskManager) {
        this.chapterService = chapterService;
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: PostChapterGenerate - async chapter generation.
     * Enters a loop: generates chapter, optionally auto-confirms and continues to next chapter
     * if autoConfirm mode is on. Returns 202 Accepted immediately.
     */
    @PostMapping("/chapter/generate")
    public ResponseEntity<?> generateChapter() {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                chapterService.generateChapter(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: GetChapterConflict - returns the current pending writing conflict (if any).
     * Response: {conflict: WritingConflict | null}
     */
    @GetMapping("/chapter/conflict")
    public ResponseEntity<Map<String, Object>> getChapterConflict() {
        Progress state = projectService.getProgress();
        return ResponseEntity.ok(Map.of("conflict",
                state.getPendingWritingConflict() != null ? state.getPendingWritingConflict() : Map.of()));
    }

    /**
     * Go: PostChapterConflictResolve - resolve a writing conflict.
     * Body: {action: "force_review" | "dismiss" | "retry"}
     * Actions:
     * - force_review: set chapter to review status, clear conflict
     * - dismiss: clear conflict only
     * - retry: clear conflict and signal retry
     */
    @PostMapping("/chapter/conflict-resolve")
    public ResponseEntity<?> resolveConflict(@RequestBody Map<String, String> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }
        return chapterService.resolveConflict(body);
    }

    /**
     * Go: PostForeshadowOutlineCheck - async foreshadow outline consistency check.
     * Requires non-empty foreshadows list. Returns 202 Accepted.
     */
    @PostMapping("/foreshadows/outline-check")
    public ResponseEntity<?> checkForeshadowConsistency() {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        Progress state = projectService.getProgress();
        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "no_foreshadows_to_check"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                chapterService.runForeshadowOutlineCheck(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostChapterConfirm - confirm the current chapter (transition from "review" to "accepted").
     * Rejects if task is running or phase is not "writing".
     */
    @PostMapping("/chapter/confirm")
    public ResponseEntity<?> confirmChapter() {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        Progress state = projectService.getProgress();
        if (!"writing".equals(state.getPhase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "phase_not_writing"));
        }

        return chapterService.confirmChapter();
    }

    /**
     * Go: PostChapterEdit - surgical edit of chapter content.
     * Body: EditChapterContentRequest {num, operation, start_line, end_line, old_text, line, new_text}
     * Operations: replace_lines, replace_text, insert_after_line, append
     * Rejects if task is running. Saves progress and markdown after edit.
     */
    @PostMapping("/chapter/edit")
    public ResponseEntity<?> editChapterContent(@RequestBody EditChapterContentRequest request) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        if (request.getOperation() == null || request.getOperation().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "chapter_edit_op_required"));
        }
        if ((request.getNewText() == null || request.getNewText().isEmpty())
                && !"replace_text".equals(request.getOperation())) {
            return ResponseEntity.badRequest().body(Map.of("error", "chapter_edit_text_required"));
        }

        return chapterService.editChapterContent(request);
    }

    /**
     * Go: PostChapterRevise - async chapter revision with user feedback.
     * Body: {feedback: "..."}
     * Returns 202 Accepted immediately.
     */
    @PostMapping("/chapter/revise")
    public ResponseEntity<?> reviseChapter(@RequestBody Map<String, String> body) {
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
                chapterService.reviseChapter(taskManager.getTaskContext(), feedback);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostChapterReviseSpecific - async targeted revision of a specific chapter by number.
     * Path variable: {num} - chapter number to revise
     * Body: {feedback: "..."}
     * Only modifies the specified chapter's content and summary, not other chapters.
     */
    @PostMapping("/chapter/revise/{num}")
    public ResponseEntity<?> reviseSpecificChapter(@PathVariable int num, @RequestBody Map<String, String> body) {
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
                chapterService.reviseSpecificChapter(taskManager.getTaskContext(), num, feedback);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostChapterPolish - async chapter polishing using enabled polish skills.
     * Body: {num: N} (optional, defaults to current chapter)
     * Requires at least one enabled polish skill. Chapter must have content and not be in "writing" status.
     * Preserves accepted status after polish.
     */
    @PostMapping("/chapter/polish")
    public ResponseEntity<?> polishChapter(@RequestBody(required = false) Map<String, Integer> body) {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }

        return chapterService.polishChapter(body, taskManager);
    }

    /**
     * Go: PostChaptersSmoothTransitions - async batch smoothing of transitions between confirmed chapters.
     * Counts adjacent accepted chapter pairs; rejects if none found.
     * Returns 202 Accepted.
     */
    @PostMapping("/chapters/smooth-transitions")
    public ResponseEntity<?> smoothTransitions() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }

        // Count adjacent accepted chapter pairs
        Progress state = projectService.getProgress();
        int pairs = 0;
        var chapters = state.getChapters();
        for (int i = 1; i < chapters.size(); i++) {
            if ("accepted".equals(chapters.get(i).getStatus())
                    && !chapters.get(i).getContent().isEmpty()
                    && "accepted".equals(chapters.get(i - 1).getStatus())
                    && !chapters.get(i - 1).getContent().isEmpty()) {
                pairs++;
            }
        }
        if (pairs == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_transitions_to_optimize"));
        }

        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                chapterService.smoothTransitions(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: DeleteChapter - delete the last chapter (reset to pending).
     * Rejects if task is running or the last chapter is in "writing" status.
     * Clears content, summary; resets status to "pending".
     */
    @DeleteMapping("/chapter")
    public ResponseEntity<?> deleteChapter() {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "delete_chapter_locked"));
        }

        return chapterService.deleteLastChapter();
    }

    /**
     * Go: DeleteChaptersFrom - delete all chapters from the specified number onward.
     * Path variable: {num} - starting chapter number
     * Rejects if any chapter in the range is in "writing" status.
     */
    @DeleteMapping("/chapters/from/{num}")
    public ResponseEntity<?> deleteChaptersFrom(@PathVariable int num) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "delete_chapter_locked"));
        }

        return chapterService.deleteChaptersFrom(num);
    }
}
