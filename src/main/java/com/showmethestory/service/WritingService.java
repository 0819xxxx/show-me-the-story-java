package com.showmethestory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.I18nContextBuilder;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.i18n.PromptRenderer;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Core writing pipeline service.
 * Ported from Go writing.go — the most important service in the application.
 * <p>
 * Includes the 5-step chapter generation pipeline (outline consistency check,
 * content generation with streaming, summary extraction, fact-checking with
 * retry, foreshadow sync), chapter revision, confirmation, polishing, and
 * transition smoothing.
 */
@Service
public class WritingService {

    private static final Logger log = LoggerFactory.getLogger(WritingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Constants
    private static final int MAX_FACT_CHECK_RETRIES = 3;
    private static final int PREV_TAIL_MAX_RUNES = 800;
    private static final int OPENING_MAX_RUNES = 1000;

    // Regex patterns for chapter meta stripping
    private static final Pattern CHAPTER_META_START_ZH = Pattern.compile("^[（(]?第\\s*\\d+\\s*章");
    private static final Pattern CHAPTER_META_START_EN = Pattern.compile("(?i)^(?:chapter\\s+\\d+|part\\s+\\d+)");
    private static final Pattern CHAPTER_META_PREAMBLE_ZH = Pattern.compile(
            "(?i)^(?:以下是|下面是|以上是|这是)?(?:以下为|以下是)?(?:修订后|修改后|润色后|重写后)?(?:的)?(?:完整)?(?:全文)?(?:第\\s*\\d+\\s*章)?(?:正文|全文|完整正文|修订后正文)?[：:。.]?\\s*$");
    private static final Pattern CHAPTER_META_PREAMBLE_EN = Pattern.compile(
            "(?i)^(?:here\\s+(?:is|are)|below\\s+is|the\\s+following\\s+is|revised|full)\\b.*(?:chapter|text|prose|content|version)");

    // Dependencies
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final StateService stateService;
    private final ProjectService projectService;
    private final ForeshadowService foreshadowService;
    private final ForeshadowConsistencyService foreshadowConsistencyService;
    private final WritingConflictService writingConflictService;
    private final OutlineService outlineService;
    private final SettingsService settingsService;
    private final SkillService skillService;

    public WritingService(OpenAIClient openAIClient,
                          LogBroadcaster logger,
                          StateService stateService,
                          ProjectService projectService,
                          ForeshadowService foreshadowService,
                          ForeshadowConsistencyService foreshadowConsistencyService,
                          WritingConflictService writingConflictService,
                          OutlineService outlineService,
                          SettingsService settingsService,
                          SkillService skillService) {
        this.openAIClient = openAIClient;
        this.logger = logger;
        this.stateService = stateService;
        this.projectService = projectService;
        this.foreshadowService = foreshadowService;
        this.foreshadowConsistencyService = foreshadowConsistencyService;
        this.writingConflictService = writingConflictService;
        this.outlineService = outlineService;
        this.settingsService = settingsService;
        this.skillService = skillService;
    }

    // ===============================================================
    // 1. generateChapterAction — The 5-step chapter writing pipeline
    // ===============================================================

    /**
     * Go: GenerateChapterAction
     * The 5-step chapter writing pipeline:
     *   Step 1: Check outline consistency (if not first chapter)
     *   Step 2: Generate chapter content (streaming with retry)
     *   Step 3: Generate chapter summary
     *   Step 4: Fact-check the chapter (up to 3 retries)
     *   Step 5: Update foreshadow status
     * Handles writing conflicts when fact-check fails repeatedly.
     */
    public void generateChapterAction(OpenAIClient.CancellationToken ctx) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();
        ProjectSettings settings = projectService.getSettings();

        OpenAIClient.validateAPIConfig(apiCfg);

        if (!"writing".equals(state.getPhase())) {
            throw new IllegalStateException("当前不在写作阶段");
        }

        if (state.getCurrentChapterIndex() >= state.getChapters().size()) {
            throw new IllegalStateException("所有章节已完成");
        }

        int i = state.getCurrentChapterIndex();
        ChapterState ch = state.getChapters().get(i);

        if (ChapterState.STATUS_ACCEPTED.equals(ch.getStatus())) {
            throw new IllegalStateException(
                    String.format("第 %d 章已确认，请确认当前章节或重置进度", ch.getNum()));
        }

        ch.setStatus(ChapterState.STATUS_WRITING);
        stateService.saveProgress(progressPath, state);

        logger.infoKey("log.chapter_start", ch.getNum(), ch.getTitle());

        // Step 1: Pre-write outline consistency check (skip for first chapter)
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

        // Run foreshadow-outline check if foreshadows exist
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(ctx);
        }

        // Steps 2-4: Content generation, summary, fact-check with retry loop
        String extraConstraints = "";
        List<String> accumulatedIssues = new ArrayList<>();

        for (int attempt = 0; attempt <= MAX_FACT_CHECK_RETRIES; attempt++) {
            if (ctx != null && ctx.isCancelled()) {
                throw new InterruptedException("任务已取消");
            }

            // Step 2: Generate chapter content (streaming)
            logger.stepInfo(2, 5, "正在构思并撰写正文...");
            String content = generateChapterContentStreamWithRetryLog(
                    ctx, apiCfg, cfg, state, i, settings, extraConstraints);
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("正文生成失败或被取消");
            }
            ch.setContent(content);
            logger.infoKey("log.prose_done", countRunes(content));

            // Step 3: Generate chapter summary
            logger.stepInfo(3, 5, "正在提炼本章摘要...");
            String summary = generateChapterSummaryWithRetryLog(ctx, apiCfg, cfg, content);
            if (summary == null || summary.isEmpty()) {
                throw new RuntimeException("摘要提炼失败或被取消");
            }
            ch.setSummary(summary);
            logger.infoKey("log.summary_done");

            // Step 4: Fact-check the chapter
            logger.stepInfo(4, 5, "正在对本章进行事实核查...");
            String historySummary = I18nContextBuilder.buildHistorySummary(state, i, cfg.getLanguage());
            String factCheckResult = generateChapterFactCheckWithRetryLog(
                    ctx, apiCfg, cfg, state, i, content, historySummary);

