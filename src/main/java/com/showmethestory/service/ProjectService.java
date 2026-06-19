package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Central project management service.
 * Holds all in-memory project state and manages project CRUD/switching.
 * Ported from Go handlers.go (Handlers struct + switchProject + ensureProject)
 * and web.go (GetProjects, PostProject, DeleteProject).
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    @Value("${app.prog-dir:${user.home}/.showmethestory}")
    private String progDir;

    @Value("${app.version:1.0.0}")
    private String version;

    private final StateService stateService;
    private final SkillService skillService;
    private final ObjectMapper mapper;
    private final ReadWriteLock projectLock = new ReentrantReadWriteLock();

    // ---- In-memory project state (mirrors Go Handlers fields) ----
    private volatile String projectName = "";
    private volatile Config cfg;
    private volatile String cfgPath;
    private volatile Progress state;
    private volatile String progressPath;
    private volatile ProjectSettings settings;
    private volatile String settingsPath;
    private volatile List<Skill> skills = new ArrayList<>();
    private volatile String sessionsDir;
    private volatile PostProcessState postprocess;
    private volatile String postprocessPath;
    private volatile APIConfig apiCfg;
    private volatile String apiCfgPath;

    public ProjectService(StateService stateService, SkillService skillService) {
        this.stateService = stateService;
        this.skillService = skillService;
        this.mapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        // Initialize defaults
        this.state = new Progress();
        this.state.setPhase("outline");
        this.settings = new ProjectSettings();
        this.postprocess = new PostProcessState();
        this.postprocess.setExecuteOptions(new PostProcessExecuteOptions());

        // Ensure prog dir exists
        try {
            Files.createDirectories(Paths.get(progDir));
        } catch (Exception e) {
            log.warn("Failed to create progDir: {}", progDir, e);
        }

        // Load global API config
        apiCfgPath = Paths.get(progDir, "api_config.json").toString();
        try {
            apiCfg = stateService.loadAPIConfig(apiCfgPath);
        } catch (Exception e) {
            log.warn("Failed to load API config, using defaults", e);
            apiCfg = new APIConfig();
            apiCfg.setHttpTimeoutSeconds(300);
        }
    }

    // ---------------------------------------------------------------
    // Project directory helpers
    // ---------------------------------------------------------------

    public String getProgDir() {
        return progDir;
    }

    public String getVersion() {
        return version;
    }

    private String storysDir() {
        return Paths.get(progDir, "storys").toString();
    }

    public String getProjectDir() {
        projectLock.readLock().lock();
        try {
            if (projectName == null || projectName.isEmpty()) {
                return progDir;
            }
            return Paths.get(progDir, "storys", projectName).toString();
        } finally {
            projectLock.readLock().unlock();
        }
    }

    public String getProgressPath() {
        return progressPath;
    }

    public String getCfgPath() {
        return cfgPath;
    }

    public String getSettingsPath() {
        return settingsPath;
    }

    public String getSessionsDir() {
        return sessionsDir;
    }

    public String getPostprocessPath() {
        return postprocessPath;
    }

    // ---------------------------------------------------------------
    // In-memory state accessors
    // ---------------------------------------------------------------

    public Progress getProgress() {
        return state;
    }

    public Config getConfig() {
        return cfg;
    }

    public APIConfig getAPIConfig() {
        return apiCfg;
    }

    public ProjectSettings getSettings() {
        return settings;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public PostProcessState getPostProcess() {
        return postprocess;
    }

    public void setPostProcess(PostProcessState pp) {
        this.postprocess = pp;
    }

    /**
     * Returns true if a project is currently selected.
     */
    public boolean ensureProject() {
        return projectName != null && !projectName.isEmpty();
    }

    // ---------------------------------------------------------------
    // State setters (used by other services to persist changes)
    // ---------------------------------------------------------------

    public void setConfig(Config cfg) {
        this.cfg = cfg;
    }

    public void setSettings(ProjectSettings settings) {
        this.settings = settings;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    // ---------------------------------------------------------------
    // Project CRUD
    // ---------------------------------------------------------------

    /**
     * Go: GetProjects - list all projects sorted by mod time descending.
     */
    public List<Map<String, String>> listProjects() {
        String dir = storysDir();
        File storysDirFile = new File(dir);
        if (!storysDirFile.exists() || !storysDirFile.isDirectory()) {
            return new ArrayList<>();
        }

        List<Map<String, String>> projects = new ArrayList<>();
        File[] entries = storysDirFile.listFiles();
        if (entries == null) return projects;

        for (File entry : entries) {
            if (!entry.isDirectory()) continue;
            String name = entry.getName();
            String projectDir = Paths.get(dir, name).toString();

            String phase = "";
            String title = "";
            String progressFile = Paths.get(projectDir, "progress.json").toString();
            try {
                Progress p = stateService.loadProgress(progressFile);
                if (p != null) {
                    phase = p.getPhase() != null ? p.getPhase() : "";
                    title = p.getTitle() != null ? p.getTitle() : "";
                }
            } catch (Exception ignored) {}

            // Read project language from config.json
            String lang = LocaleHelper.LANG_ZH;
            String configFile = Paths.get(projectDir, "config.json").toString();
            try {
                byte[] data = Files.readAllBytes(Paths.get(configFile));
                Map<?, ?> probe = mapper.readValue(data, Map.class);
                Object langObj = probe.get("language");
                if (langObj instanceof String s && !s.isEmpty()) {
                    lang = LocaleHelper.normalizeLanguage(s);
                }
            } catch (Exception ignored) {}

            Map<String, String> info = new LinkedHashMap<>();
            info.put("name", name);
            info.put("phase", phase);
            info.put("title", title);
            info.put("language", lang);
            info.put("updated_at", Instant.ofEpochMilli(entry.lastModified()).toString());
            projects.add(info);
        }

        // Sort by updated_at descending
        projects.sort((a, b) -> b.get("updated_at").compareTo(a.get("updated_at")));
        return projects;
    }

    /**
     * Go: PostProject - create a new project directory with default config.
     */
    public ResponseEntity<?> createProject(String name, String language) {
        // Validate name chars
        for (char c : name.toCharArray()) {
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                return ResponseEntity.badRequest().body(Map.of("error", "project_name_invalid_chars"));
            }
        }

        String projectDir = Paths.get(storysDir(), name).toString();
        if (new File(projectDir).exists()) {
            return ResponseEntity.status(409).body(Map.of("error", "project_exists"));
        }

        try {
            Files.createDirectories(Paths.get(projectDir));
            Files.createDirectories(Paths.get(projectDir, "sessions"));

            String lang = LocaleHelper.normalizeLanguage(language);
            Config defaultCfg = defaultConfigForLang(lang);
            String configPath = Paths.get(projectDir, "config.json").toString();
            stateService.saveConfig(configPath, defaultCfg);

            return ResponseEntity.ok(Map.of("name", name, "language", lang));
        } catch (Exception e) {
            log.error("Create project failed: {}", name, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "create_project_dir_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: GetProjectCurrent - returns current selected project name and language.
     */
    public Map<String, String> getCurrentProject() {
        projectLock.readLock().lock();
        try {
            Map<String, String> resp = new LinkedHashMap<>();
            resp.put("name", projectName != null ? projectName : "");
            if (projectName != null && !projectName.isEmpty() && cfg != null) {
                resp.put("language", LocaleHelper.normalizeLanguage(cfg.getLanguage()));
            }
            return resp;
        } finally {
            projectLock.readLock().unlock();
        }
    }

    /**
     * Go: PostProjectSelect / switchProject - switch to the specified project.
     * Loads all project-specific data from disk.
     */
    public ResponseEntity<?> selectProject(String name) {
        String projectDir = Paths.get(storysDir(), name).toString();
        File dirFile = new File(projectDir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return ResponseEntity.badRequest().body(Map.of("error", "project_not_found", "detail", "项目目录不存在: " + name));
        }

        projectLock.writeLock().lock();
        try {
            String configPath = Paths.get(projectDir, "config.json").toString();
            String progressFile = Paths.get(projectDir, "progress.json").toString();
            String settingsFile = Paths.get(projectDir, "settings.json").toString();
            String sessDir = Paths.get(projectDir, "sessions").toString();
            Files.createDirectories(Paths.get(sessDir));

            Config loadedCfg = stateService.loadConfig(configPath);
            Progress loadedState = stateService.loadProgress(progressFile);
            if (loadedState == null) {
                loadedState = new Progress();
                loadedState.setPhase("outline");
            }
            ProjectSettings loadedSettings = stateService.loadSettings(settingsFile);
            List<Skill> loadedSkills = skillService.loadAllSkills(loadedCfg, projectDir);

            String ppPath = Paths.get(projectDir, "postprocess.json").toString();
            PostProcessState loadedPP = loadPostProcess(ppPath);

            this.projectName = name;
            this.cfg = loadedCfg;
            this.cfgPath = configPath;
            this.state = loadedState;
            this.progressPath = progressFile;
            this.settings = loadedSettings;
            this.settingsPath = settingsFile;
            this.skills = loadedSkills;
            this.sessionsDir = sessDir;
            this.postprocessPath = ppPath;
            this.postprocess = loadedPP;

            log.info("Switched to project: {} ({})", name, projectDir);
            return ResponseEntity.ok(Map.of("name", name));
        } catch (Exception e) {
            log.error("Switch project failed: {}", name, e);
            return ResponseEntity.badRequest().body(Map.of("error", "switch_project_failed", "detail", e.getMessage()));
        } finally {
            projectLock.writeLock().unlock();
        }
    }

    /**
     * Go: DeleteProject - delete a project directory.
     */
    public ResponseEntity<?> deleteProject(String name) {
        projectLock.readLock().lock();
        String currentProject = projectName;
        projectLock.readLock().unlock();

        if (name.equals(currentProject)) {
            return ResponseEntity.status(409).body(Map.of("error", "cannot_delete_current_project"));
        }

        String projectDir = Paths.get(storysDir(), name).toString();
        File dirFile = new File(projectDir);
        if (!dirFile.exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "project_not_found"));
        }

        try {
            deleteDirectoryRecursive(dirFile.toPath());
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete project failed: {}", name, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_project_failed", "detail", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // PostProcess load/save
    // ---------------------------------------------------------------

    private PostProcessState loadPostProcess(String path) {
        try {
            byte[] data = stateService.loadProgress(path) != null ? null : null;
            // Use FileSystemService directly
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                byte[] bytes = Files.readAllBytes(p);
                return mapper.readValue(bytes, PostProcessState.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load postprocess state, using defaults", e);
        }
        PostProcessState pp = new PostProcessState();
        pp.setExecuteOptions(new PostProcessExecuteOptions());
        return pp;
    }

    public void savePostProcess(String path, PostProcessState pp) throws Exception {
        byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pp);
        Files.write(Paths.get(path + ".tmp"), data);
        Files.move(Paths.get(path + ".tmp"), Paths.get(path),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Config defaultConfigForLang(String lang) {
        lang = LocaleHelper.normalizeLanguage(lang);
        Config cfg = new Config();
        cfg.setLanguage(lang);
        cfg.getStory().setChapterCount(30);
        cfg.getStory().setTargetWordsPerChapter(2500);
        cfg.setSkillConfig(new SkillConfig());
        // Prompt defaults are applied by StateService.loadConfig
        return cfg;
    }

    private void deleteDirectoryRecursive(Path path) throws Exception {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception ignored) {}
                    });
        }
    }

    /**
     * Get chapter by number from current state.
     */
    public ChapterState getChapterByNum(int num) {
        if (state != null && state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if (ch.getNum() == num) return ch;
            }
        }
        return new ChapterState();
    }

    /**
     * Chapter markdown path helper (used by other services).
     */
    public String chapterMarkdownPath(String projectDir, int chapterNum) {
        return Paths.get(projectDir, String.format("chapter_%03d.md", chapterNum)).toString();
    }
}
