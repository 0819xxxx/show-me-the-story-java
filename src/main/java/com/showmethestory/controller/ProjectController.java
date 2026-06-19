package com.showmethestory.controller;

import com.showmethestory.service.ProjectService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Project management endpoints.
 * Maps to Go handlers: GetProjects, PostProject, GetProjectCurrent, PostProjectSelect, DeleteProject
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final TaskManager taskManager;

    public ProjectController(ProjectService projectService, TaskManager taskManager) {
        this.projectService = projectService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetProjects - list all projects sorted by mod time descending.
     * Returns array of {name, phase, title, language, updated_at}.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, String>>> getProjects() {
        List<Map<String, String>> projects = projectService.listProjects();
        return ResponseEntity.ok(projects);
    }

    /**
     * Go: PostProject - create a new project directory with default config.
     * Body: {name, language}
     * Validates name for invalid chars, checks for duplicates.
     */
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_project_name"));
        }

        String language = request.getOrDefault("language", "zh");
        return projectService.createProject(name.trim(), language);
    }

    /**
     * Go: GetProjectCurrent - returns current selected project name and language.
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, String>> getCurrentProject() {
        Map<String, String> current = projectService.getCurrentProject();
        return ResponseEntity.ok(current);
    }

    /**
     * Go: PostProjectSelect - switch to the specified project.
     * Rejects if a task is currently running.
     * Body: {name}
     */
    @PostMapping("/select")
    public ResponseEntity<?> selectProject(@RequestBody Map<String, String> request) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        String name = request.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_project_name"));
        }

        return projectService.selectProject(name);
    }

    /**
     * Go: DeleteProject - delete a project directory.
     * Rejects if task is running or if it's the currently selected project.
     * Path variable: {name}
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteProject(@PathVariable String name) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "delete_project_locked"));
        }

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_project_name"));
        }

        return projectService.deleteProject(name);
    }
}
