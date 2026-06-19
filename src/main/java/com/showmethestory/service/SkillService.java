package com.showmethestory.service;

import com.showmethestory.model.Config;
import com.showmethestory.model.Skill;
import com.showmethestory.model.SkillConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill loading and management.
 * Maps to Go: skills.go -- LoadBuiltinSkills, LoadProjectSkills, parseSkillFile,
 * MergeSkills, LoadAllSkills, FilterSkillsByLang, GetEnabledSkills,
 * GetEnabledSkillsByCategory, FormatSkillsContent.
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final ProjectService projectService;
    private final StateService stateService;

    public SkillService(@Lazy ProjectService projectService, StateService stateService) {
        this.projectService = projectService;
        this.stateService = stateService;
    }

    // ---------------------------------------------------------------
    // Load built-in skills from classpath resources
    // ---------------------------------------------------------------

    /**
     * Load built-in skills from classpath: skills/*.md.
     * Go equivalent: LoadBuiltinSkills() (reads from embed.FS).
     */
    public List<Skill> loadBuiltinSkills() {
        List<Skill> skills = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.md");
            for (Resource res : resources) {
                try (InputStream is = res.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Skill skill = parseSkillFile(content, "builtin");
                    if (skill != null) {
                        skills.add(skill);
                    }
                } catch (Exception e) {
                    log.warn("读取内置技能文件 {} 失败: {}", res.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("读取内置技能目录失败: {}", e.getMessage());
        }
        return skills;
    }

    // ---------------------------------------------------------------
    // Load project-specific skills from disk
    // ---------------------------------------------------------------

    /**
     * Load project-specific skills from {projectDir}/skills/*.md.
     */
    public List<Skill> loadProjectSkills(String projectDir) {
        List<Skill> skills = new ArrayList<>();
        Path skillsDir = Path.of(projectDir, "skills");
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir, "*.md")) {
            for (Path entry : stream) {
                try {
                    String content = Files.readString(entry, StandardCharsets.UTF_8);
                    Skill skill = parseSkillFile(content, "project");
                    if (skill != null) {
                        skills.add(skill);
                    }
                } catch (Exception e) {
                    log.warn("读取项目技能文件 {} 失败: {}", entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("读取项目技能目录失败: {}", e.getMessage());
        }
        return skills;
    }

    // ---------------------------------------------------------------
    // Parse a skill file (YAML frontmatter + markdown body)
    // ---------------------------------------------------------------

    /**
     * Parse a skill file with YAML frontmatter delimited by ---.
     */
    public Skill parseSkillFile(String content, String source) {
        String[] parts = content.split("---", 3);
        if (parts.length < 3) {
            log.warn("invalid skill file format: missing frontmatter");
            return null;
        }

        String frontmatter = parts[1].trim();
        String body = parts[2].trim();

        Skill skill = new Skill();
        skill.setSource(source);

        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();

            switch (key) {
                case "id" -> skill.setId(value);
                case "name" -> skill.setName(value);
                case "description" -> skill.setDescription(value);
                case "category" -> skill.setCategory(value);
                case "lang" -> skill.setLang(normalizeLanguage(value));
                case "source" -> {
                    if (source == null || source.isEmpty()) skill.setSource(value);
                }
            }
        }

        skill.setContent(body);

        if (skill.getId() == null || skill.getId().isEmpty()) {
            log.warn("skill missing id");
            return null;
        }

        return skill;
    }

    // ---------------------------------------------------------------
    // Merge, filter, and enable/disable
    // ---------------------------------------------------------------

    public List<Skill> mergeSkills(List<Skill> builtin, List<Skill> project) {
        List<Skill> result = new ArrayList<>(builtin.size() + project.size());
        result.addAll(builtin);
        result.addAll(project);
        return result;
    }

    /**
     * Load all skills for the project: builtin + project, filtered by language.
     */
    public List<Skill> loadAllSkills(Config cfg, String projectDir) {
        List<Skill> builtin = loadBuiltinSkills();
        List<Skill> project = loadProjectSkills(projectDir);
        List<Skill> merged = mergeSkills(builtin, project);
        if (cfg == null) return merged;
        return filterSkillsByLang(merged, cfg.getLanguage());
    }

    /**
     * Filter skills by project language.
     * Skills with empty lang are language-agnostic and always included.
     */
    public List<Skill> filterSkillsByLang(List<Skill> skills, String projectLang) {
        String normalizedLang = normalizeLanguage(projectLang);
        List<Skill> out = new ArrayList<>();
        for (Skill s : skills) {
            if (s.getLang() == null || s.getLang().isEmpty() || s.getLang().equals(normalizedLang)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Return only enabled skills.
     */
    public List<Skill> getEnabledSkills(List<Skill> skills, SkillConfig sc) {
        if (sc == null || sc.getEnabledSkills() == null) return new ArrayList<>();
        List<Skill> enabled = new ArrayList<>();
        for (Skill s : skills) {
            Boolean isEnabled = sc.getEnabledSkills().get(s.getId());
            if (isEnabled != null && isEnabled) {
                enabled.add(s);
            }
        }
        return enabled;
    }

    /**
     * Return only enabled skills in a specific category.
     */
    public List<Skill> getEnabledSkillsByCategory(List<Skill> skills, SkillConfig sc, String category) {
        if (sc == null || sc.getEnabledSkills() == null) return new ArrayList<>();
        List<Skill> enabled = new ArrayList<>();
        for (Skill s : skills) {
            Boolean isEnabled = sc.getEnabledSkills().get(s.getId());
            if (isEnabled != null && isEnabled && category.equals(s.getCategory())) {
                enabled.add(s);
            }
        }
        return enabled;
    }

    /**
     * Format enabled skills as prompt content.
     */
    public String formatSkillsContent(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) return "";

        boolean en = false;
        for (Skill s : skills) {
            if ("en".equals(s.getLang())) {
                en = true;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (en) {
            sb.append("Strictly follow the skill rules below while writing:\n\n");
        } else {
            sb.append("以下技能规则在创作时必须严格遵守：\n\n");
        }
        for (Skill s : skills) {
            sb.append("## ").append(s.getName()).append("\n\n")
                    .append(s.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isEmpty()) return "zh";
        lang = lang.trim().toLowerCase();
        if (lang.startsWith("en")) return "en";
        return "zh";
    }

    // ---------------------------------------------------------------
    // Skills with status (for controller)
    // ---------------------------------------------------------------

    /**
     * Return all skills with their enabled status from the current project's SkillConfig.
     */
    public List<Map<String, Object>> getSkillsWithStatus() {
        List<Skill> allSkills = projectService.getSkills();
        Config cfg = projectService.getConfig();
        SkillConfig sc = cfg != null ? cfg.getSkillConfig() : null;
        Map<String, Boolean> enabledMap = (sc != null && sc.getEnabledSkills() != null)
                ? sc.getEnabledSkills() : Map.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Skill s : allSkills) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("skill", s);
            Boolean enabled = enabledMap.get(s.getId());
            entry.put("enabled", enabled != null && enabled);
            result.add(entry);
        }
        return result;
    }

    /**
     * Toggle a skill's enabled/disabled state and persist the config.
     */
    public ResponseEntity<?> toggleSkill(String id, boolean enabled) {
        try {
            Config cfg = projectService.getConfig();
            if (cfg == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_config"));
            }
            SkillConfig sc = cfg.getSkillConfig();
            if (sc == null) {
                sc = new SkillConfig();
                cfg.setSkillConfig(sc);
            }
            sc.getEnabledSkills().put(id, enabled);
            stateService.saveConfig(projectService.getCfgPath(), cfg);
            return ResponseEntity.ok(Map.of("id", id, "enabled", enabled));
        } catch (Exception e) {
            log.error("Toggle skill failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "toggle_skill_failed", "detail", e.getMessage()));
        }
    }
}
