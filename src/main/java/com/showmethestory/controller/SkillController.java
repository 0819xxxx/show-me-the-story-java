package com.showmethestory.controller;

import com.showmethestory.service.SkillService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Skill listing and toggle endpoints.
 * Maps to Go handlers: GetSkills, PutSkillToggle
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final TaskManager taskManager;

    public SkillController(SkillService skillService, TaskManager taskManager) {
        this.skillService = skillService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetSkills - returns list of all skills with their enabled status.
     * Response: [{skill: Skill, enabled: bool}, ...]
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getSkills() {
        return ResponseEntity.ok(skillService.getSkillsWithStatus());
    }

    /**
     * Go: PutSkillToggle - toggle a skill's enabled/disabled state.
     * Path variable: {id} - skill ID
     * Body: {enabled: bool}
     * Rejects if task is running. Persists config after toggle.
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleSkill(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }

        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_json"));
        }

        return skillService.toggleSkill(id, enabled);
    }
}
