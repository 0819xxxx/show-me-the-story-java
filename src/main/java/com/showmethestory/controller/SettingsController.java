package com.showmethestory.controller;

import com.showmethestory.model.Character;
import com.showmethestory.model.Organization;
import com.showmethestory.model.ProjectSettings;
import com.showmethestory.model.Relation;
import com.showmethestory.model.StoryConfig;
import com.showmethestory.model.WorldviewEntry;
import com.showmethestory.service.ProjectService;
import com.showmethestory.service.ReconcileService;
import com.showmethestory.service.SettingsService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Project settings endpoints: CRUD for characters, worldviews, organizations, relations,
 * plus settings reconciliation.
 * Maps to Go handlers: GetSettings, PostSettingsReconcile, PostSettingsAIGenerate, PostSettingsPolish,
 *   PostCharacter, PutCharacter, DeleteCharacter,
 *   PostWorldview, PutWorldview, DeleteWorldview,
 *   PostOrganization, PutOrganization, DeleteOrganization,
 *   PostRelation, PutRelation, DeleteRelation
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private final SettingsService settingsService;
    private final ProjectService projectService;
    private final ReconcileService reconcileService;
    private final TaskManager taskManager;

    public SettingsController(SettingsService settingsService, ProjectService projectService,
                              ReconcileService reconcileService, TaskManager taskManager) {
        this.settingsService = settingsService;
        this.projectService = projectService;
        this.reconcileService = reconcileService;
        this.taskManager = taskManager;
    }

    // ---- Settings root ----

    /**
     * Go: GetSettings - returns the full ProjectSettings object.
     */
    @GetMapping("/settings")
    public ResponseEntity<ProjectSettings> getSettings() {
        return ResponseEntity.ok(projectService.getSettings());
    }

    /**
     * Go: PostSettingsReconcile - async AI-driven settings reconciliation.
     * Body: StoryConfig
     * Returns 202 Accepted immediately.
     */
    @PostMapping("/settings/reconcile")
    public ResponseEntity<?> reconcileSettings(@RequestBody StoryConfig body) {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        CompletableFuture.runAsync(() -> {
            try {
                reconcileService.reconcileSettings(taskManager.getTaskContext(), body);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * Go: PostSettingsAIGenerate - deprecated, returns 410 Gone.
     */
    @PostMapping("/settings/ai-generate")
    public ResponseEntity<?> aiGenerateSettings() {
        return ResponseEntity.status(410).body(Map.of("error", "settings_ai_generate_moved"));
    }

    /**
     * Go: PostSettingsPolish - deprecated, returns 410 Gone.
     */
    @PostMapping("/settings/polish")
    public ResponseEntity<?> polishSettings() {
        return ResponseEntity.status(410).body(Map.of("error", "settings_polish_moved"));
    }

    // ---- Characters ----

    /**
     * Go: PostCharacter - create a new character.
     * Body: Character {name, age, appearance, personality, background, motivation, abilities, notes}
     * Validates name is non-empty. Auto-assigns ID.
     */
    @PostMapping("/characters")
    public ResponseEntity<?> createCharacter(@RequestBody Character character) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        if (character.getName() == null || character.getName().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "character_name_empty"));
        }
        return settingsService.createCharacter(character);
    }

    /**
     * Go: PutCharacter - update an existing character by ID.
     * Path variable: {id}
     * Body: partial Character fields; only non-empty fields are applied.
     */
    @PutMapping("/characters/{id}")
    public ResponseEntity<?> updateCharacter(@PathVariable String id, @RequestBody Character character) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.updateCharacter(id, character);
    }

    /**
     * Go: DeleteCharacter - delete a character by ID.
     */
    @DeleteMapping("/characters/{id}")
    public ResponseEntity<?> deleteCharacter(@PathVariable String id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.deleteCharacter(id);
    }

    // ---- Worldview ----

    /**
     * Go: PostWorldview - create a new worldview entry.
     * Body: WorldviewEntry {category, name, description, tags}
     * Validates name and description are non-empty.
     */
    @PostMapping("/worldview")
    public ResponseEntity<?> createWorldview(@RequestBody WorldviewEntry worldview) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        if (worldview.getName() == null || worldview.getName().isEmpty()
                || worldview.getDescription() == null || worldview.getDescription().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "worldview_field_empty"));
        }
        return settingsService.createWorldview(worldview);
    }

    /**
     * Go: PutWorldview - update an existing worldview entry by ID.
     * Path variable: {id}
     */
    @PutMapping("/worldview/{id}")
    public ResponseEntity<?> updateWorldview(@PathVariable String id, @RequestBody WorldviewEntry worldview) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.updateWorldview(id, worldview);
    }

    /**
     * Go: DeleteWorldview - delete a worldview entry by ID.
     */
    @DeleteMapping("/worldview/{id}")
    public ResponseEntity<?> deleteWorldview(@PathVariable String id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.deleteWorldview(id);
    }

    // ---- Organizations ----

    /**
     * Go: PostOrganization - create a new organization.
     * Body: Organization {name, type, description, members}
     * Validates name is non-empty.
     */
    @PostMapping("/organizations")
    public ResponseEntity<?> createOrganization(@RequestBody Organization organization) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        if (organization.getName() == null || organization.getName().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "organization_name_empty"));
        }
        return settingsService.createOrganization(organization);
    }

    /**
     * Go: PutOrganization - update an existing organization by ID.
     * Path variable: {id}
     */
    @PutMapping("/organizations/{id}")
    public ResponseEntity<?> updateOrganization(@PathVariable String id, @RequestBody Organization organization) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.updateOrganization(id, organization);
    }

    /**
     * Go: DeleteOrganization - delete an organization by ID.
     */
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<?> deleteOrganization(@PathVariable String id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.deleteOrganization(id);
    }

    // ---- Relations ----

    /**
     * Go: PostRelation - create a new relation between entities.
     * Body: Relation {source_id, source_type, target_id, target_type, label}
     * Validates source_id and target_id are non-empty.
     */
    @PostMapping("/relations")
    public ResponseEntity<?> createRelation(@RequestBody Relation relation) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        if (relation.getSourceId() == null || relation.getSourceId().isEmpty()
                || relation.getTargetId() == null || relation.getTargetId().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "relation_endpoints_empty"));
        }
        return settingsService.createRelation(relation);
    }

    /**
     * Go: PutRelation - update an existing relation by ID.
     * Path variable: {id}
     */
    @PutMapping("/relations/{id}")
    public ResponseEntity<?> updateRelation(@PathVariable String id, @RequestBody Relation relation) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.updateRelation(id, relation);
    }

    /**
     * Go: DeleteRelation - delete a relation by ID.
     */
    @DeleteMapping("/relations/{id}")
    public ResponseEntity<?> deleteRelation(@PathVariable String id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return settingsService.deleteRelation(id);
    }
}
