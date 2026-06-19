package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Whole-book post-processing: diagnosis, consistency check, roadmap generation, and execution.
 * Ported from Go postprocess.go.
 */
@Service
public class PostProcessService {

    private static final Logger log = LoggerFactory.getLogger(PostProcessService.class);

    private static final int VOLUME_SPLIT_RUNES = 150000;
    private static final int DEFAULT_CONTEXT_BUDGET = 300000;

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final ChapterService chapterService;

    public PostProcessService(ProjectService projectService,
                              StateService stateService,
                              OpenAIClient openAIClient,
                              LogBroadcaster logger,
                              ChapterService chapterService) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logger = logger;
        this.chapterService = chapterService;
    }

    /**
     * Go: GetPostProcess - returns state with book_complete flag.
     */
    public Map<String, Object> getPostProcessState() {
        PostProcessState pp = projectService.getPostProcess();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("book_complete", isBookComplete());
        resp.put("state", pp);
        return resp;
    }

    /**
     * Go: DeletePostProcess - clear reports and roadmap.
     */
    public ResponseEntity<?> clearPostProcess() {
        try {
            PostProcessState pp = new PostProcessState();
            pp.setExecuteOptions(new PostProcessExecuteOptions());
            projectService.setPostProcess(pp);

            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            return ResponseEntity.ok(Map.of("status", "cleared"));
        } catch (Exception e) {
            log.error("Clear post-process failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "clear_postprocess_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: PutPostProcessRoadmap - update roadmap items.
     */
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> updateRoadmap(Map<String, Object> body) {
        try {
            PostProcessState pp = projectService.getPostProcess();

            if (body.containsKey("roadmap")) {
                Object roadmapObj = body.get("roadmap");
                if (roadmapObj instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) roadmapObj;
                    List<RoadmapItem> updated = new ArrayList<>();
                    for (Map<String, Object> item : items) {
                        RoadmapItem ri = new RoadmapItem();
                        ri.setId((String) item.get("id"));
                        ri.setChapterNum(toInt(item.get("chapter_num")));
                        ri.setType((String) item.get("type"));
                        ri.setPriority((String) item.get("priority"));
                        ri.setFeedback((String) item.get("feedback"));
                        if (item.containsKey("selected")) {
                            ri.setSelected(Boolean.TRUE.equals(item.get("selected")));
                        }
                        ri.setStatus((String) item.getOrDefault("status", "pending"));
                        updated.add(ri);
                    }
                    pp.setRoadmap(updated);
                }
            }

            if (body.containsKey("execute_options")) {
                applyExecuteOptions(body.get("execute_options"));
            }

            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            return ResponseEntity.ok(pp);
        } catch (Exception e) {
            log.error("Update roadmap failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "update_roadmap_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: isBookFullyAccepted.
     */
    public boolean isBookComplete() {
        Progress state = projectService.getProgress();
        if (state == null || state.getChapters() == null || state.getChapters().isEmpty()) {
            return false;
        }
        for (ChapterState ch : state.getChapters()) {
            if (!"accepted".equals(ch.getStatus()) || ch.getContent() == null || ch.getContent().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if at least one report is present.
     */
    public boolean hasReports() {
        PostProcessState pp = projectService.getPostProcess();
        return (pp.getDiagnosisReport() != null && !pp.getDiagnosisReport().trim().isEmpty())
                || (pp.getConsistencyReport() != null && !pp.getConsistencyReport().trim().isEmpty());
    }

    /**
     * Returns true if at least one selected pending roadmap item exists.
     */
    public boolean hasSelectedItems() {
        PostProcessState pp = projectService.getPostProcess();
        if (pp.getRoadmap() == null) return false;
        for (RoadmapItem item : pp.getRoadmap()) {
            if (item.isSelected() && "pending".equals(item.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Apply execute_options from request body.
     */
    @SuppressWarnings("unchecked")
    public void applyExecuteOptions(Object obj) {
        if (obj == null) return;
        PostProcessState pp = projectService.getPostProcess();
        if (obj instanceof Map) {
            Map<String, Object> opts = (Map<String, Object>) obj;
            PostProcessExecuteOptions options = pp.getExecuteOptions();
            if (options == null) {
                options = new PostProcessExecuteOptions();
                pp.setExecuteOptions(options);
            }
            if (opts.containsKey("run_smooth_transitions_first")) {
                options.setRunSmoothTransitionsFirst(Boolean.TRUE.equals(opts.get("run_smooth_transitions_first")));
            }
            if (opts.containsKey("include_polish")) {
                options.setIncludePolish(Boolean.TRUE.equals(opts.get("include_polish")));
            }
        }
    }

    /**
     * Go: DiagnoseBookAction - full book diagnosis.
     */
    public void fullDiagnose(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("postprocess_diagnose");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            ProjectSettings settings = projectService.getSettings();
            PostProcessState pp = projectService.getPostProcess();

            openAIClient.validateAPIConfig(apiCfg);
            if (!isBookComplete()) {
                throw new Exception("全书尚未完成（需所有章节已确认）");
            }

            // Build bundle
            String settingsText = buildAllSettingsText(cfg, settings, state);
            String summaryIndex = buildSummaryIndex(state);
            String fullText = buildFullBookText(state);
            int budget = getContextBudget(apiCfg);

            logger.infoKey("log.postprocess_material", fullText.length(), (int)(fullText.length() * 1.5), "full");

            String fullTextBlock = fullText;
            String modeNote = "";
            int fixedCost = (int)((settingsText.length() + summaryIndex.length()) * 1.5);
            int fullCost = (int)(fullText.length() * 1.5);
            int usable = (int)(budget * 0.65);
            if (fixedCost + fullCost > usable) {
                fullTextBlock = "（正文过长，本轮诊断仅依据章节摘要索引）";
                modeNote = "注意：因上下文预算限制，本次诊断基于摘要索引而非全文。";
            }

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getBookDiagnosis(), Map.of(
                    "SettingsText", settingsText,
                    "SummaryIndex", summaryIndex,
                    "FullText", fullTextBlock,
                    "ModeNote", modeNote
            ));
            String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "book_diagnosis");

            logger.stepInfo(1, 3, "正在进行全书诊断...");
            String diagResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (diagResp == null || diagResp.isEmpty()) {
                throw new Exception("全书诊断调用失败或被取消");
            }
            pp.setDiagnosisReport(diagResp.trim());
            pp.setDiagnosedAt(Instant.now().toString());

            // Also run consistency check
            logger.stepInfo(2, 3, "正在进行一致性核查...");
            String consistReport = runConsistencyCheck(ctx, apiCfg, cfg, settingsText, summaryIndex, fullText);
            pp.setConsistencyReport(consistReport);
            pp.setConsistencyAt(Instant.now().toString());

            // Build roadmap from reports
            logger.stepInfo(3, 3, "正在生成路线图...");
            List<RoadmapItem> items = buildRoadmapItems(ctx, apiCfg, cfg, pp.getDiagnosisReport(), pp.getConsistencyReport());
            pp.setRoadmap(items);
            pp.setRoadmapAt(Instant.now().toString());

            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            logger.successKey("log.postprocess_diagnose_done", items.size());
            logger.taskEnd("postprocess_diagnose", true);
        } catch (Exception e) {
            log.error("Full diagnose failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.postprocess_diagnose_cancelled");
            } else {
                logger.errorKey("log.postprocess_diagnose_failed", e.getMessage());
            }
            logger.taskEnd("postprocess_diagnose", false);
        }
    }

    /**
     * Go: ConsistencyCheckBookAction - consistency check only.
     */
    public void consistencyCheck(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("postprocess_consistency");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            ProjectSettings settings = projectService.getSettings();
            PostProcessState pp = projectService.getPostProcess();

            openAIClient.validateAPIConfig(apiCfg);
            if (!isBookComplete()) {
                throw new Exception("全书尚未完成");
            }

            String settingsText = buildAllSettingsText(cfg, settings, state);
            String summaryIndex = buildSummaryIndex(state);
            String fullText = buildFullBookText(state);

            String report = runConsistencyCheck(ctx, apiCfg, cfg, settingsText, summaryIndex, fullText);
            pp.setConsistencyReport(report);
            pp.setConsistencyAt(Instant.now().toString());

            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            logger.successKey("log.postprocess_consistency_done");
            logger.taskEnd("postprocess_consistency", true);
        } catch (Exception e) {
            log.error("Consistency check failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.postprocess_consistency_cancelled");
            } else {
                logger.errorKey("log.postprocess_consistency_failed", e.getMessage());
            }
            logger.taskEnd("postprocess_consistency", false);
        }
    }

    /**
     * Go: BuildRoadmapAction - build roadmap from existing reports.
     */
    public void buildRoadmap(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("postprocess_roadmap");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            PostProcessState pp = projectService.getPostProcess();

            openAIClient.validateAPIConfig(apiCfg);

            String diagReport = pp.getDiagnosisReport() != null ? pp.getDiagnosisReport() : "";
            String consistReport = pp.getConsistencyReport() != null ? pp.getConsistencyReport() : "";

            if (diagReport.trim().isEmpty() && consistReport.trim().isEmpty()) {
                throw new Exception("缺少诊断或核查报告");
            }

            List<RoadmapItem> items = buildRoadmapItems(ctx, apiCfg, cfg, diagReport, consistReport);
            pp.setRoadmap(items);
            pp.setRoadmapAt(Instant.now().toString());

            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            logger.successKey("log.postprocess_roadmap_done", items.size());
            logger.taskEnd("postprocess_roadmap", true);
        } catch (Exception e) {
            log.error("Build roadmap failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.postprocess_roadmap_cancelled");
            } else {
                logger.errorKey("log.postprocess_roadmap_failed", e.getMessage());
            }
            logger.taskEnd("postprocess_roadmap", false);
        }
    }

    /**
     * Go: PostPostProcessExecute - execute selected roadmap items.
     * Each item triggers a chapter revision for the target chapter.
     */
    public void executeRoadmap(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("postprocess_execute");

            PostProcessState pp = projectService.getPostProcess();
            Progress state = projectService.getProgress();

            if (pp.getExecuteOptions() != null && pp.getExecuteOptions().isRunSmoothTransitionsFirst()) {
                logger.infoKey("log.postprocess_smooth_first");
                chapterService.smoothTransitions(ctx);
            }

            List<RoadmapItem> pending = new ArrayList<>();
            if (pp.getRoadmap() != null) {
                for (RoadmapItem item : pp.getRoadmap()) {
                    if (item.isSelected() && "pending".equals(item.getStatus())) {
                        pending.add(item);
                    }
                }
            }

            logger.infoKey("log.postprocess_execute_start", pending.size());

            for (int n = 0; n < pending.size(); n++) {
                if (ctx != null && ctx.isCancelled()) {
                    throw new Exception("任务已取消");
                }
                RoadmapItem item = pending.get(n);
                item.setStatus("running");
                logger.stepInfo(n + 1, pending.size(), String.format("正在执行: 第%d章 %s - %s",
                        item.getChapterNum(), item.getType(), truncate(item.getFeedback(), 50)));

                try {
                    // Find the chapter index
                    int chapterIdx = -1;
                    if (state.getChapters() != null) {
                        for (int i = 0; i < state.getChapters().size(); i++) {
                            if (state.getChapters().get(i).getNum() == item.getChapterNum()) {
                                chapterIdx = i;
                                break;
                            }
                        }
                    }
                    if (chapterIdx == -1) {
                        throw new Exception("章节不存在: " + item.getChapterNum());
                    }

                    // Use chapter revision with the roadmap feedback
                    chapterService.reviseSpecificChapter(ctx, item.getChapterNum(), item.getFeedback());
                    item.setStatus("done");
                    logger.infoKey("log.postprocess_item_done", item.getChapterNum());
                } catch (Exception e) {
                    item.setStatus("failed");
                    item.setError(e.getMessage());
                    logger.warnKey("log.postprocess_item_failed", item.getChapterNum(), e.getMessage());
                }
            }

            pp.setLastExecuteAt(Instant.now().toString());
            String path = projectService.getPostprocessPath();
            projectService.savePostProcess(path, pp);

            long doneCount = pending.stream().filter(i -> "done".equals(i.getStatus())).count();
            logger.successKey("log.postprocess_execute_done", doneCount, pending.size());
            logger.taskEnd("postprocess_execute", true);
        } catch (Exception e) {
            log.error("Execute roadmap failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.postprocess_execute_cancelled");
            } else {
                logger.errorKey("log.postprocess_execute_failed", e.getMessage());
            }
            logger.taskEnd("postprocess_execute", false);
        }
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private String runConsistencyCheck(OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
                                        String settingsText, String summaryIndex, String fullText) throws Exception {
        List<String> volumes = splitTextByRunes(fullText, VOLUME_SPLIT_RUNES);

        if (volumes.size() == 1) {
            logger.infoKey("log.postprocess_consistency_single");
            return runConsistencyVolume(ctx, apiCfg, cfg, settingsText, summaryIndex, volumes.get(0), 1, 1);
        }

        logger.infoKey("log.postprocess_consistency_multi", volumes.size());
        List<String> reports = new ArrayList<>();
        for (int i = 0; i < volumes.size(); i++) {
            if (ctx != null && ctx.isCancelled()) {
                throw new Exception("任务已取消");
            }
            logger.stepInfo(i + 1, volumes.size(), String.format("正在核查第 %d/%d 卷...", i + 1, volumes.size()));
            String report = runConsistencyVolume(ctx, apiCfg, cfg, settingsText, summaryIndex, volumes.get(i), i + 1, volumes.size());
            reports.add(String.format("### 第 %d/%d 卷\n\n%s", i + 1, volumes.size(), report));
        }
        return String.join("\n\n---\n\n", reports);
    }

    private String runConsistencyVolume(OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
                                         String settingsText, String summaryIndex, String volumeText,
                                         int volIdx, int volTotal) throws Exception {
        String volNote = "";
        if (volTotal > 1) {
            volNote = String.format("（全书第 %d/%d 卷）", volIdx, volTotal);
        }
        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getBookConsistencyCheck(), Map.of(
                "SettingsText", settingsText,
                "SummaryIndex", summaryIndex,
                "FullText", volumeText,
                "VolumeNote", volNote
        ));
        String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "book_consistency_check");

        String resp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (resp == null || resp.isEmpty()) {
            throw new Exception("一致性核查调用失败或被取消");
        }
        return resp.trim();
    }

    private List<RoadmapItem> buildRoadmapItems(OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
                                                 String diagnosisReport, String consistencyReport) throws Exception {
        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getBookRoadmap(), Map.of(
                "DiagnosisReport", diagnosisReport != null ? diagnosisReport : "",
                "ConsistencyReport", consistencyReport != null ? consistencyReport : ""
        ));
        String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "book_roadmap");

        String resp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (resp == null || resp.isEmpty()) {
            throw new Exception("路线图生成调用失败或被取消");
        }

        String cleaned = OpenAIClient.cleanJSONFences(resp);
        // Try parsing as {items: [...]} or plain array
        try {
            Map<?, ?> wrapper = openAIClient.getObjectMapper().readValue(cleaned, Map.class);
            Object items = wrapper.get("items");
            if (items instanceof List) {
                return parseRoadmapEntries(items);
            }
        } catch (Exception ignored) {}

        try {
            List<?> arr = openAIClient.getObjectMapper().readValue(cleaned, List.class);
            return parseRoadmapEntries(arr);
        } catch (Exception e) {
            throw new Exception("解析路线图 JSON 失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<RoadmapItem> parseRoadmapEntries(Object listObj) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) listObj;
        List<RoadmapItem> items = new ArrayList<>();
        int counter = 0;
        for (Map<String, Object> e : entries) {
            int chapterNum = toInt(e.get("chapter_num"));
            String feedback = (String) e.get("feedback");
            if (chapterNum <= 0 || feedback == null || feedback.trim().isEmpty()) continue;

            counter++;
            RoadmapItem ri = new RoadmapItem();
            ri.setId("rm_" + counter);
            ri.setChapterNum(chapterNum);
            ri.setType((String) e.getOrDefault("type", "style"));
            ri.setPriority((String) e.getOrDefault("priority", "P1"));
            ri.setFeedback(feedback.trim());
            ri.setSelected(true);
            ri.setStatus("pending");
            items.add(ri);
        }
        // Sort by priority then chapter
        items.sort((a, b) -> {
            int pa = priorityOrder(a.getPriority());
            int pb = priorityOrder(b.getPriority());
            if (pa != pb) return pa - pb;
            return a.getChapterNum() - b.getChapterNum();
        });
        return items;
    }

    private int priorityOrder(String p) {
        return switch (p != null ? p : "") {
            case "P0" -> 0;
            case "P1" -> 1;
            case "P2" -> 2;
            default -> 3;
        };
    }

    private String buildFullBookText(Progress state) {
        StringBuilder sb = new StringBuilder();
        String title = state.getTitle() != null && !state.getTitle().isEmpty() ? state.getTitle() : "未命名";
        sb.append("《").append(title).append("》\n");
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if (ch.getContent() == null || ch.getContent().isEmpty()) continue;
                sb.append(String.format("\n\n第 %d 章 %s\n\n%s", ch.getNum(), ch.getTitle(), ch.getContent()));
            }
        }
        return sb.toString();
    }

    private String buildSummaryIndex(Progress state) {
        StringBuilder sb = new StringBuilder();
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if (ch.getContent() == null || ch.getContent().isEmpty()) continue;
                String summary = ch.getSummary() != null && !ch.getSummary().isEmpty() ? ch.getSummary() : "（无摘要）";
                sb.append(String.format("第%d章《%s》| %s\n", ch.getNum(), ch.getTitle(), summary));
            }
        }
        return sb.toString();
    }

    private String buildAllSettingsText(Config cfg, ProjectSettings settings, Progress state) {
        StringBuilder sb = new StringBuilder();
        String title = (cfg.getStory().getTitle() != null && !cfg.getStory().getTitle().isEmpty())
                ? cfg.getStory().getTitle() : (state.getTitle() != null ? state.getTitle() : "");
        sb.append("标题：").append(title).append("\n");
        sb.append("类型：").append(cfg.getStory().getType()).append("\n");
        sb.append("写作风格：").append(cfg.getStory().getWritingStyle()).append("\n");
        if (cfg.getStory().getWritingPov() != null && !cfg.getStory().getWritingPov().isEmpty()) {
            sb.append("叙述视角：").append(cfg.getStory().getWritingPov()).append("\n");
        }
        String synopsis = (cfg.getStory().getStorySynopsis() != null && !cfg.getStory().getStorySynopsis().isEmpty())
                ? cfg.getStory().getStorySynopsis() : (state.getStorySynopsis() != null ? state.getStorySynopsis() : "");
        sb.append("梗概：").append(synopsis).append("\n");
        if (state.getCorePrompt() != null && !state.getCorePrompt().isEmpty()) {
            sb.append("核心提示词：").append(state.getCorePrompt()).append("\n");
        }
        // Characters
        if (settings != null && settings.getCharacters() != null && !settings.getCharacters().isEmpty()) {
            sb.append("\n【角色设定】\n");
            for (var c : settings.getCharacters()) {
                sb.append("· ").append(c.getName());
                if (c.getAge() != null && !c.getAge().isEmpty()) sb.append("（").append(c.getAge()).append("）");
                sb.append("\n");
                if (c.getPersonality() != null && !c.getPersonality().isEmpty())
                    sb.append("  性格：").append(c.getPersonality()).append("\n");
                if (c.getBackground() != null && !c.getBackground().isEmpty())
                    sb.append("  背景：").append(c.getBackground()).append("\n");
            }
        }
        // Foreshadows
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            sb.append("\n【伏笔】\n");
            for (var fs : state.getForeshadows()) {
                sb.append(String.format("· %s [埋设第%d章→预计第%d章回收] 状态:%s — %s\n",
                        fs.getName(), fs.getPlantChapter(), fs.getTargetChapter(),
                        fs.getStatus(), fs.getDescription()));
            }
        }
        return sb.toString();
    }

    private int getContextBudget(APIConfig apiCfg) {
        if (apiCfg != null && apiCfg.getContextBudgetTokens() > 0) {
            return apiCfg.getContextBudgetTokens();
        }
        return DEFAULT_CONTEXT_BUDGET;
    }

    private List<String> splitTextByRunes(String text, int maxRunes) {
        if (text == null || text.length() <= maxRunes) {
            return List.of(text != null ? text : "");
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxRunes) {
            int end = Math.min(i + maxRunes, text.length());
            parts.add(text.substring(i, end));
        }
        return parts;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "…";
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); } catch (Exception ignored) {}
        }
        return 0;
    }
}