            FactCheckResult parsed = parseFactCheckResult(factCheckResult);
            if (parsed.failed) {
                accumulatedIssues = mergeUniqueIssues(
                        accumulatedIssues, splitFactCheckIssues(parsed.issues));

                if (attempt < MAX_FACT_CHECK_RETRIES) {
                    logger.warnKey("log.factcheck_retry", ch.getNum(), attempt + 1);
                    logger.warnKey("log.factcheck_details", parsed.issues);
                    continue;
                }

                // Max retries exhausted — analyze the conflict
                logger.warnKey("log.factcheck_max_retries");
                WritingConflictAnalysis analysis;
                try {
                    analysis = writingConflictService.analyzeWritingConflict(
                            ctx, state, i, content, accumulatedIssues);
                } catch (Exception e) {
                    logger.warnKey("log.conflict_analyze_failed", e.getMessage());
                    break;
                }

                if (analysis.isReconcilable()
                        && analysis.getExtraConstraints() != null
                        && !analysis.getExtraConstraints().trim().isEmpty()) {
                    // Retry once with extra constraints from conflict analysis
                    logger.infoKey("log.conflict_retry");
                    extraConstraints = analysis.getExtraConstraints().trim();
                    content = generateChapterContentStreamWithRetryLog(
                            ctx, apiCfg, cfg, state, i, settings, extraConstraints);
                    if (content == null || content.isEmpty()) {
                        throw new RuntimeException("正文生成失败或被取消");
                    }
                    ch.setContent(content);

                    summary = generateChapterSummaryWithRetryLog(ctx, apiCfg, cfg, content);
                    if (summary == null || summary.isEmpty()) {
                        throw new RuntimeException("摘要提炼失败或被取消");
                    }
                    ch.setSummary(summary);

                    factCheckResult = generateChapterFactCheckWithRetryLog(
                            ctx, apiCfg, cfg, state, i, content, historySummary);
                    parsed = parseFactCheckResult(factCheckResult);
                    if (parsed.failed) {
                        accumulatedIssues = mergeUniqueIssues(
                                accumulatedIssues, splitFactCheckIssues(parsed.issues));
                    } else {
                        logger.infoKey("log.factcheck_constraint_pass");
                        break;
                    }
                }

                // Build and store the writing conflict
                WritingConflict conflict = writingConflictService.buildWritingConflict(
                        state, i, accumulatedIssues, analysis);
                String lang = cfg.getLanguage();
                conflict.setSuggestedActions(
                        writingConflictService.ensureConflictActions(conflict.getSuggestedActions(), lang));
                state.setPendingWritingConflict(conflict);
                stateService.saveProgress(progressPath, state);
                logger.writingConflict(conflict);
                throw new WritingConflictException(conflict);
            }

