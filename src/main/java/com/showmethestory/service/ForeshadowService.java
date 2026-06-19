package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Foreshadow lifecycle management: CRUD, AI suggestion, confirmation, roadmap, sync.
 * Maps to Go: foreshadow.go.
 */
@Service
public class ForeshadowService {

    private static final Logger log = LoggerFactory.getLogger(ForeshadowService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logBroadcaster;

    public ForeshadowService(ProjectService projectService, StateService stateService,
                              OpenAIClient openAIClient, LogBroadcaster logBroadcaster) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logBroadcaster = logBroadcaster;
    }

    // ---------------------------------------------------------------
    // Controller-facing methods
    // ---------------------------------------------------------------

    public List<Foreshadow> getForeshadows() {
        Progress state = projectService.getProgress();
        return state.getForeshadows();
    }

    public Map<String, Object> buildRoadmap() {
        Progress state = projectService.getProgress();
        String markdown = buildForeshadowRoadmapMarkdown(state);
        String path = projectService.getProjectDir() + "/Foreshadows.md";
        return Map.of("markdown", markdown, "path", path);
    }

    public void suggestForeshadows(OpenAIClient.CancellationToken ct) {
        try {
            Progress state = projectService.getProgress();
            Config cfg = projectService.getConfig();
            APIConfig apiCfg = projectService.getAPIConfig();
            String lang = cfg.getLanguage();

            StringBuilder outline = new StringBuilder();
            for (ChapterState ch : state.getChapters()) {
                outline.append(formatChapterLine(ch, lang));
            }

            String userPrompt = renderPrompt(cfg.getPrompts().getForeshadowPlanning(), Map.of(
                    "Title", state.getTitle() != null ? state.getTitle() : "",
                    "CorePrompt", state.getCorePrompt() != null ? state.getCorePrompt() : "",
                    "StorySynopsis", state.getStorySynopsis() != null ? state.getStorySynopsis() : "",
                    "Outline", outline.toString()
            ));

            String systemPrompt = LocaleHelper.systemPromptFor(lang, "narrative_architect_json");

            String rawResp = openAIClient.callAPIWithRetry(ct, apiCfg, systemPrompt, userPrompt, logBroadcaster, null);
            if (rawResp == null || rawResp.isEmpty()) {
                logBroadcaster.error("API 调用失败或被取消");
                return;
            }
            rawResp = cleanJSONResponse(rawResp);

            ForeshadowPlanResponse resp = MAPPER.readValue(rawResp, ForeshadowPlanResponse.class);
            logBroadcaster.infoKey("log.foreshadow_plan_parsed", resp.getForeshadows().size());
            logBroadcaster.foreshadowSuggestions(resp.getForeshadows());

        } catch (Exception e) {
            logBroadcaster.error("生成伏笔建议失败: " + e.getMessage());
        }
    }

