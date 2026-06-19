package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter generation, revision, confirmation, editing, polishing, smooth transitions, and deletion.
 * Ported from Go writing.go + relevant handler logic.
 * The 5-step pipeline: outline consistency check → content generation → summary → fact-check → foreshadow sync.
 */
@Service
public class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private static final int PREV_TAIL_MAX_RUNES = 800;
    private static final int OPENING_MAX_RUNES = 1000;
    private static final int FUTURE_OUTLINE_WINDOW = 10;
    private static final int MAX_FACT_CHECK_RETRIES = 3;

    private static final Pattern CHAPTER_META_START_ZH = Pattern.compile("^[（(]?第\\s*\\d+\\s*章");
    private static final Pattern CHAPTER_META_START_EN = Pattern.compile("(?i)^(?:chapter\\s+\\d+|part\\s+\\d+)");

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final EditingService editingService;
    private final ForeshadowService foreshadowService;
    private final ForeshadowConsistencyService foreshadowConsistencyService;
    private final WritingConflictService writingConflictService;
    private final SettingsService settingsService;
    private final TaskManager taskManager;
    private final SkillService skillService;

    public ChapterService(ProjectService projectService,
                          StateService stateService,
                          OpenAIClient openAIClient,
                          LogBroadcaster logger,
                          EditingService editingService,
                          ForeshadowService foreshadowService,
                          ForeshadowConsistencyService foreshadowConsistencyService,
                          WritingConflictService writingConflictService,
                          SettingsService settingsService,
                          TaskManager taskManager,
                          SkillService skillService) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logger = logger;
        this.editingService = editingService;
        this.foreshadowService = foreshadowService;
        this.foreshadowConsistencyService = foreshadowConsistencyService;
        this.writingConflictService = writingConflictService;
        this.settingsService = settingsService;
        this.taskManager = taskManager;
        this.skillService = skillService;
    }

    /**
     * Go: PostChapterGenerate handler + GenerateChapterAction with auto-confirm loop.
     * Called in background thread.
     */
    public void generateChapter(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("chapter_generation");

            while (true) {
                Progress state = projectService.getProgress();
                int chIdx = state.getCurrentChapterIndex();
                String chTitle = "";
                if (state.getChapters() != null && chIdx < state.getChapters().size()) {
                    chTitle = state.getChapters().get(chIdx).getTitle();
                }

                logger.infoKey("log.chapter_writing", chIdx + 1);
                generateChapterAction(ctx, state);

                logger.successKey("log.chapter_write_done", chIdx + 1, chTitle);
                broadcastProgress();

                // Auto-confirm mode
                if (!taskManager.isAutoConfirm()) {
                    break;
                }
                try {
                    confirmChapterAction(state);
                } catch (Exception e) {
                    logger.warnKey("log.chapter_autoconfirm_failed", e.getMessage());
                    break;
                }
                logger.successKey("log.chapter_autoconfirmed", chIdx + 1, chTitle);
                broadcastProgress();

                if (state.getCurrentChapterIndex() >= state.getChapters().size()) {
                    logger.successKey("log.all_chapters_done");
                    break;
                }
                if (ctx != null && ctx.isCancelled()) {
                    logger.warnKey("log.autowrite_cancelled");
                    break;
                }
            }

            logger.taskEnd("chapter_generation", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Chapter generation failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.chapter_write_cancelled");
            } else if (e instanceof WritingConflictException) {
                logger.warnKey("log.chapter_write_conflict_pause");
            } else {
                logger.errorKey("log.chapter_write_failed", e.getMessage());
            }
            logger.taskEnd("chapter_generation", false);
            broadcastProgress();
        }
    }

    /**
     * Go: GenerateChapterAction - the 5-step chapter generation pipeline.
     */
    private void generateChapterAction(OpenAIClient.CancellationToken ctx, Progress state) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        String progressPath = projectService.getProgressPath();
        ProjectSettings settings = projectService.getSettings();

        openAIClient.validateAPIConfig(apiCfg);
        if (!"writing".equals(state.getPhase())) {
            throw new Exception("当前不在写作阶段");
        }
        if (state.getCurrentChapterIndex() >= state.getChapters().size()) {
            throw new Exception("所有章节已完成");
        }

        int i = state.getCurrentChapterIndex();
        ChapterState ch = state.getChapters().get(i);

        if ("accepted".equals(ch.getStatus())) {
            throw new Exception("第 " + ch.getNum() + " 章已确认，请确认当前章节或重置进度");
        }

        ch.setStatus("writing");
        stateService.saveProgress(progressPath, state);

        logger.infoKey("log.chapter_start", ch.getNum(), ch.getTitle());

        // Step 1: Outline consistency check (if not first chapter)
        if (i > 0) {
            logger.stepInfo(1, 5, "正在检查本章大纲与当前剧情的一致性...");
            boolean revised = checkOutlineConsistency(ctx, apiCfg, cfg, state, i);
            if (revised) {
                stateService.saveProgress(progressPath, state);
                logger.infoKey("log.outline_auto_revised");
            } else {
                logger.infoKey("log.outline_consistent");
            }
        }

        // Foreshadow outline check
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(ctx);
        }

        // Fact-check retry loop
        String extraConstraints = "";
        List<String> accumulatedIssues = new ArrayList<>();

        for (int attempt = 0; attempt <= MAX_FACT_CHECK_RETRIES; attempt++) {
            if (ctx != null && ctx.isCancelled()) {
                throw new Exception("任务已取消");
            }

            // Step 2: Generate content
            logger.stepInfo(2, 5, "正在构思并撰写正文...");
            String content = generateChapterContentStreamWithRetry(ctx, apiCfg, cfg, state, i, settings, extraConstraints);
            if (content == null || content.isEmpty()) {
                throw new Exception("正文生成失败或被取消");
            }
            ch.setContent(content);
            logger.infoKey("log.prose_done", content.length());

            // Step 3: Generate summary
            logger.stepInfo(3, 5, "正在提炼本章摘要...");
            String summary = generateChapterSummaryWithRetry(ctx, apiCfg, cfg, content);
            if (summary == null || summary.isEmpty()) {
                throw new Exception("摘要提炼失败或被取消");
            }
            ch.setSummary(summary);
            logger.infoKey("log.summary_done");

            // Step 4: Fact-check
            logger.stepInfo(4, 5, "正在对本章进行事实核查...");
            String historySummary = ForeshadowService.buildHistorySummary(state, i, cfg.getLanguage());
            String factCheckResult = generateChapterFactCheckWithRetry(ctx, apiCfg, cfg, state, i, content, historySummary);

            boolean[] fcResult = parseFactCheckResult(factCheckResult);
            boolean failed = fcResult[0];
            String issues = (String) factCheckResult; // simplified

            if (failed) {
                accumulatedIssues = writingConflictService.mergeUniqueIssues(
                        accumulatedIssues, writingConflictService.splitFactCheckIssues(issues));
                if (attempt < MAX_FACT_CHECK_RETRIES) {
                    logger.warnKey("log.factcheck_retry", ch.getNum(), attempt + 1);
                    logger.warnKey("log.factcheck_details", issues);
                    continue;
                }

                logger.warnKey("log.factcheck_max_retries");
                WritingConflictAnalysis analysis = writingConflictService.analyzeWritingConflict(
                        ctx, state, i, content, accumulatedIssues);

                if (analysis != null && analysis.isReconcilable()
                        && analysis.getExtraConstraints() != null
                        && !analysis.getExtraConstraints().trim().isEmpty()) {
                    logger.infoKey("log.conflict_retry");
                    extraConstraints = analysis.getExtraConstraints().trim();
                    content = generateChapterContentStreamWithRetry(ctx, apiCfg, cfg, state, i, settings, extraConstraints);
                    if (content == null || content.isEmpty()) {
                        throw new Exception("正文生成失败或被取消");
                    }
                    ch.setContent(content);
                    summary = generateChapterSummaryWithRetry(ctx, apiCfg, cfg, content);
                    if (summary == null || summary.isEmpty()) {
                        throw new Exception("摘要提炼失败或被取消");
                    }
                    ch.setSummary(summary);
                    factCheckResult = generateChapterFactCheckWithRetry(ctx, apiCfg, cfg, state, i, content, historySummary);
                    fcResult = parseFactCheckResult(factCheckResult);
                    if (!fcResult[0]) {
                        logger.infoKey("log.factcheck_constraint_pass");
                    }
                }

                WritingConflict conflict = writingConflictService.buildWritingConflict(
                        state, i, accumulatedIssues, analysis);
                String lang = cfg.getLanguage();
                conflict.setSuggestedActions(writingConflictService.ensureConflictActions(
                        conflict.getSuggestedActions(), lang));
                state.setPendingWritingConflict(conflict);
                stateService.saveProgress(progressPath, state);
                logger.writingConflict(conflict);
                throw new WritingConflictException(conflict);
            }
            logger.infoKey("log.factcheck_pass");
            break;
        }

        state.setPendingWritingConflict(null);

        // Step 5: Foreshadow sync
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            logger.stepInfo(5, 5, "正在更新伏笔状态...");
            foreshadowService.syncForeshadowsAfterChapter(ctx, cfg, apiCfg, state, i, progressPath);
        }

        // Save markdown
        saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());

        ch.setStatus("review");
        state.setCurrentChapterIndex(i);
        stateService.saveProgress(progressPath, state);

        logger.successKey("log.chapter_write_complete", ch.getNum());
    }

    /**
     * Parse fact-check result. Returns [failed (boolean encoded as 0/1)].
     */
    private boolean[] parseFactCheckResult(String raw) {
        if (raw == null || raw.isEmpty()) return new boolean[]{false};
        boolean failed = raw.contains("FAIL");
        return new boolean[]{failed};
    }

    /**
     * Go: checkOutlineConsistency - pre-writing outline check.
     */
    private boolean checkOutlineConsistency(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                             Config cfg, Progress state, int idx) {
        try {
            ChapterState ch = state.getChapters().get(idx);
            if (ch.getOutline() == null || ch.getOutline().trim().isEmpty()) {
                return false;
            }

            String lang = cfg.getLanguage();
            String prevEnding = "";
            if (idx > 0 && state.getChapters().get(idx - 1).getContent() != null
                    && !state.getChapters().get(idx - 1).getContent().isEmpty()) {
                String tail = tailAtParagraph(state.getChapters().get(idx - 1).getContent(), PREV_TAIL_MAX_RUNES);
                if (!tail.isEmpty()) {
                    boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
                    if (en) {
                        prevEnding = "[Previous chapter ending]\n" + tail + "\n\n";
                    } else {
                        prevEnding = "【上一章结尾原文】\n" + tail + "\n\n";
                    }
                }
            }

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getOutlineConsistencyCheck(), Map.of(
                    "ChapterNum", String.valueOf(ch.getNum()),
                    "ChapterTitle", ch.getTitle(),
                    "ChapterOutline", ch.getOutline(),
                    "HistorySummary", ForeshadowService.buildHistorySummary(state, idx, lang),
                    "PreviousEnding", prevEnding
            ));
            String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_brief_json");

            String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (rawResp == null || rawResp.isEmpty()) return false;

            String jsonStr = ForeshadowConsistencyService.extractJSON(OpenAIClient.cleanJSONFences(rawResp));
            if (jsonStr == null || jsonStr.isEmpty()) return false;

            Map<?, ?> resp = openAIClient.getObjectMapper().readValue(jsonStr, Map.class);
            Object conflictObj = resp.get("conflict");
            boolean conflict = Boolean.TRUE.equals(conflictObj);
            String revisedOutline = (String) resp.get("revised_outline");

            if (!conflict || revisedOutline == null || revisedOutline.trim().isEmpty()) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) resp.get("issues");
            String issuesStr = issues != null ? String.join("；", issues) : "";
            logger.warnKey("log.outline_conflict", ch.getNum(), issuesStr);
            ch.setOutline(revisedOutline.trim());
            return true;
        } catch (Exception e) {
            logger.warnKey("log.outline_check_failed", e.getMessage());
            return false;
        }
    }

    /**
     * Go: generateChapterContentStream - generate chapter content with streaming.
     */
    private String generateChapterContentStream(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                                 Config cfg, Progress state, int idx,
                                                 ProjectSettings settings, String extraWritingConstraints) throws Exception {
        ChapterState ch = state.getChapters().get(idx);
        String lang = cfg.getLanguage();

        String historySummary = ForeshadowService.buildHistorySummary(state, idx, lang);
        StoryConfig snapshot = state.getStoryConfigSnapshot();
        if (snapshot == null) {
            snapshot = cfg.getStory();
        }

        String foreshadowContext = foreshadowService.formatActiveForeshadowsForChapter(
                state.getForeshadows(), ch.getNum());
        String characterContext = buildCharacterContextForLang(settings, ch.getOutline(), lang);
        String worldviewContext = buildWorldviewContextForLang(settings, ch.getOutline(), lang);
        String outlineConstraints = buildOutlineConstraintsForLang(state, idx, lang);

        String title = preferUserValue(cfg.getStory().getTitle(), state.getTitle());
        String storySynopsis = preferUserValue(cfg.getStory().getStorySynopsis(), state.getStorySynopsis());

        Map<String, String> promptVars = new HashMap<>();
        promptVars.put("Title", title);
        promptVars.put("ChapterNum", String.valueOf(ch.getNum()));
        promptVars.put("CorePrompt", state.getCorePrompt() != null ? state.getCorePrompt() : "");
        promptVars.put("StorySynopsis", storySynopsis);
        promptVars.put("HistorySummary", historySummary);
        promptVars.put("PreviousEnding", buildPreviousChapterTailForLang(state, idx, lang));
        promptVars.put("ChapterTitle", ch.getTitle());
        promptVars.put("ChapterOutline", ch.getOutline());
        promptVars.put("WritingStyle", cfg.getStory().getWritingStyle());
        promptVars.put("WritingPOV", cfg.getStory().getWritingPov());
        promptVars.put("CharacterContext", characterContext);
        promptVars.put("WorldviewContext", worldviewContext);
        promptVars.put("TargetWords", String.valueOf(snapshot.getTargetWordsPerChapter()));
        promptVars.put("Foreshadows", foreshadowContext);
        promptVars.put("OutlineConstraints", outlineConstraints);

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getChapterWriting(), promptVars);

        // Append extra constraints block
        if (extraWritingConstraints != null && !extraWritingConstraints.trim().isEmpty()) {
            boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
            String block = en
                    ? "[Extra writing constraints (fact-check reconciliation)]\n" + extraWritingConstraints
                    : "【补充写作约束（事实核查冲突调和）】\n" + extraWritingConstraints;
            userPrompt += "\n\n" + block;
        }

        String systemPrompt = state.getCorePrompt();
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = LocaleHelper.systemPromptFor(lang, "author_default");
        }

        // Stream start notification
        logger.streamStart(idx);

        String content = openAIClient.callAPIStream(ctx, apiCfg, systemPrompt, userPrompt,
                chunk -> logger.contentChunk(idx, chunk), null);

        return stripChapterMetaProse(content, lang);
    }

    /**
     * Go: generateChapterContentStreamWithRetryLog - retry wrapper.
     */
    private String generateChapterContentStreamWithRetry(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                                          Config cfg, Progress state, int idx,
                                                          ProjectSettings settings, String extraConstraints) {
        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                String content = generateChapterContentStream(ctx, apiCfg, cfg, state, idx, settings, extraConstraints);
                if (content != null && !content.isEmpty()) return content;
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = openAIClient.getWaitTime(retryCount);
                logger.warnKey("log.content_gen_retry", e.getMessage(), retryCount, waitTime);
                try {
                    Thread.sleep(waitTime * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }
        }
    }

    /**
     * Go: generateChapterSummaryWithRetryLog.
     */
    private String generateChapterSummaryWithRetry(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                                    Config cfg, String content) {
        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getChapterSummary(),
                        Map.of("ChapterContent", content));
                String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "summary_analyst");
                String summary = openAIClient.callAPI(ctx, apiCfg, systemPrompt, userPrompt, null);
                if (summary != null && !summary.isEmpty()) return summary;
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = openAIClient.getWaitTime(retryCount);
                logger.warnKey("log.summary_retry", e.getMessage(), retryCount, waitTime);
                try {
                    Thread.sleep(waitTime * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }
        }
    }

    /**
     * Go: generateChapterFactCheckWithRetryLog.
     */
    private String generateChapterFactCheckWithRetry(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                                      Config cfg, Progress state, int idx,
                                                      String content, String historySummary) {
        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                ChapterState ch = state.getChapters().get(idx);
                String lang = cfg.getLanguage();
                String outlineConstraints = buildOutlineConstraintsForLang(state, idx, lang);

                String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getFactCheck(), Map.of(
                        "ChapterContent", content,
                        "HistorySummary", historySummary,
                        "CorePrompt", "",
                        "ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "",
                        "OutlineConstraints", outlineConstraints
                ));

                String systemPrompt = LocaleHelper.systemPromptFor(lang, "fact_checker_json");
                String result = openAIClient.callAPI(ctx, apiCfg, systemPrompt, userPrompt, null);
                if (result != null && !result.isEmpty()) return result;
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = openAIClient.getWaitTime(retryCount);
                logger.warnKey("log.factcheck_api_retry", e.getMessage(), retryCount, waitTime);
                try {
                    Thread.sleep(waitTime * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "";
                }
            }
        }
    }

    /**
     * Go: ReviseChapterAction - revise the current chapter.
     */
    public void reviseChapter(OpenAIClient.CancellationToken ctx, String feedback) {
        try {
            logger.taskStart("chapter_revision");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();
            ProjectSettings settings = projectService.getSettings();

            openAIClient.validateAPIConfig(apiCfg);
            if (!"writing".equals(state.getPhase())) {
                throw new Exception("当前不在写作阶段");
            }

            int chapterIdx = state.getCurrentChapterIndex();
            if (chapterIdx >= state.getChapters().size()) {
                throw new Exception("章节索引越界");
            }

            ChapterState ch = state.getChapters().get(chapterIdx);
            if (!"review".equals(ch.getStatus()) && !"writing".equals(ch.getStatus())) {
                throw new Exception("当前章节不在审核/写作状态");
            }

            logger.infoKey("log.chapter_modifying", ch.getNum(), ch.getTitle());

            // Step 1: Revise content
            logger.stepInfo(1, 3, "正在根据意见修订正文...");
            String revisedContent = reviseChapterContentStream(ctx, apiCfg, cfg, state, chapterIdx, feedback, settings);
            if (revisedContent == null || revisedContent.isEmpty()) {
                throw new Exception("修订章节失败");
            }
            ch.setContent(revisedContent);
            logger.infoKey("log.prose_revised", revisedContent.length());

            // Step 2: Regenerate summary
            logger.stepInfo(2, 3, "重新提炼摘要...");
            String summary = generateChapterSummaryWithRetry(ctx, apiCfg, cfg, ch.getContent());
            if (summary == null || summary.isEmpty()) {
                throw new Exception("摘要提炼失败或被取消");
            }
            ch.setSummary(summary);
            logger.infoKey("log.summary_done");

            saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());

            // Step 3: Revise subsequent outlines
            if (chapterIdx + 1 < state.getChapters().size()) {
                logger.stepInfo(3, 3, "正在修订后续章节大纲...");
                reviseSubsequentOutlines(ctx, apiCfg, cfg, state, chapterIdx, feedback);
            }

            ch.setStatus("review");
            stateService.saveProgress(progressPath, state);

            if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
                foreshadowService.syncForeshadowsAfterChapter(ctx, cfg, apiCfg, state, chapterIdx, progressPath);
                stateService.saveProgress(progressPath, state);
            }

            logger.successKey("log.chapter_revised");
            logger.taskEnd("chapter_revision", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Chapter revision failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.chapter_revise_cancelled");
            } else {
                logger.errorKey("log.chapter_revise_failed", e.getMessage());
            }
            logger.taskEnd("chapter_revision", false);
        }
    }

    /**
     * Go: ReviseSpecificChapterAction - revise a specific chapter by number.
     */
    public void reviseSpecificChapter(OpenAIClient.CancellationToken ctx, int chapterNum, String feedback) {
        try {
            logger.taskStart("chapter_revision");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();
            ProjectSettings settings = projectService.getSettings();

            openAIClient.validateAPIConfig(apiCfg);
            if (feedback == null || feedback.trim().isEmpty()) {
                throw new Exception("缺少修改意见");
            }

            int chapterIdx = -1;
            if (state.getChapters() != null) {
                for (int i = 0; i < state.getChapters().size(); i++) {
                    if (state.getChapters().get(i).getNum() == chapterNum) {
                        chapterIdx = i;
                        break;
                    }
                }
            }
            if (chapterIdx == -1) {
                throw new Exception("第 " + chapterNum + " 章不存在");
            }

            ChapterState ch = state.getChapters().get(chapterIdx);
            if (ch.getContent() == null || ch.getContent().isEmpty()) {
                throw new Exception("第 " + chapterNum + " 章尚未生成内容，无法修订");
            }
            if ("writing".equals(ch.getStatus())) {
                throw new Exception("第 " + chapterNum + " 章正在写作中，无法修订");
            }

            logger.infoKey("log.chapter_specific_revising_long", ch.getNum(), ch.getTitle());

            // Step 1: Revise content
            logger.stepInfo(1, 2, "正在根据意见修订正文...");
            String revisedContent = reviseChapterContentStream(ctx, apiCfg, cfg, state, chapterIdx, feedback, settings);
            if (revisedContent == null || revisedContent.isEmpty()) {
                throw new Exception("修订章节失败");
            }
            ch.setContent(revisedContent);
            logger.infoKey("log.prose_specific_revised", revisedContent.length());

            // Step 2: Regenerate summary
            logger.stepInfo(2, 2, "重新提炼摘要...");
            String summary = generateChapterSummaryWithRetry(ctx, apiCfg, cfg, ch.getContent());
            if (summary == null || summary.isEmpty()) {
                throw new Exception("摘要提炼失败或被取消");
            }
            ch.setSummary(summary);

            saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
            stateService.saveProgress(progressPath, state);

            if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
                foreshadowService.syncForeshadowsAfterChapter(ctx, cfg, apiCfg, state, chapterIdx, progressPath);
                stateService.saveProgress(progressPath, state);
            }

            logger.successKey("log.chapter_specific_done", ch.getNum());
            logger.taskEnd("chapter_revision", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Specific chapter revision failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.chapter_revise_cancelled");
            } else {
                logger.errorKey("log.chapter_revise_failed", e.getMessage());
            }
            logger.taskEnd("chapter_revision", false);
        }
    }

    /**
     * Go: reviseChapterContentStream - revise content with streaming.
     */
    private String reviseChapterContentStream(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                               Config cfg, Progress state, int chapterIdx,
                                               String userFeedback, ProjectSettings settings) throws Exception {
        ChapterState ch = state.getChapters().get(chapterIdx);
        String lang = cfg.getLanguage();

        String historySummary = ForeshadowService.buildHistorySummary(state, chapterIdx, lang);
        String characterContext = buildCharacterContextForLang(settings, ch.getOutline(), lang);
        String worldviewContext = buildWorldviewContextForLang(settings, ch.getOutline(), lang);

        Map<String, String> revisionVars = new HashMap<>();
        revisionVars.put("ChapterNum", String.valueOf(ch.getNum()));
        revisionVars.put("ChapterTitle", ch.getTitle());
        revisionVars.put("CorePrompt", state.getCorePrompt() != null ? state.getCorePrompt() : "");
        revisionVars.put("HistorySummary", historySummary);
        revisionVars.put("WritingStyle", cfg.getStory().getWritingStyle());
        revisionVars.put("WritingPOV", cfg.getStory().getWritingPov());
        revisionVars.put("CharacterContext", characterContext);
        revisionVars.put("WorldviewContext", worldviewContext);
        revisionVars.put("OriginalContent", ch.getContent() != null ? ch.getContent() : "");
        revisionVars.put("UserFeedback", userFeedback);

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getChapterRevision(), revisionVars);

        String systemPrompt = state.getCorePrompt();
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = LocaleHelper.systemPromptFor(lang, "author_default");
        }
        systemPrompt += LocaleHelper.systemPromptFor(lang, "chapter_revision_suffix");

        logger.streamStart(chapterIdx);

        String content = openAIClient.callAPIStream(ctx, apiCfg, systemPrompt, userPrompt,
                chunk -> logger.contentChunk(chapterIdx, chunk), null);

        return stripChapterMetaProse(content, lang);
    }

    /**
     * Go: reviseSubsequentOutlines - revise outlines of chapters after currentIdx.
     */
    private void reviseSubsequentOutlines(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                           Config cfg, Progress state, int currentIdx, String userFeedback) {
        try {
            String lang = cfg.getLanguage();
            boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

            String subsequentChapters = "";
            for (int i = currentIdx + 1; i < state.getChapters().size(); i++) {
                ChapterState ch = state.getChapters().get(i);
                if (!"accepted".equals(ch.getStatus())) {
                    subsequentChapters += ForeshadowService.formatChapterLine(ch, lang);
                }
            }
            if (subsequentChapters.isEmpty()) return;

            String lockedChapters = "";
            for (int i = 0; i <= currentIdx; i++) {
                ChapterState ch = state.getChapters().get(i);
                if (en) {
                    lockedChapters += String.format("Chapter %d \"%s\" (summary): %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                } else {
                    lockedChapters += String.format("第%d章《%s》（摘要）: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                }
            }

            String feedbackWrap;
            if (en) {
                feedbackWrap = String.format("The user gave revision feedback on chapter %d: %s\nOnly adjust later chapter outlines if this feedback affects downstream plot.",
                        state.getChapters().get(currentIdx).getNum(), userFeedback);
            } else {
                feedbackWrap = String.format("用户对第%d章提出了修改意见：%s\n请仅在该意见影响后续剧情时调整后续章节大纲。",
                        state.getChapters().get(currentIdx).getNum(), userFeedback);
            }

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getOutlineRevision(), Map.of(
                    "CurrentOutline", subsequentChapters,
                    "UserFeedback", feedbackWrap,
                    "LockedChapters", lockedChapters
            ));

            String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_locked_json");
            String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (rawResp == null || rawResp.isEmpty()) return;
            rawResp = OpenAIClient.cleanJSONFences(rawResp);

            OutlineResponse resp = openAIClient.getObjectMapper().readValue(rawResp, OutlineResponse.class);
            OutlineService.applyOutlineRevision(resp, state);
        } catch (Exception e) {
            logger.warnKey("log.subsequent_outline_failed", e.getMessage());
        }
    }

    /**
     * Go: ConfirmChapterAction - confirm the current chapter.
     */
    public ResponseEntity<?> confirmChapter() {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            confirmChapterAction(state);
            stateService.saveProgress(progressPath, state);

            int confirmedIdx = state.getCurrentChapterIndex() - 1;
            ChapterState ch = state.getChapters().get(confirmedIdx);
            logger.successKey("log.chapter_confirmed", ch.getNum());
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Confirm chapter failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void confirmChapterAction(Progress state) throws Exception {
        if (!"writing".equals(state.getPhase())) {
            throw new Exception("当前不在写作阶段");
        }
        int chapterIdx = state.getCurrentChapterIndex();
        if (chapterIdx >= state.getChapters().size()) {
            throw new Exception("章节索引越界");
        }
        ChapterState ch = state.getChapters().get(chapterIdx);
        if (!"review".equals(ch.getStatus())) {
            throw new Exception("当前章节不在审核状态，无法确认");
        }
        ch.setStatus("accepted");
        state.setCurrentChapterIndex(chapterIdx + 1);
    }

    /**
     * Go: PostChapterConflictResolve handler.
     */
    public ResponseEntity<?> resolveConflict(Map<String, String> body) {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            if (state.getPendingWritingConflict() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "writing_conflict_none"));
            }

            String action = body.get("action");
            if (action == null || action.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_action"));
            }

            WritingConflict conflict = state.getPendingWritingConflict();
            int idx = conflict.getChapterIndex();
            if (idx < 0 || idx >= state.getChapters().size()) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_conflict_chapter_idx"));
            }
            ChapterState ch = state.getChapters().get(idx);

            switch (action) {
                case "force_review":
                    ch.setStatus("review");
                    state.setPendingWritingConflict(null);
                    stateService.saveProgress(progressPath, state);
                    logger.successKey("log.chapter_kept_review", ch.getNum());
                    broadcastProgress();
                    return ResponseEntity.ok(state);
                case "dismiss":
                    state.setPendingWritingConflict(null);
                    stateService.saveProgress(progressPath, state);
                    broadcastProgress();
                    return ResponseEntity.ok(state);
                case "retry":
                    state.setPendingWritingConflict(null);
                    stateService.saveProgress(progressPath, state);
                    broadcastProgress();
                    return ResponseEntity.accepted().body(Map.of("status", "retry"));
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "unsupported_action", "action", action));
            }
        } catch (Exception e) {
            log.error("Resolve conflict failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "resolve_conflict_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Delegate foreshadow outline check to ForeshadowConsistencyService.
     */
    public void runForeshadowOutlineCheck(OpenAIClient.CancellationToken ctx) {
        logger.taskStart("foreshadow_outline_check");
        foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(ctx);
        logger.taskEnd("foreshadow_outline_check", true);
        broadcastProgress();
    }

    /**
     * Go: PostChapterEdit handler - delegates to EditingService.
     */
    public ResponseEntity<?> editChapterContent(EditChapterContentRequest req) {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            int totalLines = editingService.editChapterContent(state, req);
            stateService.saveProgress(progressPath, state);

            // Save markdown
            ChapterState ch = projectService.getChapterByNum(req.getChapterNum());
            saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
            broadcastProgress();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total_lines", totalLines);
            result.put("chapter", ch);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Edit chapter content failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", "chapter_edit_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: PolishChapterAction - polish a chapter using enabled skills.
     */
    public ResponseEntity<?> polishChapter(Map<String, Integer> body, TaskManager tm) {
        try {
            Progress state = projectService.getProgress();
            Config cfg = projectService.getConfig();
            List<Skill> skills = projectService.getSkills();
            String progressPath = projectService.getProgressPath();

            int chapterIdx = -1;
            if (body != null && body.containsKey("num")) {
                int num = body.get("num");
                if (state.getChapters() != null) {
                    for (int i = 0; i < state.getChapters().size(); i++) {
                        if (state.getChapters().get(i).getNum() == num) {
                            chapterIdx = i;
                            break;
                        }
                    }
                }
            }
            if (chapterIdx == -1) {
                chapterIdx = state.getCurrentChapterIndex();
            }

            if (chapterIdx < 0 || chapterIdx >= state.getChapters().size()) {
                return ResponseEntity.badRequest().body(Map.of("error", "chapter_index_out_of_bounds"));
            }

            ChapterState ch = state.getChapters().get(chapterIdx);
            if (ch.getContent() == null || ch.getContent().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "chapter_content_empty"));
            }

            if (!tm.tryStartTask()) {
                return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
            }

            final int fIdx = chapterIdx;
            CompletableFuture.runAsync(() -> {
                try {
                    polishChapterAction(tm.getTaskContext(), state, fIdx, skills, cfg, progressPath);
                } finally {
                    tm.endTask();
                }
            });

            return ResponseEntity.accepted().body(Map.of("status", "started"));
        } catch (Exception e) {
            log.error("Polish chapter failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "polish_failed", "detail", e.getMessage()));
        }
    }

    private void polishChapterAction(OpenAIClient.CancellationToken ctx, Progress state, int chapterIdx,
                                      List<Skill> skills, Config cfg, String progressPath) {
        try {
            logger.taskStart("chapter_polish");
            APIConfig apiCfg = projectService.getAPIConfig();

            ChapterState ch = state.getChapters().get(chapterIdx);
            String skillsContent = skillService.formatSkillsContent(skills);
            if (skillsContent == null || skillsContent.isEmpty()) {
                throw new Exception("没有启用的润色技能");
            }

            String lang = cfg.getLanguage();
            boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

            String userPrompt;
            if (en) {
                userPrompt = String.format("Polish the chapter below according to the rules. Output the full revised chapter prose.\n\n## Polish rules\n\n%s\n\n## Chapter to polish\n\n%s",
                        skillsContent, ch.getContent());
            } else {
                userPrompt = String.format("请根据以下规则对下面的章节正文进行去AI味处理，输出修改后的完整正文。\n\n## 润色规则\n\n%s\n\n## 待处理正文\n\n%s",
                        skillsContent, ch.getContent());
            }

            String systemPrompt = LocaleHelper.systemPromptFor(lang, "polish_editor");

            logger.streamStart(chapterIdx);
            String result = openAIClient.callAPIStream(ctx, apiCfg, systemPrompt, userPrompt,
                    chunk -> logger.contentChunk(chapterIdx, chunk), null);

            ch.setContent(stripChapterMetaProse(result, lang));
            ch.setStatus("review");

            saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
            stateService.saveProgress(progressPath, state);

            logger.taskEnd("chapter_polish", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Polish failed", e);
            logger.errorKey("log.polish_failed", e.getMessage());
            logger.taskEnd("chapter_polish", false);
        }
    }

    /**
     * Go: SmoothTransitionsAction - batch smooth transitions between confirmed chapters.
     */
    public void smoothTransitions(OpenAIClient.CancellationToken ctx) {
        try {
            logger.taskStart("smooth_transitions");
            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            openAIClient.validateAPIConfig(apiCfg);

            List<Integer> targets = new ArrayList<>();
            for (int i = 1; i < state.getChapters().size(); i++) {
                ChapterState curr = state.getChapters().get(i);
                ChapterState prev = state.getChapters().get(i - 1);
                if ("accepted".equals(curr.getStatus()) && curr.getContent() != null && !curr.getContent().isEmpty()
                        && "accepted".equals(prev.getStatus()) && prev.getContent() != null && !prev.getContent().isEmpty()) {
                    targets.add(i);
                }
            }
            if (targets.isEmpty()) {
                throw new Exception("没有可优化的章节");
            }

            logger.infoKey("log.smooth_start", targets.size());
            int optimized = 0;
            for (int n = 0; n < targets.size(); n++) {
                if (ctx != null && ctx.isCancelled()) {
                    throw new Exception("任务已取消");
                }
                int idx = targets.get(n);
                ChapterState ch = state.getChapters().get(idx);
                logger.stepInfo(n + 1, targets.size(), String.format("正在检查第 %d 章《%s》的衔接...", ch.getNum(), ch.getTitle()));

                String prevTail = tailAtParagraph(state.getChapters().get(idx - 1).getContent(), PREV_TAIL_MAX_RUNES);
                String[] openingRest = splitChapterOpening(ch.getContent(), OPENING_MAX_RUNES);
                String opening = openingRest[0];
                String rest = openingRest[1];

                String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getTransitionSmoothing(), Map.of(
                        "ChapterNum", String.valueOf(ch.getNum()),
                        "ChapterTitle", ch.getTitle(),
                        "ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "",
                        "PrevTail", prevTail,
                        "Opening", opening
                ));
                String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "transition_editor");

                String resp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
                if (resp == null || resp.isEmpty()) {
                    throw new Exception("第 " + ch.getNum() + " 章衔接检查调用失败或被取消");
                }
                String revised = resp.trim();

                String head = revised.length() > 30 ? revised.substring(0, 30) : revised;
                if (revised.isEmpty() || head.contains("NO_CHANGE")) {
                    logger.infoKey("log.smooth_natural", ch.getNum());
                    continue;
                }

                if (rest.isEmpty()) {
                    ch.setContent(revised);
                } else {
                    ch.setContent(revised + "\n\n" + rest.replaceFirst("^\\n+", ""));
                }
                saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
                stateService.saveProgress(progressPath, state);
                optimized++;
                logger.infoKey("log.smooth_optimized", ch.getNum());
            }

            logger.successKey("log.smooth_done", targets.size(), optimized);
            logger.taskEnd("smooth_transitions", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Smooth transitions failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.smooth_transitions_cancelled");
            } else {
                logger.errorKey("log.smooth_transitions_failed", e.getMessage());
            }
            logger.taskEnd("smooth_transitions", false);
            broadcastProgress();
        }
    }

    /**
     * Go: DeleteChapter handler - delete the last chapter (reset to pending).
     */
    public ResponseEntity<?> deleteLastChapter() {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            if (state.getChapters() == null || state.getChapters().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "no_chapters_to_delete"));
            }

            int lastIdx = state.getChapters().size() - 1;
            ChapterState ch = state.getChapters().get(lastIdx);

            if ("writing".equals(ch.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "writing_chapter_cannot_delete"));
            }

            // Delete markdown file
            try {
                Files.deleteIfExists(Paths.get(projectService.chapterMarkdownPath(projectService.getProjectDir(), ch.getNum())));
            } catch (Exception ignored) {}

            ch.setContent("");
            ch.setSummary("");
            ch.setStatus("pending");

            if (state.getCurrentChapterIndex() > lastIdx) {
                state.setCurrentChapterIndex(lastIdx);
            }

            stateService.saveProgress(progressPath, state);
            logger.successKey("log.chapter_deleted", ch.getNum());
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Delete chapter failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_chapter_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: DeleteChaptersFrom handler - delete all chapters from num onward.
     */
    public ResponseEntity<?> deleteChaptersFrom(int num) {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            int startIdx = -1;
            if (state.getChapters() != null) {
                for (int i = 0; i < state.getChapters().size(); i++) {
                    if (state.getChapters().get(i).getNum() == num) {
                        startIdx = i;
                        break;
                    }
                }
            }
            if (startIdx == -1) {
                return ResponseEntity.status(404).body(Map.of("error", "chapter_n_not_found", "num", num));
            }

            for (int i = startIdx; i < state.getChapters().size(); i++) {
                if ("writing".equals(state.getChapters().get(i).getStatus())) {
                    return ResponseEntity.status(409).body(Map.of("error", "writing_range_has_writing"));
                }
            }

            int deletedCount = state.getChapters().size() - startIdx;

            for (int i = startIdx; i < state.getChapters().size(); i++) {
                ChapterState ch = state.getChapters().get(i);
                try {
                    Files.deleteIfExists(Paths.get(projectService.chapterMarkdownPath(projectService.getProjectDir(), ch.getNum())));
                } catch (Exception ignored) {}
                ch.setContent("");
                ch.setSummary("");
                ch.setStatus("pending");
            }

            if (state.getCurrentChapterIndex() >= startIdx) {
                state.setCurrentChapterIndex(startIdx);
            }

            stateService.saveProgress(progressPath, state);
            logger.successKey("log.chapters_deleted_from", num, deletedCount);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Delete chapters from failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_chapters_failed", "detail", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private String stripChapterMetaProse(String content, String lang) {
        if (content == null) return "";
        content = content.trim();
        if (content.isEmpty()) return content;
        String[] lines = content.split("\n");
        int start = 0;
        while (start < lines.length && isChapterMetaLine(lines[start].trim(), lang)) {
            start++;
        }
        int end = lines.length - 1;
        while (end >= start && isChapterMetaLine(lines[end].trim(), lang)) {
            end--;
        }
        if (start > end) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    private boolean isChapterMetaLine(String line, String lang) {
        if (line == null || line.isEmpty()) return false;
        String[] exact = {
                "本章完", "本章终", "待续", "未完待续", "（完）", "(完)", "完", "——", "—", "***", "---",
                "End of chapter", "To be continued", "The End"
        };
        for (String s : exact) {
            if (line.equals(s) || line.startsWith(s + ".") || line.startsWith(s + "。")) return true;
        }
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
        if (en) {
            if (CHAPTER_META_START_EN.matcher(line).matches()) return true;
            return false;
        }
        if (CHAPTER_META_START_ZH.matcher(line).matches()) return true;
        return false;
    }

    private String tailAtParagraph(String content, int maxRunes) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= maxRunes) return trimmed;
        String tail = trimmed.substring(trimmed.length() - maxRunes);
        int nlIdx = tail.indexOf('\n');
        if (nlIdx >= 0 && nlIdx + 1 < tail.length()) {
            tail = tail.substring(nlIdx + 1);
        }
        return tail.trim();
    }

    private String[] splitChapterOpening(String content, int maxRunes) {
        if (content == null) return new String[]{"", ""};
        if (content.length() <= maxRunes) return new String[]{content, ""};
        int cut = maxRunes;
        for (int i = maxRunes; i > 0; i--) {
            if (content.charAt(i - 1) == '\n') {
                cut = i;
                break;
            }
        }
        return new String[]{content.substring(0, cut), content.substring(cut)};
    }

    private String buildOutlineConstraintsForLang(Progress state, int idx, String lang) {
        if (state.getChapters() == null) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
        StringBuilder sb = new StringBuilder();
        int end = Math.min(idx + 1 + FUTURE_OUTLINE_WINDOW, state.getChapters().size());
        for (int j = idx + 1; j < end; j++) {
            ChapterState future = state.getChapters().get(j);
            if (en) {
                sb.append(String.format("Chapter %d \"%s\": %s\n", future.getNum(), future.getTitle(), future.getOutline()));
            } else {
                sb.append(String.format("第%d章《%s》: %s\n", future.getNum(), future.getTitle(), future.getOutline()));
            }
        }
        return sb.toString();
    }

    private String buildPreviousChapterTailForLang(Progress state, int idx, String lang) {
        if (idx <= 0) return "";
        ChapterState prev = state.getChapters().get(idx - 1);
        if (prev.getContent() == null || prev.getContent().isEmpty()) return "";
        String tail = tailAtParagraph(prev.getContent(), PREV_TAIL_MAX_RUNES);
        if (tail.isEmpty()) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
        if (en) {
            return "[Previous chapter ending]\n" + tail;
        }
        return "【上一章结尾原文】\n" + tail;
    }

    private String buildCharacterContextForLang(ProjectSettings settings, String outline, String lang) {
        if (settings == null) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
        return en ? settingsService.buildCharacterContext(settings, outline)
                : settingsService.buildCharacterContext(settings, outline);
    }

    private String buildWorldviewContextForLang(ProjectSettings settings, String outline, String lang) {
        if (settings == null) return "";
        return settingsService.buildWorldviewContext(settings, outline);
    }

    private String preferUserValue(String userVal, String fallback) {
        if (userVal != null && !userVal.isEmpty()) return userVal;
        return fallback != null ? fallback : "";
    }

    private void saveChapterMarkdown(String projectDir, ChapterState ch, String storyTitle) {
        try {
            String path = projectService.chapterMarkdownPath(projectDir, ch.getNum());
            String content = ch.getContent() != null ? ch.getContent() : "";
            Files.writeString(Paths.get(path), content);
        } catch (Exception e) {
            log.warn("Failed to save chapter markdown", e);
        }
    }

    private void broadcastProgress() {
        Progress state = projectService.getProgress();
        int accepted = 0;
        int total = 0;
        if (state.getChapters() != null) {
            total = state.getChapters().size();
            for (ChapterState ch : state.getChapters()) {
                if ("accepted".equals(ch.getStatus())) accepted++;
            }
        }
        double pct = total > 0 ? (double) accepted / total * 100 : 0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", state.getPhase());
        data.put("title", state.getTitle());
        data.put("current_chapter", state.getCurrentChapterIndex());
        data.put("total_chapters", total);
        data.put("accepted_chapters", accepted);
        data.put("percent", pct);
        data.put("is_task_running", taskManager.isTaskRunning());
        logger.progressUpdate(data);
    }

    private static class CompletableFuture {
        static void runAsync(Runnable task) {
            java.util.concurrent.CompletableFuture.runAsync(task);
        }
    }
}
