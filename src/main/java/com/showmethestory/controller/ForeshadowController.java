package com.showmethestory.controller;

import com.showmethestory.model.Foreshadow;
import com.showmethestory.service.ForeshadowService;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Foreshadow management endpoints (CRUD, suggest, confirm, roadmap, outline-check).
 * Maps to Go handlers: GetForeshadows, GetForeshadowsRoadmap, PostForeshadowsSuggest,
 *   PostForeshadowsConfirm, PostForeshadow, PutForeshadow, DeleteForeshadow
 */
@RestController
@RequestMapping("/api/foreshadows")
public class ForeshadowController {

    private final ForeshadowService foreshadowService;
    private final ProjectService projectService;
    private final TaskManager taskManager;

    public ForeshadowController(ForeshadowService foreshadowService, ProjectService projectService, TaskManager taskManager) {
        this.foreshadowService = foreshadowService;
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetForeshadows - returns the list of foreshadows (empty array if null).
     */
    @GetMapping
    public ResponseEntity<List<Foreshadow>> getForeshadows() {
        List<Foreshadow> foreshadows = foreshadowService.getForeshadows();
        return ResponseEntity.ok(foreshadows != null ? foreshadows : List.of());
    }

    /**
     * Go: GetForeshadowsRoadmap - returns the foreshadow roadmap markdown and file path.
     * Response: {markdown: "...", path: "..."}
     */
    @GetMapping("/roadmap")
    public ResponseEntity<?> getForeshadowRoadmap() {
        if (!projectService.ensureProject()) {
            return ResponseEntity.badRequest().body(Map.of("error", "select_project_first"));
        }
        return ResponseEntity.ok(foreshadowService.buildRoadmap());
    }

    /**
     * Go: PostForeshadowsSuggest - async AI suggestion of foreshadows.
     * Requires non-empty chapters list. Returns 202 Accepted.
     * Suggestions are broadcast via SSE when complete.
     */
    @PostMapping("/suggest")
    public ResponseEntity<?> suggestForeshadows() {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        var state = projectService.getProgress();
        if (state.getChapters() == null || state.getChapters().isEmpty()) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "need_generate_outline_first"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                foreshadowService.suggestForeshadows(taskManager.getTaskContext());
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostForeshadowsConfirm - confirm (import) a batch of foreshadow suggestions.
     * Body: {foreshadows: [...]}
     * Assigns new IDs, sets status to "planted", appends to existing list.
     * Triggers async foreshadow outline check in background.
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmForeshadows(@RequestBody Map<String, List<Foreshadow>> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        List<Foreshadow> foreshadows = body.get("foreshadows");
        if (foreshadows == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_json"));
        }

        return foreshadowService.confirmForeshadows(foreshadows);
    }

    /**
     * Go: PostForeshadow - create a single new foreshadow.
     * Body: {name, description, plant_chapter, target_chapter}
     * Rejects if task is running. Validates name and description are non-empty.
     */
    @PostMapping
    public ResponseEntity<?> createForeshadow(@RequestBody Map<String, Object> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "foreshadow_name_required"));
        }
        String description = (String) body.get("description");
        if (description == null || description.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "foreshadow_desc_required"));
        }

        int plantChapter = body.containsKey("plant_chapter") ? ((Number) body.get("plant_chapter")).intValue() : 0;
        int targetChapter = body.containsKey("target_chapter") ? ((Number) body.get("target_chapter")).intValue() : 0;

        return foreshadowService.createForeshadow(name, description, plantChapter, targetChapter);
    }

    /**
     * Go: PutForeshadow - update an existing foreshadow by ID.
     * Path variable: {id}
     * Body: partial update fields {name, description, plant_chapter, target_chapter, status, resolution}
     * Only non-empty/non-zero fields are applied.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateForeshadow(@PathVariable int id, @RequestBody Map<String, Object> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        return foreshadowService.updateForeshadow(id, body);
    }

    /**
     * Go: DeleteForeshadow - delete a foreshadow by ID.
     * Path variable: {id}
     * Rejects if task is running. Persists roadmap after deletion.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteForeshadow(@PathVariable int id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        return foreshadowService.deleteForeshadow(id);
    }
}
