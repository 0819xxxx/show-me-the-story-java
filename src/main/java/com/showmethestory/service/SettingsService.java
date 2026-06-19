package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.showmethestory.model.Character;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Project settings with character/worldview/organization/relation CRUD.
 * Maps to Go: settings.go -- LoadProjectSettings, SaveProjectSettings,
 * buildCharacterContext, buildWorldviewContext, nextID helpers.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final FileSystemService fileSystemService;
    private final ProjectService projectService;
    private final StateService stateService;
    private final ObjectMapper objectMapper;

    public SettingsService(FileSystemService fileSystemService, ProjectService projectService, StateService stateService) {
        this.fileSystemService = fileSystemService;
        this.projectService = projectService;
        this.stateService = stateService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ---------------------------------------------------------------
    // Load / Save
    // ---------------------------------------------------------------

    /**
     * Load project settings from the given path.
     * Returns an empty ProjectSettings if the file does not exist.
     */
    public ProjectSettings loadProjectSettings(String path) throws IOException {
        byte[] data = fileSystemService.readFile(path);
        if (data == null) {
            return new ProjectSettings();
        }
        return objectMapper.readValue(data, ProjectSettings.class);
    }

    /**
     * Save project settings to the given path atomically.
     */
    public void saveProjectSettings(String path, ProjectSettings settings) throws IOException {
        byte[] data = objectMapper.writeValueAsBytes(settings);
        fileSystemService.writeFileAtomic(path, data);
    }

    // ---------------------------------------------------------------
    // ID generation (prefix-based, scans all entity IDs)
    // ---------------------------------------------------------------

    /**
     * Generate the next ID with the given prefix, scanning all existing IDs.
     * Go equivalent: nextID(prefix, existingIDs).
     */
    public String nextID(String prefix, List<String> existingIDs) {
        int maxNum = 0;
        String prefixUnderscore = prefix + "_";
        for (String id : existingIDs) {
            if (id.startsWith(prefixUnderscore)) {
                String numStr = id.substring(prefixUnderscore.length());
                try {
                    int n = Integer.parseInt(numStr);
                    if (n > maxNum) maxNum = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + "_" + (maxNum + 1);
    }

    /**
     * Collect all entity IDs from the project settings.
     */
    public List<String> allIDs(ProjectSettings ps) {
        List<String> ids = new ArrayList<>();
        if (ps.getCharacters() != null) {
            for (Character c : ps.getCharacters()) ids.add(c.getId());
        }
        if (ps.getWorldview() != null) {
            for (WorldviewEntry w : ps.getWorldview()) ids.add(w.getId());
        }
        if (ps.getOrganizations() != null) {
            for (Organization o : ps.getOrganizations()) ids.add(o.getId());
        }
        if (ps.getRelations() != null) {
            for (Relation r : ps.getRelations()) ids.add(r.getId());
        }
        return ids;
    }

    public String nextCharacterID(ProjectSettings ps) {
        return nextID("c", allIDs(ps));
    }

    public String nextWorldviewID(ProjectSettings ps) {
        return nextID("w", allIDs(ps));
    }

    public String nextOrganizationID(ProjectSettings ps) {
        return nextID("o", allIDs(ps));
    }

    public String nextRelationID(ProjectSettings ps) {
        return nextID("r", allIDs(ps));
    }

    // ---------------------------------------------------------------
    // Context builders for prompt injection
    // ---------------------------------------------------------------

    /**
     * Build character context string for prompt injection.
     * Filters to characters mentioned in the chapter outline; falls back to all.
     */
    public String buildCharacterContext(ProjectSettings settings, String chapterOutline) {
        if (settings == null || settings.getCharacters() == null || settings.getCharacters().isEmpty()) {
            return "";
        }

        List<Character> relevant = new ArrayList<>();
        for (Character c : settings.getCharacters()) {
            if (chapterOutline != null && chapterOutline.contains(c.getName())) {
                relevant.add(c);
            }
        }
        if (relevant.isEmpty()) {
            relevant = settings.getCharacters();
        }

        StringBuilder sb = new StringBuilder();
        for (Character c : relevant) {
            sb.append("【").append(c.getName()).append("】");
            if (c.getAge() != null && !c.getAge().isEmpty()) {
                sb.append(" 年龄:").append(c.getAge());
            }
            sb.append("\n");
            if (c.getAppearance() != null && !c.getAppearance().isEmpty()) {
                sb.append("  外貌: ").append(c.getAppearance()).append("\n");
            }
            if (c.getPersonality() != null && !c.getPersonality().isEmpty()) {
                sb.append("  性格: ").append(c.getPersonality()).append("\n");
            }
            if (c.getBackground() != null && !c.getBackground().isEmpty()) {
                sb.append("  背景: ").append(c.getBackground()).append("\n");
            }
            if (c.getMotivation() != null && !c.getMotivation().isEmpty()) {
                sb.append("  动机: ").append(c.getMotivation()).append("\n");
            }
            if (c.getAbilities() != null && !c.getAbilities().isEmpty()) {
                sb.append("  能力: ").append(c.getAbilities()).append("\n");
            }
            if (c.getNotes() != null && !c.getNotes().isEmpty()) {
                sb.append("  备注: ").append(c.getNotes()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Build worldview + organization context string for prompt injection.
     */
    public String buildWorldviewContext(ProjectSettings settings, String chapterOutline) {
        if (settings == null) return "";

        StringBuilder sb = new StringBuilder();

        // Worldview entries
        if (settings.getWorldview() != null && !settings.getWorldview().isEmpty()) {
            boolean anyMatched = false;
            for (WorldviewEntry w : settings.getWorldview()) {
                if (chapterOutline != null
                        && (chapterOutline.contains(w.getName())
                        || chapterOutline.contains(w.getCategory()))) {
                    sb.append("【").append(w.getName()).append("】(")
                            .append(w.getCategory()).append(")\n  ")
                            .append(w.getDescription()).append("\n\n");
                    anyMatched = true;
                }
            }
            if (!anyMatched) {
                for (WorldviewEntry w : settings.getWorldview()) {
                    sb.append("【").append(w.getName()).append("】(")
                            .append(w.getCategory()).append(")\n  ")
                            .append(w.getDescription()).append("\n\n");
                }
            }
        }

        // Organizations
        if (settings.getOrganizations() != null && !settings.getOrganizations().isEmpty()) {
            List<Organization> relevantOrgs = new ArrayList<>();
            for (Organization o : settings.getOrganizations()) {
                if (chapterOutline != null && chapterOutline.contains(o.getName())) {
                    relevantOrgs.add(o);
                }
            }
            if (relevantOrgs.isEmpty()) {
                relevantOrgs = settings.getOrganizations();
            }
            for (Organization o : relevantOrgs) {
                sb.append("【组织:").append(o.getName()).append("】(")
                        .append(o.getType()).append(")\n  ")
                        .append(o.getDescription()).append("\n");
                if (o.getMembers() != null && !o.getMembers().isEmpty()) {
                    sb.append("  成员IDs: ").append(String.join(", ", o.getMembers())).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Character CRUD
    // ---------------------------------------------------------------

    public ResponseEntity<?> createCharacter(Character character) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getCharacters() == null) ps.setCharacters(new ArrayList<>());
            character.setId(nextCharacterID(ps));
            ps.getCharacters().add(character);
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(character);
        } catch (Exception e) {
            log.error("Create character failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "create_character_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> updateCharacter(String id, Character patch) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getCharacters() == null) return ResponseEntity.status(404).body(Map.of("error", "character_not_found"));
            for (Character c : ps.getCharacters()) {
                if (id.equals(c.getId())) {
                    if (patch.getName() != null && !patch.getName().isEmpty()) c.setName(patch.getName());
                    if (patch.getAge() != null && !patch.getAge().isEmpty()) c.setAge(patch.getAge());
                    if (patch.getAppearance() != null) c.setAppearance(patch.getAppearance());
                    if (patch.getPersonality() != null) c.setPersonality(patch.getPersonality());
                    if (patch.getBackground() != null) c.setBackground(patch.getBackground());
                    if (patch.getMotivation() != null) c.setMotivation(patch.getMotivation());
                    if (patch.getAbilities() != null) c.setAbilities(patch.getAbilities());
                    if (patch.getNotes() != null) c.setNotes(patch.getNotes());
                    projectService.setSettings(ps);
                    stateService.saveSettings(projectService.getSettingsPath(), ps);
                    return ResponseEntity.ok(c);
                }
            }
            return ResponseEntity.status(404).body(Map.of("error", "character_not_found"));
        } catch (Exception e) {
            log.error("Update character failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "update_character_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteCharacter(String id) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getCharacters() == null) return ResponseEntity.status(404).body(Map.of("error", "character_not_found"));
            boolean removed = ps.getCharacters().removeIf(c -> id.equals(c.getId()));
            if (!removed) return ResponseEntity.status(404).body(Map.of("error", "character_not_found"));
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete character failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_character_failed", "detail", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Worldview CRUD
    // ---------------------------------------------------------------

    public ResponseEntity<?> createWorldview(WorldviewEntry entry) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getWorldview() == null) ps.setWorldview(new ArrayList<>());
            entry.setId(nextWorldviewID(ps));
            ps.getWorldview().add(entry);
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(entry);
        } catch (Exception e) {
            log.error("Create worldview failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "create_worldview_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> updateWorldview(String id, WorldviewEntry patch) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getWorldview() == null) return ResponseEntity.status(404).body(Map.of("error", "worldview_not_found"));
            for (WorldviewEntry w : ps.getWorldview()) {
                if (id.equals(w.getId())) {
                    if (patch.getName() != null && !patch.getName().isEmpty()) w.setName(patch.getName());
                    if (patch.getCategory() != null) w.setCategory(patch.getCategory());
                    if (patch.getDescription() != null) w.setDescription(patch.getDescription());
                    projectService.setSettings(ps);
                    stateService.saveSettings(projectService.getSettingsPath(), ps);
                    return ResponseEntity.ok(w);
                }
            }
            return ResponseEntity.status(404).body(Map.of("error", "worldview_not_found"));
        } catch (Exception e) {
            log.error("Update worldview failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "update_worldview_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteWorldview(String id) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getWorldview() == null) return ResponseEntity.status(404).body(Map.of("error", "worldview_not_found"));
            boolean removed = ps.getWorldview().removeIf(w -> id.equals(w.getId()));
            if (!removed) return ResponseEntity.status(404).body(Map.of("error", "worldview_not_found"));
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete worldview failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_worldview_failed", "detail", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Organization CRUD
    // ---------------------------------------------------------------

    public ResponseEntity<?> createOrganization(Organization org) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getOrganizations() == null) ps.setOrganizations(new ArrayList<>());
            org.setId(nextOrganizationID(ps));
            ps.getOrganizations().add(org);
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(org);
        } catch (Exception e) {
            log.error("Create organization failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "create_organization_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> updateOrganization(String id, Organization patch) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getOrganizations() == null) return ResponseEntity.status(404).body(Map.of("error", "organization_not_found"));
            for (Organization o : ps.getOrganizations()) {
                if (id.equals(o.getId())) {
                    if (patch.getName() != null && !patch.getName().isEmpty()) o.setName(patch.getName());
                    if (patch.getType() != null) o.setType(patch.getType());
                    if (patch.getDescription() != null) o.setDescription(patch.getDescription());
                    if (patch.getMembers() != null) o.setMembers(patch.getMembers());
                    projectService.setSettings(ps);
                    stateService.saveSettings(projectService.getSettingsPath(), ps);
                    return ResponseEntity.ok(o);
                }
            }
            return ResponseEntity.status(404).body(Map.of("error", "organization_not_found"));
        } catch (Exception e) {
            log.error("Update organization failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "update_organization_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteOrganization(String id) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getOrganizations() == null) return ResponseEntity.status(404).body(Map.of("error", "organization_not_found"));
            boolean removed = ps.getOrganizations().removeIf(o -> id.equals(o.getId()));
            if (!removed) return ResponseEntity.status(404).body(Map.of("error", "organization_not_found"));
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete organization failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_organization_failed", "detail", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Relation CRUD
    // ---------------------------------------------------------------

    public ResponseEntity<?> createRelation(Relation relation) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getRelations() == null) ps.setRelations(new ArrayList<>());
            relation.setId(nextRelationID(ps));
            ps.getRelations().add(relation);
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(relation);
        } catch (Exception e) {
            log.error("Create relation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "create_relation_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> updateRelation(String id, Relation patch) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getRelations() == null) return ResponseEntity.status(404).body(Map.of("error", "relation_not_found"));
            for (Relation r : ps.getRelations()) {
                if (id.equals(r.getId())) {
                    if (patch.getSourceId() != null) r.setSourceId(patch.getSourceId());
                    if (patch.getSourceType() != null) r.setSourceType(patch.getSourceType());
                    if (patch.getTargetId() != null) r.setTargetId(patch.getTargetId());
                    if (patch.getTargetType() != null) r.setTargetType(patch.getTargetType());
                    if (patch.getLabel() != null) r.setLabel(patch.getLabel());
                    projectService.setSettings(ps);
                    stateService.saveSettings(projectService.getSettingsPath(), ps);
                    return ResponseEntity.ok(r);
                }
            }
            return ResponseEntity.status(404).body(Map.of("error", "relation_not_found"));
        } catch (Exception e) {
            log.error("Update relation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "update_relation_failed", "detail", e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteRelation(String id) {
        try {
            ProjectSettings ps = projectService.getSettings();
            if (ps.getRelations() == null) return ResponseEntity.status(404).body(Map.of("error", "relation_not_found"));
            boolean removed = ps.getRelations().removeIf(r -> id.equals(r.getId()));
            if (!removed) return ResponseEntity.status(404).body(Map.of("error", "relation_not_found"));
            projectService.setSettings(ps);
            stateService.saveSettings(projectService.getSettingsPath(), ps);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete relation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_relation_failed", "detail", e.getMessage()));
        }
    }
}