    public ResponseEntity<?> confirmForeshadows(List<Foreshadow> foreshadows) {
        try {
            Progress state = projectService.getProgress();
            if (state.getForeshadows() == null) {
                state.setForeshadows(new ArrayList<>());
            }

            int nextId = nextForeshadowID(state.getForeshadows());
            for (Foreshadow fs : foreshadows) {
                fs.setId(nextId++);
                fs.setStatus(Foreshadow.STATUS_PLANTED);
                if (fs.getEvents() == null) {
                    fs.setEvents(new ArrayList<>());
                }
                state.getForeshadows().add(fs);
            }

            stateService.saveProgress(projectService.getProgressPath(), state);
            saveForeshadowRoadmap(projectService.getProjectDir(), state);
            logBroadcaster.success("已确认 " + foreshadows.size() + " 条伏笔");

            return ResponseEntity.ok(Map.of("status", "ok", "count", foreshadows.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public ResponseEntity<?> createForeshadow(String name, String description, int plantChapter, int targetChapter) {
        try {
            Progress state = projectService.getProgress();
            if (state.getForeshadows() == null) {
                state.setForeshadows(new ArrayList<>());
            }

            Foreshadow fs = new Foreshadow();
            fs.setId(nextForeshadowID(state.getForeshadows()));
            fs.setName(name);
            fs.setDescription(description);
            fs.setPlantChapter(plantChapter);
            fs.setTargetChapter(targetChapter);
            fs.setStatus(Foreshadow.STATUS_PLANTED);
            fs.setEvents(new ArrayList<>());

            state.getForeshadows().add(fs);
            stateService.saveProgress(projectService.getProgressPath(), state);
            saveForeshadowRoadmap(projectService.getProjectDir(), state);

            return ResponseEntity.ok(fs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public ResponseEntity<?> updateForeshadow(int id, Map<String, Object> body) {
        try {
            Progress state = projectService.getProgress();
            Foreshadow fs = findForeshadow(state, id);
            if (fs == null) {
                return ResponseEntity.status(404).body(Map.of("error", "foreshadow_not_found"));
            }

            if (body.containsKey("name") && body.get("name") != null) {
                fs.setName((String) body.get("name"));
            }
            if (body.containsKey("description") && body.get("description") != null) {
                fs.setDescription((String) body.get("description"));
            }
            if (body.containsKey("plant_chapter") && body.get("plant_chapter") instanceof Number n) {
                fs.setPlantChapter(n.intValue());
            }
            if (body.containsKey("target_chapter") && body.get("target_chapter") instanceof Number n) {
                fs.setTargetChapter(n.intValue());
            }
            if (body.containsKey("status") && body.get("status") != null) {
                fs.setStatus((String) body.get("status"));
            }
            if (body.containsKey("resolution") && body.get("resolution") != null) {
                fs.setResolution((String) body.get("resolution"));
            }

            stateService.saveProgress(projectService.getProgressPath(), state);
            saveForeshadowRoadmap(projectService.getProjectDir(), state);

            return ResponseEntity.ok(fs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public ResponseEntity<?> deleteForeshadow(int id) {
        try {
            Progress state = projectService.getProgress();
            if (state.getForeshadows() != null) {
                state.getForeshadows().removeIf(fs -> fs.getId() == id);
            }
            stateService.saveProgress(projectService.getProgressPath(), state);
            saveForeshadowRoadmap(projectService.getProjectDir(), state);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Internal foreshadow sync (called after chapter generation)
    // ---------------------------------------------------------------

    public void syncForeshadowsAfterChapter(OpenAIClient.CancellationToken ct, Config cfg,
                                             APIConfig apiCfg, Progress state,
                                             int chapterIdx, String progressPath) {
        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) return;

        try {
            updateForeshadowsFromAI(ct, cfg, apiCfg, state, chapterIdx);

            int active = 0, resolved = 0;
            for (Foreshadow fs : state.getForeshadows()) {
                if (Foreshadow.STATUS_PLANTED.equals(fs.getStatus()) || Foreshadow.STATUS_PROGRESSING.equals(fs.getStatus())) {
                    active++;
                } else if (Foreshadow.STATUS_RESOLVED.equals(fs.getStatus())) {
                    resolved++;
                }
            }
            logBroadcaster.infoKey("log.foreshadow_sync_summary", active, resolved);

            saveForeshadowRoadmap(projectService.getProjectDir(), state);

            String warn = buildForeshadowWarnings(state);
            if (!warn.isEmpty()) {
                logBroadcaster.warn(warn);
            }
        } catch (Exception e) {
            logBroadcaster.warnKey("log.foreshadow_sync_failed", e.getMessage());
        }
    }

    private void updateForeshadowsFromAI(OpenAIClient.CancellationToken ct, Config cfg,
                                          APIConfig apiCfg, Progress state, int chapterIdx) throws Exception {
        ChapterState ch = state.getChapters().get(chapterIdx);
        String lang = cfg.getLanguage();

        String foreshadowsText = formatForeshadowsForPrompt(state.getForeshadows());
        if ("无".equals(foreshadowsText) || "(none)".equals(foreshadowsText)) return;

        String historySummary = buildHistorySummary(state, chapterIdx, lang);

        String userPrompt = renderPrompt(cfg.getPrompts().getForeshadowUpdate(), Map.of(
                "Title", state.getTitle() != null ? state.getTitle() : "",
                "ChapterNum", String.valueOf(ch.getNum()),
                "ChapterTitle", ch.getTitle() != null ? ch.getTitle() : "",
                "ChapterContent", ch.getContent() != null ? ch.getContent() : "",
                "HistorySummary", historySummary,
                "Foreshadows", foreshadowsText
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "foreshadow_tracker_json");
        String rawResp = openAIClient.callAPIWithRetry(ct, apiCfg, systemPrompt, userPrompt, logBroadcaster, null);
        if (rawResp == null || rawResp.isEmpty()) return;

        rawResp = cleanJSONResponse(rawResp);
        ForeshadowUpdateResponse resp = MAPPER.readValue(rawResp, ForeshadowUpdateResponse.class);
        applyForeshadowUpdates(state, resp.getUpdates(), ch.getNum());
        logBroadcaster.infoKey("log.foreshadow_status_updated", resp.getUpdates().size());
    }

    // ---------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------

    public String formatForeshadowsForPrompt(List<Foreshadow> foreshadows) {
        if (foreshadows == null || foreshadows.isEmpty()) return "无";

        StringBuilder sb = new StringBuilder();
        for (Foreshadow fs : foreshadows) {
            sb.append("#").append(fs.getId()).append(" [").append(fs.getStatus()).append("] ")
                    .append(fs.getName()).append("\n");
            sb.append("   描述: ").append(fs.getDescription()).append("\n");
            sb.append("   埋设于: 第").append(fs.getPlantChapter()).append("章");
            if (fs.getTargetChapter() > 0) {
                sb.append("，预计回收: 第").append(fs.getTargetChapter()).append("章");
            }
            sb.append("\n");

            if (fs.getEvents() != null && !fs.getEvents().isEmpty()) {
                sb.append("   已有进展:\n");
                for (ForeshadowEvent ev : fs.getEvents()) {
                    sb.append("   - 第").append(ev.getChapter()).append("章: ").append(ev.getNote()).append("\n");
                }
            }

            if (fs.getResolution() != null && !fs.getResolution().isEmpty()) {
                sb.append("   回收方式: ").append(fs.getResolution()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String formatActiveForeshadowsForChapter(List<Foreshadow> foreshadows, int chapterNum) {
        if (foreshadows == null) return "";

        List<Foreshadow> active = new ArrayList<>();
        List<Foreshadow> overdue = new ArrayList<>();

        for (Foreshadow fs : foreshadows) {
            if (Foreshadow.STATUS_PLANTED.equals(fs.getStatus()) || Foreshadow.STATUS_PROGRESSING.equals(fs.getStatus())) {
                active.add(fs);
                if (fs.getTargetChapter() > 0 && chapterNum >= fs.getTargetChapter()) {
                    overdue.add(fs);
                }
            }
        }

        if (active.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【活跃伏笔（写作时必须注意推进或回收）】\n");

        for (Foreshadow fs : active) {
            sb.append("#").append(fs.getId()).append(" \"").append(fs.getName())
                    .append("\" [第").append(fs.getPlantChapter()).append("章埋设");
            if (fs.getTargetChapter() > 0) {
                sb.append("，预计第").append(fs.getTargetChapter()).append("章回收");
            }
            sb.append("]\n");
            sb.append("   描述: ").append(fs.getDescription()).append("\n");

            if (fs.getEvents() != null && !fs.getEvents().isEmpty()) {
                sb.append("   已有进展:\n");
                for (ForeshadowEvent ev : fs.getEvents()) {
                    sb.append("   - 第").append(ev.getChapter()).append("章: ").append(ev.getNote()).append("\n");
                }
            }

            boolean isOverdue = overdue.stream().anyMatch(o -> o.getId() == fs.getId());
            if (isOverdue) {
                sb.append("   ⚠️ 该伏笔已超过预计回收章节（第").append(fs.getTargetChapter())
                        .append("章），本章应优先考虑回收\n");
            } else if (fs.getTargetChapter() > 0 && chapterNum >= fs.getTargetChapter() - 2) {
                sb.append("   → 接近预计回收节点（第").append(fs.getTargetChapter())
                        .append("章），可开始收束\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String buildForeshadowWarnings(Progress state) {
        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) return "";

        int currentChapter = state.getCurrentChapterIndex() + 1;
        List<String> warnings = new ArrayList<>();

        for (Foreshadow fs : state.getForeshadows()) {
            if (Foreshadow.STATUS_RESOLVED.equals(fs.getStatus()) || Foreshadow.STATUS_ABANDONED.equals(fs.getStatus())) continue;
            if (fs.getTargetChapter() > 0 && currentChapter > fs.getTargetChapter() + 3) {
                warnings.add("伏笔 #" + fs.getId() + " \"" + fs.getName()
                        + "\" 已超过预计回收章节 " + fs.getTargetChapter() + " 章以上");
            }
        }

        if (warnings.isEmpty()) return "";
        return "⚠️ 伏笔超期告警: " + String.join("；", warnings);
    }

    // ---------------------------------------------------------------
    // Roadmap markdown
    // ---------------------------------------------------------------

    public String buildForeshadowRoadmapMarkdown(Progress state) {
        String title = state.getTitle();
        if (title == null || title.isEmpty()) title = "未命名小说";

        StringBuilder sb = new StringBuilder();
        sb.append("# 伏笔路线图 — 《").append(title).append("》\n\n");
        sb.append("> 更新时间：").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) {
            sb.append("当前尚无伏笔记录。\n");
            return sb.toString();
        }

        int active = 0, resolved = 0, abandoned = 0;
        for (Foreshadow fs : state.getForeshadows()) {
            String status = fs.getStatus();
            if (Foreshadow.STATUS_PLANTED.equals(status) || Foreshadow.STATUS_PROGRESSING.equals(status)) {
                active++;
            } else if (Foreshadow.STATUS_RESOLVED.equals(status)) {
                resolved++;
            } else if (Foreshadow.STATUS_ABANDONED.equals(status)) {
                abandoned++;
            }
        }

        sb.append("## 概览\n\n");
        sb.append("- 总计 **").append(state.getForeshadows().size()).append("** 条 | 活跃 **")
                .append(active).append("** | 已回收 **").append(resolved)
                .append("** | 已放弃 **").append(abandoned).append("**\n\n");

        String warn = buildForeshadowWarnings(state);
        if (!warn.isEmpty()) {
            sb.append("## 超期告警\n\n");
            sb.append(warn.replaceFirst("⚠️ 伏笔超期告警: ", "")).append("\n\n");
        }

        int maxCh = maxChapterNum(state);
        if (maxCh > 0) {
            sb.append("## 按章节时间线\n\n");
            for (int chNum = 1; chNum <= maxCh; chNum++) {
                List<String> lines = new ArrayList<>();
                for (Foreshadow fs : state.getForeshadows()) {
                    if (fs.getPlantChapter() == chNum) {
                        lines.add("- 🔵 #" + fs.getId() + " " + fs.getName()
                                + " — 埋设（" + foreshadowStatusLabel(fs.getStatus()) + "）");
                    }
                    if (fs.getTargetChapter() == chNum) {
                        lines.add("- 🎯 #" + fs.getId() + " " + fs.getName()
                                + " — 预计回收（" + foreshadowStatusLabel(fs.getStatus()) + "）");
                    }
                    if (fs.getEvents() != null) {
                        for (ForeshadowEvent ev : fs.getEvents()) {
                            if (ev.getChapter() == chNum) {
                                lines.add("- 📌 #" + fs.getId() + " " + fs.getName()
                                        + " — " + ev.getNote());
                            }
                        }
                    }
                }
                if (lines.isEmpty()) continue;
                sb.append("### 第 ").append(chNum).append(" 章\n\n");
                sb.append(String.join("\n", lines)).append("\n\n");
            }
        }

        sb.append("## 伏笔详情\n\n");
        for (Foreshadow fs : state.getForeshadows()) {
            sb.append("### #").append(fs.getId()).append(" ").append(fs.getName())
                    .append(" [").append(foreshadowStatusLabel(fs.getStatus())).append("]\n\n");
            sb.append(fs.getDescription()).append("\n\n");
            sb.append("- 埋设章节：第 **").append(fs.getPlantChapter()).append("** 章\n");
            if (fs.getTargetChapter() > 0) {
                sb.append("- 预计回收：第 **").append(fs.getTargetChapter()).append("** 章\n");
            }
            if (fs.getEvents() != null && !fs.getEvents().isEmpty()) {
                sb.append("- 进展记录：\n");
                for (ForeshadowEvent ev : fs.getEvents()) {
                    sb.append("  - 第 ").append(ev.getChapter()).append(" 章：").append(ev.getNote()).append("\n");
                }
            }
            if (fs.getResolution() != null && !fs.getResolution().isEmpty()) {
                sb.append("- 回收方式：").append(fs.getResolution()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public void saveForeshadowRoadmap(String projectDir, Progress state) {
        try {
            String content = buildForeshadowRoadmapMarkdown(state);
            String path = projectDir + "/Foreshadows.md";
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
        } catch (Exception e) {
            log.warn("保存伏笔路线图失败: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Utility methods
    // ---------------------------------------------------------------

    public int nextForeshadowID(List<Foreshadow> foreshadows) {
        int maxID = 0;
        if (foreshadows != null) {
            for (Foreshadow fs : foreshadows) {
                if (fs.getId() > maxID) maxID = fs.getId();
            }
        }
        return maxID + 1;
    }

    private Foreshadow findForeshadow(Progress state, int id) {
        if (state.getForeshadows() == null) return null;
        for (Foreshadow fs : state.getForeshadows()) {
            if (fs.getId() == id) return fs;
        }
        return null;
    }

    public void applyForeshadowUpdates(Progress state, List<ForeshadowUpdateItem> updates, int chapterNum) {
        if (updates == null || state.getForeshadows() == null) return;

        Map<Integer, ForeshadowUpdateItem> updateMap = new HashMap<>();
        for (ForeshadowUpdateItem u : updates) {
            updateMap.put(u.getId(), u);
        }

        for (Foreshadow fs : state.getForeshadows()) {
            ForeshadowUpdateItem u = updateMap.get(fs.getId());
            if (u == null) continue;

            if (u.getEvent() != null && !u.getEvent().isEmpty()) {
                if (fs.getEvents() == null) fs.setEvents(new ArrayList<>());
                fs.getEvents().add(new ForeshadowEvent(chapterNum, u.getEvent()));
            }
            if (u.getStatus() != null && !u.getStatus().isEmpty()) {
                fs.setStatus(u.getStatus());
            }
            if (u.getResolution() != null && !u.getResolution().isEmpty()) {
                fs.setResolution(u.getResolution());
            }
        }
    }

    private int maxChapterNum(Progress state) {
        int maxNum = 0;
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if (ch.getNum() > maxNum) maxNum = ch.getNum();
            }
        }
        if (state.getForeshadows() != null) {
            for (Foreshadow fs : state.getForeshadows()) {
                if (fs.getPlantChapter() > maxNum) maxNum = fs.getPlantChapter();
                if (fs.getTargetChapter() > maxNum) maxNum = fs.getTargetChapter();
                if (fs.getEvents() != null) {
                    for (ForeshadowEvent ev : fs.getEvents()) {
                        if (ev.getChapter() > maxNum) maxNum = ev.getChapter();
                    }
                }
            }
        }
        return maxNum;
    }

    private String foreshadowStatusLabel(String status) {
        if (Foreshadow.STATUS_PLANTED.equals(status)) return "已埋设";
        if (Foreshadow.STATUS_PROGRESSING.equals(status)) return "推进中";
        if (Foreshadow.STATUS_RESOLVED.equals(status)) return "已回收";
        if (Foreshadow.STATUS_ABANDONED.equals(status)) return "已放弃";
        return status != null ? status : "";
    }

    // ---------------------------------------------------------------
    // Shared helpers (also used by other services)
    // ---------------------------------------------------------------

    public static String formatChapterLine(ChapterState ch, String lang) {
        boolean en = "en".equals(LocaleHelper.normalizeLanguage(lang));
        if (en) {
            return "Chapter " + ch.getNum() + " \"" + ch.getTitle() + "\": " + ch.getOutline() + "\n";
        }
        return "第" + ch.getNum() + "章《" + ch.getTitle() + "》: " + ch.getOutline() + "\n";
    }

    public static String buildHistorySummary(Progress state, int chapterIdx, String lang) {
        boolean en = "en".equals(LocaleHelper.normalizeLanguage(lang));
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, chapterIdx - 5);
        for (int i = start; i < chapterIdx; i++) {
            ChapterState ch = state.getChapters().get(i);
            if (ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                if (en) {
                    sb.append("Chapter ").append(ch.getNum()).append(" \"").append(ch.getTitle())
                            .append("\" summary: ").append(ch.getSummary()).append("\n");
                } else {
                    sb.append("第").append(ch.getNum()).append("章《").append(ch.getTitle())
                            .append("》摘要: ").append(ch.getSummary()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    static String renderPrompt(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{." + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    static String cleanJSONResponse(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
