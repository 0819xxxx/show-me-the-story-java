package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.ChapterState;
import com.showmethestory.model.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progress, status, and version endpoints.
 * Ported from Go handlers.go: GetProgress, DeleteProgress, GetStatus, GetVersion.
 */
@Service
public class ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);

    private final ProjectService projectService;
    private final StateService stateService;
    private final LogBroadcaster logger;
    private final TaskManager taskManager;

    public ProgressService(ProjectService projectService,
                           StateService stateService,
                           LogBroadcaster logger,
                           TaskManager taskManager) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.logger = logger;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetProgress - returns the full Progress state.
     */
    public Progress getProgress() {
        return projectService.getProgress();
    }

    /**
     * Go: DeleteProgress - resets progress by deleting the progress file.
     * Resets to initial state {phase: "outline"}.
     */
    public ResponseEntity<?> deleteProgress() {
        try {
            String progressPath = projectService.getProgressPath();
            if (progressPath != null) {
                stateService.saveProgress(progressPath, new Progress());
            }
            Progress fresh = new Progress();
            fresh.setPhase("outline");
            // Reset in-memory state
            Progress state = projectService.getProgress();
            state.setPhase("outline");
            state.setTitle("");
            state.setCorePrompt("");
            state.setStorySynopsis("");
            state.setChapters(null);
            state.setStoryConfigSnapshot(null);
            state.setCurrentChapterIndex(0);
            state.setForeshadows(null);
            state.setPendingWritingConflict(null);
            state.setLastForeshadowOutlineReport(null);

            try {
                stateService.saveProgress(progressPath, state);
            } catch (Exception e) {
                log.warn("Failed to persist reset progress", e);
            }

            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Delete progress failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_progress_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: GetStatus - returns a lightweight status summary.
     */
    public Map<String, Object> getStatus() {
        Progress state = projectService.getProgress();
        var cfg = projectService.getConfig();
        String lang = LocaleHelper.LANG_ZH;
        if (cfg != null) {
            lang = LocaleHelper.normalizeLanguage(cfg.getLanguage());
        }

        int totalChapters = 0;
        if (state.getChapters() != null) {
            totalChapters = state.getChapters().size();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("phase", state.getPhase());
        resp.put("title", state.getTitle());
        resp.put("total_chapters", totalChapters);
        resp.put("is_task_running", taskManager.isTaskRunning());
        resp.put("auto_confirm", taskManager.isAutoConfirm());
        resp.put("project_language", lang);

        return resp;
    }

    /**
     * Go: GetVersion - returns {version: "..."}.
     */
    public String getVersion() {
        return projectService.getVersion();
    }
}