            // Fact-check passed
            logger.infoKey("log.factcheck_pass");
            break;
        }

        // Clear any pending conflict on success
        state.setPendingWritingConflict(null);

        // Step 5: Update foreshadow status
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            logger.stepInfo(5, 5, "正在更新伏笔状态...");
            foreshadowService.syncForeshadowsAfterChapter(ctx, cfg, apiCfg, state, i, progressPath);
        }

        // Save chapter markdown
        saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());

        // Transition to review state
        ch.setStatus(ChapterState.STATUS_REVIEW);
        state.setCurrentChapterIndex(i);
        stateService.saveProgress(progressPath, state);

        logger.successKey("log.chapter_write_complete", ch.getNum());
    }

    // ===============================================================
    // 2. reviseChapterAction — Revise the current chapter
    // ===============================================================

    /**
     * Go: ReviseChapterAction
     * Revise the "current chapter" (in review/writing state) based on user feedback.
     * Uses minimal-revision prompts and optionally syncs subsequent pending outlines.
     */
    public void reviseChapterAction(OpenAIClient.CancellationToken ctx, String feedback) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();
        ProjectSettings settings = projectService.getSettings();

        OpenAIClient.validateAPIConfig(apiCfg);

        if (!"writing".equals(state.getPhase())) {
            throw new IllegalStateException("当前不在写作阶段");
        }

        int chapterIdx = state.getCurrentChapterIndex();
        if (chapterIdx >= state.getChapters().size()) {
            throw new IndexOutOfBoundsException("章节索引越界");
        }

        ChapterState ch = state.getChapters().get(chapterIdx);
        if (!ChapterState.STATUS_REVIEW.equals(ch.getStatus())
                && !ChapterState.STATUS_WRITING.equals(ch.getStatus())) {
            throw new IllegalStateException("当前章节不在审核/写作状态");
        }

        logger.infoKey("log.chapter_modifying", ch.getNum(), ch.getTitle());

        // Step 1: Revise content based on feedback
        logger.stepInfo(1, 3, "正在根据意见修订正文...");
        String revisedContent = reviseChapterContentStream(
                ctx, apiCfg, cfg, state, chapterIdx, feedback, settings);
        ch.setContent(revisedContent);
        logger.infoKey("log.prose_revised", countRunes(revisedContent));

        // Step 2: Regenerate summary
        logger.stepInfo(2, 3, "重新提炼摘要...");
        String summary = generateChapterSummaryWithRetryLog(ctx, apiCfg, cfg, ch.getContent());
        if (summary == null || summary.isEmpty()) {
            throw new RuntimeException("摘要提炼失败或被取消");
        }
        ch.setSummary(summary);
        logger.infoKey("log.summary_done");

        saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());

        // Step 3: Revise subsequent chapter outlines if needed
        if (chapterIdx + 1 < state.getChapters().size()) {
            logger.stepInfo(3, 3, "正在修订后续章节大纲...");
            try {
                reviseSubsequentOutlines(ctx, apiCfg, cfg, state, chapterIdx, feedback);
                logger.infoKey("log.subsequent_outline_done");
            } catch (Exception e) {
                logger.warnKey("log.subsequent_outline_failed", e.getMessage());
            }
        }

        ch.setStatus(ChapterState.STATUS_REVIEW);
        stateService.saveProgress(progressPath, state);

        // Sync foreshadows if present
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            foreshadowService.syncForeshadowsAfterChapter(
                    ctx, cfg, apiCfg, state, chapterIdx, progressPath);
            stateService.saveProgress(progressPath, state);
        }

        logger.successKey("log.chapter_revised");
    }

    // ===============================================================
    // 3. reviseSpecificChapterAction — Revise a specific chapter by number
    // ===============================================================

    /**
     * Go: ReviseSpecificChapterAction
     * Perform minimal revision on a specific chapter (including accepted ones).
     * Only modifies that chapter's content and summary — never touches other
     * chapters, outlines, or the progress pointer.
     */
    public void reviseSpecificChapterAction(OpenAIClient.CancellationToken ctx,
                                             int chapterNum, String feedback) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();
        ProjectSettings settings = projectService.getSettings();

        OpenAIClient.validateAPIConfig(apiCfg);

        if (feedback == null || feedback.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少修改意见");
        }

        int chapterIdx = -1;
        for (int i = 0; i < state.getChapters().size(); i++) {
            if (state.getChapters().get(i).getNum() == chapterNum) {
                chapterIdx = i;
                break;
            }
        }
        if (chapterIdx == -1) {
            throw new IllegalArgumentException(
                    String.format("第 %d 章不存在", chapterNum));
        }

        ChapterState ch = state.getChapters().get(chapterIdx);
        if (ch.getContent() == null || ch.getContent().isEmpty()) {
            throw new IllegalStateException(
                    String.format("第 %d 章尚未生成内容，无法修订（请先生成该章）", chapterNum));
        }
        if (ChapterState.STATUS_WRITING.equals(ch.getStatus())) {
            throw new IllegalStateException(
                    String.format("第 %d 章正在写作中，无法修订", chapterNum));
        }

        logger.infoKey("log.chapter_specific_revising_long", ch.getNum(), ch.getTitle());

        // Step 1: Revise content
        logger.stepInfo(1, 2, "正在根据意见修订正文...");
        String revisedContent = reviseChapterContentStream(
                ctx, apiCfg, cfg, state, chapterIdx, feedback, settings);
        ch.setContent(revisedContent);
        logger.infoKey("log.prose_specific_revised", countRunes(revisedContent));

        // Step 2: Regenerate summary
        logger.stepInfo(2, 2, "重新提炼摘要...");
        String summary = generateChapterSummaryWithRetryLog(ctx, apiCfg, cfg, ch.getContent());
        if (summary == null || summary.isEmpty()) {
            throw new RuntimeException("摘要提炼失败或被取消");
        }
        ch.setSummary(summary);

        saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
        stateService.saveProgress(progressPath, state);

        // Sync foreshadows if present
        if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
            foreshadowService.syncForeshadowsAfterChapter(
                    ctx, cfg, apiCfg, state, chapterIdx, progressPath);
            stateService.saveProgress(progressPath, state);
        }

        logger.successKey("log.chapter_specific_done", ch.getNum());
    }

    // ===============================================================
    // 4. confirmChapterAction — Accept a chapter, advance to next
    // ===============================================================

    /**
     * Go: ConfirmChapterAction
     * Accept the current chapter and advance the chapter index.
     */
    public void confirmChapterAction() throws Exception {
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();

        if (!"writing".equals(state.getPhase())) {
            throw new IllegalStateException("当前不在写作阶段");
        }

        int chapterIdx = state.getCurrentChapterIndex();
        if (chapterIdx >= state.getChapters().size()) {
            throw new IndexOutOfBoundsException("章节索引越界");
        }

        ChapterState ch = state.getChapters().get(chapterIdx);
        if (!ChapterState.STATUS_REVIEW.equals(ch.getStatus())) {
            throw new IllegalStateException("当前章节不在审核状态，无法确认");
        }

        ch.setStatus(ChapterState.STATUS_ACCEPTED);
        state.setCurrentChapterIndex(chapterIdx + 1);
        stateService.saveProgress(progressPath, state);
    }

    // ===============================================================
    // 5. polishChapterAction — Polish/improve a chapter
    // ===============================================================

    /**
     * Go: PolishChapterAction
     * Polish/improve a chapter using enabled skill rules.
     */
    public void polishChapterAction(OpenAIClient.CancellationToken ctx,
                                     int chapterIdx) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();
        List<Skill> skills = projectService.getSkills();

        if (chapterIdx < 0 || chapterIdx >= state.getChapters().size()) {
            throw new IndexOutOfBoundsException("章节索引越界");
        }

        ChapterState ch = state.getChapters().get(chapterIdx);
        if (ch.getContent() == null || ch.getContent().isEmpty()) {
            throw new IllegalStateException("章节内容为空，无法润色");
        }

        String skillsContent = skillService.formatSkillsContent(skills);
        if (skillsContent == null || skillsContent.isEmpty()) {
            throw new IllegalStateException("没有启用的润色技能，请先在技能管理页启用");
        }

        String lang = cfg.getLanguage();
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        String userPrompt;
        if (en) {
            userPrompt = String.format(
                    "Polish the chapter below according to the rules. Output the full revised chapter prose. Do not add chapter titles, numbers, \"End of chapter\", or any other meta or explanatory text.\n\n## Polish rules\n\n%s\n\n## Chapter to polish\n\n%s",
                    skillsContent, ch.getContent());
        } else {
            userPrompt = String.format(
                    "请根据以下规则对下面的章节正文进行去AI味处理，输出修改后的完整正文。不要添加章节标题、章节号、「本章完」等任何元信息或说明性文字。\n\n## 润色规则\n\n%s\n\n## 待处理正文\n\n%s",
                    skillsContent, ch.getContent());
        }

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "polish_editor");

        logger.streamStart(chapterIdx);
        String result = openAIClient.callAPIStream(ctx, apiCfg, systemPrompt, userPrompt,
                chunk -> logger.contentChunk(chapterIdx, chunk), null);

        ch.setContent(stripChapterMetaProse(result, lang));
        ch.setStatus(ChapterState.STATUS_REVIEW);

        saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
        stateService.saveProgress(progressPath, state);
    }

    // ===============================================================
    // 6. smoothTransitionsAction — Smooth transitions between chapters
    // ===============================================================

    /**
     * Go: SmoothTransitionsAction
     * Batch-optimize transitions between confirmed chapters:
     * For each chapter, feed the previous chapter's tail and this chapter's
     * opening to the AI; only rewrite the opening if the transition is awkward.
     * Each chapter is saved immediately after processing so the task can be
     * cancelled at any time without losing completed work.
     */
    public void smoothTransitionsAction(OpenAIClient.CancellationToken ctx) throws Exception {
        APIConfig apiCfg = projectService.getAPIConfig();
        Config cfg = projectService.getConfig();
        Progress state = projectService.getProgress();
        String progressPath = projectService.getProgressPath();

        OpenAIClient.validateAPIConfig(apiCfg);

        // Collect target chapters: adjacent pairs of accepted chapters with content
        List<Integer> targets = new ArrayList<>();
        for (int i = 1; i < state.getChapters().size(); i++) {
            ChapterState curr = state.getChapters().get(i);
            ChapterState prev = state.getChapters().get(i - 1);
            if (ChapterState.STATUS_ACCEPTED.equals(curr.getStatus())
                    && curr.getContent() != null && !curr.getContent().isEmpty()
                    && ChapterState.STATUS_ACCEPTED.equals(prev.getStatus())
                    && prev.getContent() != null && !prev.getContent().isEmpty()) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException("没有可优化的章节（需要至少两个相邻的已确认章节）");
        }

        logger.infoKey("log.smooth_start", targets.size());
        int optimized = 0;

        for (int n = 0; n < targets.size(); n++) {
            if (ctx != null && ctx.isCancelled()) {
                throw new InterruptedException("任务已取消");
            }

            int idx = targets.get(n);
            ChapterState ch = state.getChapters().get(idx);
            logger.stepInfo(n + 1, targets.size(),
                    String.format("正在检查第 %d 章《%s》的衔接...", ch.getNum(), ch.getTitle()));

            String prevTail = tailAtParagraph(
                    state.getChapters().get(idx - 1).getContent(), PREV_TAIL_MAX_RUNES);
            String[] openingRest = splitChapterOpening(ch.getContent(), OPENING_MAX_RUNES);
            String opening = openingRest[0];
            String rest = openingRest[1];

            String userPrompt = PromptRenderer.renderPrompt(
                    cfg.getPrompts().getTransitionSmoothing(), Map.of(
                            "ChapterNum", String.valueOf(ch.getNum()),
                            "ChapterTitle", ch.getTitle(),
                            "ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "",
                            "PrevTail", prevTail,
                            "Opening", opening
                    ));
            String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "transition_editor");

            String resp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (resp == null || resp.isEmpty()) {
                throw new RuntimeException(
                        String.format("第 %d 章衔接检查调用失败或被取消", ch.getNum()));
            }
            String revised = resp.trim();

            // Check if AI says no change needed
            String head = revised;
            if (countRunes(head) > 30) {
                head = substringByRunes(head, 30);
            }
            if (revised.isEmpty() || head.contains("NO_CHANGE")) {
                logger.infoKey("log.smooth_natural", ch.getNum());
                continue;
            }

            // Apply the revised opening
            if (rest == null || rest.isEmpty()) {
                ch.setContent(revised);
            } else {
                ch.setContent(revised + "\n\n" + stripLeading(rest, '\n'));
            }

            saveChapterMarkdown(projectService.getProjectDir(), ch, state.getTitle());
            stateService.saveProgress(progressPath, state);
            optimized++;
            logger.infoKey("log.smooth_optimized", ch.getNum());
        }

        logger.successKey("log.smooth_done", targets.size(), optimized);
    }

    // ===============================================================
    // Helper: generateChapterContentStreamWithRetryLog
    // ===============================================================

    /**
     * Go: generateChapterContentStreamWithRetryLog
     * Generate chapter content with streaming, retrying on transient errors.
     */
    String generateChapterContentStreamWithRetryLog(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int idx, ProjectSettings settings,
            String extraWritingConstraints) {

        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                String content = generateChapterContentStream(
                        ctx, apiCfg, cfg, state, idx, settings, extraWritingConstraints);
                if (content != null && !content.isEmpty()) {
                    return content;
                }
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = OpenAIClient.getWaitTime(retryCount);
                logger.warnKey("log.content_gen_retry", e.getMessage(), retryCount, waitTime);
                if (!sleepOrCancel(ctx, waitTime)) return "";
            }
        }
    }

    // ===============================================================
    // Helper: generateChapterSummaryWithRetryLog
    // ===============================================================

    /**
     * Go: generateChapterSummaryWithRetryLog
     * Generate chapter summary with retry on transient errors.
     */
    String generateChapterSummaryWithRetryLog(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
            Config cfg, String content) {

        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                String summary = generateChapterSummary(ctx, apiCfg, cfg, content);
                if (summary != null && !summary.isEmpty()) {
                    return summary;
                }
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = OpenAIClient.getWaitTime(retryCount);
                logger.warnKey("log.summary_retry", e.getMessage(), retryCount, waitTime);
                if (!sleepOrCancel(ctx, waitTime)) return "";
            }
        }
    }

    // ===============================================================
    // Helper: generateChapterFactCheckWithRetryLog
    // ===============================================================

    /**
     * Go: generateChapterFactCheckWithRetryLog
     * Generate chapter fact-check result with retry on transient errors.
     */
    String generateChapterFactCheckWithRetryLog(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int idx, String content, String historySummary) {

        int retryCount = 0;
        while (true) {
            if (ctx != null && ctx.isCancelled()) return "";
            try {
                String result = generateChapterFactCheck(
                        ctx, apiCfg, cfg, state, idx, content, historySummary);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                if (OpenAIClient.isFatalError(e)) {
                    logger.errorKey("log.fatal_no_retry", e.getMessage());
                    return "";
                }
                retryCount++;
                int waitTime = OpenAIClient.getWaitTime(retryCount);
                logger.warnKey("log.factcheck_api_retry", e.getMessage(), retryCount, waitTime);
                if (!sleepOrCancel(ctx, waitTime)) return "";
            }
        }
    }

    // ===============================================================
    // stripChapterMetaProse — Remove AI-generated meta text from prose
    // ===============================================================

    /**
     * Go: stripChapterMetaProse
     * Trims common AI-emitted chapter framing lines from prose boundaries.
     */
    public static String stripChapterMetaProse(String content, String lang) {
        if (content == null) return "";
        content = content.trim();
        if (content.isEmpty()) return content;

        String[] lines = content.split("\n");
        List<String> lineList = new ArrayList<>(Arrays.asList(lines));

        // Strip from beginning
        while (!lineList.isEmpty()
                && isChapterMetaLine(lineList.get(0).trim(), lang)) {
            lineList.remove(0);
        }

        // Strip from end
        while (!lineList.isEmpty()
                && isChapterMetaLine(lineList.get(lineList.size() - 1).trim(), lang)) {
            lineList.remove(lineList.size() - 1);
        }

        return String.join("\n", lineList).trim();
    }

    // ===============================================================
    // isChapterMetaLine — Check if a line is meta/framing text
    // ===============================================================

    /**
     * Go: isChapterMetaLine
     * Returns true if the line is a chapter meta/framing line that should be stripped.
     */
    public static boolean isChapterMetaLine(String line, String lang) {
        if (line == null || line.isEmpty()) return false;

        // Exact match set
        String[] exactMatches = {
                "本章完", "本章终", "待续", "未完待续", "（完）", "(完)", "完", "——", "—",
                "***", "---",
                "End of chapter", "To be continued", "The End"
        };
        for (String s : exactMatches) {
            if (line.equals(s) || line.startsWith(s + ".") || line.startsWith(s + "。")) {
                return true;
            }
        }

        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            if (CHAPTER_META_START_EN.matcher(line).matches()) return true;
            if (line.matches("(?i)^\\(chapter\\s+\\d+.*\\)$")) return true;
            if (CHAPTER_META_PREAMBLE_EN.matcher(line).matches()) return true;
            return false;
        }

        // Chinese patterns
        if (CHAPTER_META_START_ZH.matcher(line).matches()) return true;
        if (line.matches("^[（(]第\\s*\\d+\\s*章[^）)]*[）)]$")) return true;
        if (CHAPTER_META_PREAMBLE_ZH.matcher(line).matches()) return true;
        // Match "以下为修订后的第X章完整正文" etc.
        if (line.matches("^.{0,10}(修订|修改|润色|重写).{0,5}第\\s*\\d+\\s*章")) return true;
        if (line.matches("^.{0,10}(完整|全部)?(正文|全文|内容).{0,5}(如下|如下方|如下所示)[：:。.]?\\s*$"))
            return true;

        return false;
    }

    // ===============================================================
    // parseFactCheckResult — Parse JSON fact check result
    // ===============================================================

    /**
     * Go: parseFactCheckResult
     * Parse fact-check result. Prefers JSON "result" field; falls back to
     * string matching on failure.
     */
    static FactCheckResult parseFactCheckResult(String raw) {
        FactCheckResult result = new FactCheckResult();
        if (raw == null || raw.isEmpty()) {
            result.failed = false;
            result.issues = "";
            return result;
        }

        String cleaned = cleanJSONResponse(raw);
        String jsonStr = extractJSON(cleaned);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JsonNode node = MAPPER.readTree(jsonStr);
                JsonNode resultField = node.get("result");
                if (resultField != null && !resultField.isNull()) {
                    String resultVal = resultField.asText().trim();
                    boolean failed = "FAIL".equalsIgnoreCase(resultVal);

                    StringBuilder issuesBuilder = new StringBuilder();
                    JsonNode issuesNode = node.get("issues");
                    if (issuesNode != null && issuesNode.isArray()) {
                        for (int i = 0; i < issuesNode.size(); i++) {
                            if (i > 0) issuesBuilder.append("；");
                            issuesBuilder.append(issuesNode.get(i).asText());
                        }
                    }
                    result.failed = failed;
                    result.issues = issuesBuilder.toString();
                    return result;
                }
            } catch (Exception ignored) {
                // Fall through to string fallback
            }
        }

        // Fallback: string matching
        result.failed = raw.contains("FAIL");
        result.issues = truncate(raw, 300);
        return result;
    }

    /**
     * Internal result holder for fact-check parsing.
     */
    static class FactCheckResult {
        boolean failed;
        String issues;
    }

    // ===============================================================
    // splitFactCheckIssues — Split issues string into list
    // ===============================================================

    /**
     * Go: splitFactCheckIssues (in writing_conflict.go)
     * Split fact-check issues string by Chinese/English semicolons.
     */
    public static List<String> splitFactCheckIssues(String issues) {
        return WritingConflictService.splitFactCheckIssues(issues);
    }

    // ===============================================================
    // mergeUniqueIssues — Merge unique issues from lists
    // ===============================================================

    /**
     * Go: mergeUniqueIssues (in writing_conflict.go)
     * Merge unique issues from multiple lists, preserving insertion order.
     */
    @SafeVarargs
    public static List<String> mergeUniqueIssues(List<String>... issueLists) {
        return WritingConflictService.mergeUniqueIssues(issueLists);
    }

    // ===============================================================
    // formatWritingPOVBlock — Format POV block for prompts
    // ===============================================================

    /**
     * Go: formatWritingPOVBlock
     */
    public static String formatWritingPOVBlock(String pov, String lang) {
        if (pov == null || pov.trim().isEmpty()) return "";
        pov = pov.trim();
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            return "[Narrative POV] " + pov;
        }
        return "【叙述视角】" + pov;
    }

    // ===============================================================
    // formatExtraWritingConstraintsBlock — Format constraints block
    // ===============================================================

    /**
     * Go: formatExtraWritingConstraintsBlock
     */
    public static String formatExtraWritingConstraintsBlock(String constraints, String lang) {
        if (constraints == null || constraints.trim().isEmpty()) return "";
        constraints = constraints.trim();
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            return "[Extra writing constraints (fact-check reconciliation)]\n" + constraints;
        }
        return "【补充写作约束（事实核查冲突调和）】\n" + constraints;
    }

    // ===============================================================
    // preferUserValue — Helper for value preference
    // ===============================================================

    /**
     * Go: preferUserValue
     * Return userVal if non-empty, otherwise fallback.
     */
    public static String preferUserValue(String userVal, String fallback) {
        if (userVal != null && !userVal.isEmpty()) return userVal;
        return fallback != null ? fallback : "";
    }

    // ===============================================================
    // countRunes — Count Unicode code points in a string
    // ===============================================================

    /**
     * Go: len([]rune(s))
     * Count Unicode code points (runes) in a string.
     */
    public static int countRunes(String s) {
        if (s == null) return 0;
        return s.codePointCount(0, s.length());
    }

    // ===============================================================
    // formatChapterLine — Format a chapter line for prompts
    // ===============================================================

    /**
     * Go: formatChapterLine (from i18n_inject.go)
     */
    public static String formatChapterLine(int num, String title, String outline, String lang) {
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            return String.format("Chapter %d \"%s\": %s\n", num, title, outline);
        }
        return String.format("第%d章《%s》：%s\n", num, title, outline);
    }

    // ===============================================================
    // buildHistorySummary — Build rolling summary of recent chapters
    // ===============================================================

    /**
     * Go: buildHistorySummary (Chinese default)
     */
    public static String buildHistorySummary(Progress state, int idx) {
        return I18nContextBuilder.buildHistorySummary(state, idx, LocaleHelper.LANG_ZH);
    }

    // ===============================================================
    // Private: generateChapterContentStream
    // ===============================================================

    /**
     * Go: generateChapterContentStream
     * Generate chapter content using streaming API call.
     */
    private String generateChapterContentStream(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int idx, ProjectSettings settings,
            String extraWritingConstraints) {

        ChapterState ch = state.getChapters().get(idx);
        String lang = cfg.getLanguage();

        String historySummary = I18nContextBuilder.buildHistorySummary(state, idx, lang);

        StoryConfig snapshot = state.getStoryConfigSnapshot();
        if (snapshot == null) {
            snapshot = cfg.getStory();
        }

        String foreshadowContext = I18nContextBuilder.formatActiveForeshadows(
                state.getForeshadows(), ch.getNum(), lang);

        String characterContext = I18nContextBuilder.buildCharacterContext(
                settings, ch.getOutline() != null ? ch.getOutline() : "", lang);
        String worldviewContext = I18nContextBuilder.buildWorldviewContext(
                settings, ch.getOutline() != null ? ch.getOutline() : "", lang);
        String outlineConstraints = I18nContextBuilder.buildOutlineConstraints(state, idx, lang);

        Map<String, String> promptVars = new HashMap<>();
        promptVars.put("Title", preferUserValue(cfg.getStory().getTitle(), state.getTitle()));
        promptVars.put("ChapterNum", String.valueOf(ch.getNum()));
        promptVars.put("CorePrompt", state.getCorePrompt() != null ? state.getCorePrompt() : "");
        promptVars.put("StorySynopsis", preferUserValue(cfg.getStory().getStorySynopsis(), state.getStorySynopsis()));
        promptVars.put("HistorySummary", historySummary);
        promptVars.put("PreviousEnding", I18nContextBuilder.buildPreviousChapterTail(state, idx, lang));
        promptVars.put("ChapterTitle", ch.getTitle() != null ? ch.getTitle() : "");
        promptVars.put("ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "");
        promptVars.put("WritingStyle", cfg.getStory().getWritingStyle() != null ? cfg.getStory().getWritingStyle() : "");
        promptVars.put("WritingPOV", cfg.getStory().getWritingPov() != null ? cfg.getStory().getWritingPov() : "");
        promptVars.put("CharacterContext", characterContext);
        promptVars.put("WorldviewContext", worldviewContext);
        promptVars.put("TargetWords", String.valueOf(snapshot.getTargetWordsPerChapter()));
        promptVars.put("Foreshadows", foreshadowContext);
        promptVars.put("OutlineConstraints", outlineConstraints);

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getChapterWriting(), promptVars);

        // Old-template fallback: append missing placeholders
        userPrompt = appendIfMissingPlaceholder(
                cfg.getPrompts().getChapterWriting(), userPrompt,
                "{{.OutlineConstraints}}", outlineConstraints);
        userPrompt = appendIfMissingPlaceholder(
                cfg.getPrompts().getChapterWriting(), userPrompt,
                "{{.Foreshadows}}", foreshadowContext);
        userPrompt = appendIfMissingPlaceholder(
                cfg.getPrompts().getChapterWriting(), userPrompt,
                "{{.WritingPOV}}", formatWritingPOVBlock(cfg.getStory().getWritingPov(), lang));

        String extraBlock = formatExtraWritingConstraintsBlock(extraWritingConstraints, lang);
        if (!extraBlock.isEmpty()) {
            userPrompt += "\n\n" + extraBlock;
        }

        String systemPrompt = state.getCorePrompt();
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = LocaleHelper.systemPromptFor(lang, "author_default");
        }

        // Notify frontend to clear stream buffer (avoid content stacking on retry/auto-continue)
        logger.streamStart(idx);

        String content = openAIClient.callAPIStream(ctx, apiCfg, systemPrompt, userPrompt,
                chunk -> logger.contentChunk(idx, chunk), null);

        return stripChapterMetaProse(content, lang);
    }

    // ===============================================================
    // Private: generateChapterSummary
    // ===============================================================

    /**
     * Go: generateChapterSummary
     */
    private String generateChapterSummary(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
            Config cfg, String content) {

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getChapterSummary(), Map.of(
                        "ChapterContent", content
                ));
        String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "summary_analyst");
        return openAIClient.callAPI(ctx, apiCfg, systemPrompt, userPrompt, null);
    }

    // ===============================================================
    // Private: generateChapterFactCheck
    // ===============================================================

    /**
     * Go: generateChapterFactCheck
     */
    private String generateChapterFactCheck(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int idx, String content, String historySummary) {

        ChapterState ch = state.getChapters().get(idx);
        String lang = cfg.getLanguage();
        String outlineConstraints = I18nContextBuilder.buildOutlineConstraints(state, idx, lang);

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getFactCheck(), Map.of(
                        "ChapterContent", content,
                        "HistorySummary", historySummary,
                        "CorePrompt", "",
                        "ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "",
                        "OutlineConstraints", outlineConstraints
                ));

        // Old-template fallback: if placeholder is missing, append the material
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            userPrompt = appendIfMissingPlaceholder(
                    cfg.getPrompts().getFactCheck(), userPrompt,
                    "{{.ChapterOutline}}",
                    "[Chapter outline]\n" + (ch.getOutline() != null ? ch.getOutline() : ""));
            if (!outlineConstraints.isEmpty()) {
                userPrompt = appendIfMissingPlaceholder(
                        cfg.getPrompts().getFactCheck(), userPrompt,
                        "{{.OutlineConstraints}}",
                        outlineConstraints
                                + "Supplementary audit scope (also count as reportable objective contradictions): "
                                + "(a) premature introduction of characters/events scheduled for later chapters per the outline; "
                                + "(b) one-time events from prior chapters (first meetings, identity reveals, etc.) being re-enacted as new in this chapter.");
            }
        } else {
            userPrompt = appendIfMissingPlaceholder(
                    cfg.getPrompts().getFactCheck(), userPrompt,
                    "{{.ChapterOutline}}",
                    "【本章大纲】\n" + (ch.getOutline() != null ? ch.getOutline() : ""));
            if (!outlineConstraints.isEmpty()) {
                userPrompt = appendIfMissingPlaceholder(
                        cfg.getPrompts().getFactCheck(), userPrompt,
                        "{{.OutlineConstraints}}",
                        outlineConstraints
                                + "补充核查范围（同样属于必须报告的客观矛盾）："
                                + "(a) 提前引入按章节脉络安排在后续章节才登场或发生的人物/事件；"
                                + "(b) 前文已发生的一次性事件（初次见面、身份揭示等）在本章作为新事件重复发生。");
            }
        }

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "fact_checker_json");
        return openAIClient.callAPI(ctx, apiCfg, systemPrompt, userPrompt, null);
    }

    // ===============================================================
    // Private: reviseChapterContentStream
    // ===============================================================

    /**
     * Go: reviseChapterContentStream
     * Revise chapter content based on user feedback using streaming.
     */
    private String reviseChapterContentStream(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int chapterIdx, String userFeedback,
            ProjectSettings settings) {

        ChapterState ch = state.getChapters().get(chapterIdx);
        String lang = cfg.getLanguage();

        String historySummary = I18nContextBuilder.buildHistorySummary(state, chapterIdx, lang);
        String characterContext = I18nContextBuilder.buildCharacterContext(
                settings, ch.getOutline() != null ? ch.getOutline() : "", lang);
        String worldviewContext = I18nContextBuilder.buildWorldviewContext(
                settings, ch.getOutline() != null ? ch.getOutline() : "", lang);

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getChapterRevision(), Map.of(
                        "ChapterNum", String.valueOf(ch.getNum()),
                        "ChapterTitle", ch.getTitle() != null ? ch.getTitle() : "",
                        "CorePrompt", state.getCorePrompt() != null ? state.getCorePrompt() : "",
                        "HistorySummary", historySummary,
                        "WritingStyle", cfg.getStory().getWritingStyle() != null ? cfg.getStory().getWritingStyle() : "",
                        "WritingPOV", cfg.getStory().getWritingPov() != null ? cfg.getStory().getWritingPov() : "",
                        "CharacterContext", characterContext,
                        "WorldviewContext", worldviewContext,
                        "OriginalContent", ch.getContent() != null ? ch.getContent() : "",
                        "UserFeedback", userFeedback
                ));

        userPrompt = appendIfMissingPlaceholder(
                cfg.getPrompts().getChapterRevision(), userPrompt,
                "{{.WritingPOV}}", formatWritingPOVBlock(cfg.getStory().getWritingPov(), lang));

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

    // ===============================================================
    // Private: reviseSubsequentOutlines
    // ===============================================================

    /**
     * Go: reviseSubsequentOutlines
     * After revising a chapter, optionally revise subsequent pending chapter
     * outlines if the feedback affects downstream plot.
     */
    private void reviseSubsequentOutlines(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int currentIdx, String userFeedback) throws Exception {

        String lang = cfg.getLanguage();
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        StringBuilder subsequentChapters = new StringBuilder();
        for (int i = currentIdx + 1; i < state.getChapters().size(); i++) {
            ChapterState ch = state.getChapters().get(i);
            if (!ChapterState.STATUS_ACCEPTED.equals(ch.getStatus())) {
                subsequentChapters.append(
                        formatChapterLine(ch.getNum(), ch.getTitle(), ch.getOutline(), lang));
            }
        }
        if (subsequentChapters.length() == 0) return;

        StringBuilder lockedChapters = new StringBuilder();
        for (int i = 0; i <= currentIdx; i++) {
            ChapterState ch = state.getChapters().get(i);
            if (en) {
                lockedChapters.append(String.format(
                        "Chapter %d \"%s\" (summary): %s\n", ch.getNum(), ch.getTitle(), ch.getSummary()));
            } else {
                lockedChapters.append(String.format(
                        "第%d章《%s》（摘要）: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary()));
            }
        }

        String feedbackWrap;
        if (en) {
            feedbackWrap = String.format(
                    "The user gave revision feedback on chapter %d: %s\nOnly adjust later chapter outlines if this feedback affects downstream plot. If it is just wording detail, return the outlines verbatim.",
                    state.getChapters().get(currentIdx).getNum(), userFeedback);
        } else {
            feedbackWrap = String.format(
                    "用户对第%d章提出了修改意见：%s\n请仅在该意见影响后续剧情时调整后续章节大纲；若意见只是文字细节修改，请原样返回大纲。",
                    state.getChapters().get(currentIdx).getNum(), userFeedback);
        }

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getOutlineRevision(), Map.of(
                        "CurrentOutline", subsequentChapters.toString(),
                        "UserFeedback", feedbackWrap,
                        "LockedChapters", lockedChapters.toString()
                ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_locked_json");

        String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new RuntimeException("API 调用失败或被取消");
        }
        rawResp = cleanJSONResponse(rawResp);

        OutlineResponse resp = MAPPER.readValue(rawResp, OutlineResponse.class);
        if (resp.getChapters() != null) {
            for (OutlineChapter newCh : resp.getChapters()) {
                for (int i = 0; i < state.getChapters().size(); i++) {
                    ChapterState existingCh = state.getChapters().get(i);
                    if (existingCh.getNum() == newCh.getNum()
                            && !ChapterState.STATUS_ACCEPTED.equals(existingCh.getStatus())) {
                        existingCh.setTitle(newCh.getTitle());
                        existingCh.setOutline(newCh.getOutline());
                    }
                }
            }
        }
    }

    // ===============================================================
    // Private: checkOutlineConsistency
    // ===============================================================

    /**
     * Go: checkOutlineConsistency
     * Pre-write outline consistency check: compare the current chapter outline
     * against recent history and the previous chapter's ending. If the outline
     * conflicts with actual story progression, use AI to produce a minimal
     * revision. Returns true if a revision was applied.
     */
    private boolean checkOutlineConsistency(
            OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
            Progress state, int idx) {

        ChapterState ch = state.getChapters().get(idx);
        if (ch.getOutline() == null || ch.getOutline().trim().isEmpty()) {
            return false;
        }

        String lang = cfg.getLanguage();
        String prevEnding = "";
        if (idx > 0) {
            ChapterState prev = state.getChapters().get(idx - 1);
            if (prev.getContent() != null && !prev.getContent().isEmpty()) {
                String tail = tailAtParagraph(prev.getContent(), PREV_TAIL_MAX_RUNES);
                if (!tail.isEmpty()) {
                    if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
                        prevEnding = "[Previous chapter ending]\n" + tail + "\n\n";
                    } else {
                        prevEnding = "【上一章结尾原文】\n" + tail + "\n\n";
                    }
                }
            }
        }

        String userPrompt = PromptRenderer.renderPrompt(
                cfg.getPrompts().getOutlineConsistencyCheck(), Map.of(
                        "ChapterNum", String.valueOf(ch.getNum()),
                        "ChapterTitle", ch.getTitle() != null ? ch.getTitle() : "",
                        "ChapterOutline", ch.getOutline(),
                        "HistorySummary", I18nContextBuilder.buildHistorySummary(state, idx, lang),
                        "PreviousEnding", prevEnding
                ));
        String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_brief_json");

        String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (rawResp == null || rawResp.isEmpty()) {
            logger.warnKey("log.outline_check_failed", "API 调用失败或被取消");
            return false;
        }

        String jsonStr = extractJSON(cleanJSONResponse(rawResp));
        if (jsonStr == null || jsonStr.isEmpty()) {
            logger.warnKey("log.outline_check_failed", "无法解析检查结果");
            return false;
        }

        try {
            JsonNode node = MAPPER.readTree(jsonStr);
            boolean conflict = node.has("conflict") && node.get("conflict").asBoolean(false);
            String revisedOutline = node.has("revised_outline")
                    ? node.get("revised_outline").asText("").trim() : "";

            if (!conflict || revisedOutline.isEmpty()) {
                return false;
            }

            // Collect issues for logging
            StringBuilder issuesBuilder = new StringBuilder();
            JsonNode issuesNode = node.get("issues");
            if (issuesNode != null && issuesNode.isArray()) {
                for (int i = 0; i < issuesNode.size(); i++) {
                    if (i > 0) issuesBuilder.append("；");
                    issuesBuilder.append(issuesNode.get(i).asText());
                }
            }
            logger.warnKey("log.outline_conflict", ch.getNum(), issuesBuilder.toString());
            ch.setOutline(revisedOutline);
            return true;
        } catch (Exception e) {
            logger.warnKey("log.outline_check_failed", e.getMessage());
            return false;
        }
    }

    // ===============================================================
    // appendIfMissingPlaceholder
    // ===============================================================

    /**
     * Go: appendIfMissingPlaceholder
     * Old-project compatibility: if the template lacks a placeholder,
     * append the content block to the rendered result.
     */
    static String appendIfMissingPlaceholder(
            String template, String rendered, String placeholder, String block) {
        if (block == null || block.trim().isEmpty()) return rendered;
        if (template != null && template.contains(placeholder)) return rendered;
        return rendered + "\n\n" + block.trim();
    }

    // ===============================================================
    // tailAtParagraph
    // ===============================================================

    /**
     * Go: tailAtParagraph
     * Take the last ~maxRunes characters of content, aligning forward to
     * paragraph boundary to avoid cutting mid-sentence.
     */
    static String tailAtParagraph(String content, int maxRunes) {
        if (content == null) return "";
        String trimmed = content.trim();
        int[] codePoints = trimmed.codePoints().toArray();
        if (codePoints.length <= maxRunes) return trimmed;

        String tail = new String(codePoints, codePoints.length - maxRunes, maxRunes);
        int nlIdx = tail.indexOf('\n');
        if (nlIdx >= 0 && nlIdx + 1 < tail.length()) {
            tail = tail.substring(nlIdx + 1);
        }
        return tail.trim();
    }

    // ===============================================================
    // splitChapterOpening
    // ===============================================================

    /**
     * Go: splitChapterOpening
     * Split chapter content into opening fragment and rest.
     * The cut point aligns backward to a paragraph boundary.
     *
     * @return String[2]: [0] = opening, [1] = rest (empty if whole chapter is opening)
     */
    static String[] splitChapterOpening(String content, int maxRunes) {
        if (content == null) return new String[]{"", ""};
        int[] codePoints = content.codePoints().toArray();
        if (codePoints.length <= maxRunes) {
            return new String[]{content, ""};
        }

        int cut = maxRunes;
        for (int i = maxRunes; i > 0; i--) {
            if (codePoints[i - 1] == '\n') {
                cut = i;
                break;
            }
        }
        String opening = new String(codePoints, 0, cut);
        String rest = new String(codePoints, cut, codePoints.length - cut);
        return new String[]{opening, rest};
    }

    // ===============================================================
    // Internal utilities
    // ===============================================================

    /**
     * Save a chapter's content as a markdown file.
     */
    private void saveChapterMarkdown(String projectDir, ChapterState ch, String storyTitle) {
        try {
            String path = projectService.chapterMarkdownPath(projectDir, ch.getNum());
            String content = ch.getContent() != null ? ch.getContent() : "";
            Files.writeString(Paths.get(path), content);
        } catch (Exception e) {
            log.warn("保存章节 Markdown 失败: {}", e.getMessage());
        }
    }

    /**
     * Sleep for the given seconds, returning false if cancelled.
     */
    private boolean sleepOrCancel(OpenAIClient.CancellationToken ctx, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (ctx != null && ctx.isCancelled()) return false;
            try {
                long remaining = deadline - System.currentTimeMillis();
                Thread.sleep(Math.min(remaining, 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Strip markdown code fences from a JSON response.
     */
    private static String cleanJSONResponse(String s) {
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

    /**
     * Extract JSON object/array from a string by finding the first { or [
     * and matching closing bracket.
     */
    private static String extractJSON(String s) {
        if (s == null) return "";
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        start = s.indexOf('[');
        end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    /**
     * Truncate string to max characters.
     */
    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars);
    }

    /**
     * Get a substring by Unicode code points.
     */
    private static String substringByRunes(String s, int maxRunes) {
        if (s == null) return "";
        int[] cps = s.codePoints().toArray();
        if (cps.length <= maxRunes) return s;
        return new String(cps, 0, maxRunes);
    }

    /**
     * Strip leading occurrences of a character from a string.
     */
    private static String stripLeading(String s, char c) {
        if (s == null) return "";
        int i = 0;
        while (i < s.length() && s.charAt(i) == c) {
            i++;
        }
        return s.substring(i);
    }
}
