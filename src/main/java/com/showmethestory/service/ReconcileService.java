package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI-driven settings reconciliation.
 * Ported from Go reconcile.go.
 */
@Service
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final ForeshadowConsistencyService foreshadowConsistencyService;

    public ReconcileService(ProjectService projectService,
                            StateService stateService,
                            OpenAIClient openAIClient,
                            LogBroadcaster logger,
                            ForeshadowConsistencyService foreshadowConsistencyService) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logger = logger;
        this.foreshadowConsistencyService = foreshadowConsistencyService;
    }

    /**
     * Go: ReconcileSettingsAction - reconcile new settings with existing chapters.
     * Called in background thread from SettingsController.
     */
    public void reconcileSettings(OpenAIClient.CancellationToken ctx, StoryConfig newSettings) {
        try {
            logger.taskStart("settings_reconciliation");
            logger.infoKey("log.settings_reconciling");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();
            String cfgPath = projectService.getCfgPath();

            logger.stepInfo(1, 3, "正在分析已有章节与新设定的兼容性...");
            String lang = cfg.getLanguage();
            boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

            String acceptedSummaries = "";
            if (state.getChapters() != null) {
                for (ChapterState ch : state.getChapters()) {
                    if ("accepted".equals(ch.getStatus()) && ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                        if (en) {
                            acceptedSummaries += String.format("Chapter %d \"%s\" summary: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                        } else {
                            acceptedSummaries += String.format("第%d章《%s》摘要: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                        }
                    }
                }
            }
            if (acceptedSummaries.isEmpty()) {
                acceptedSummaries = en ? "(no confirmed chapters yet)" : "尚无已确认章节。";
            }

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getSettingsReconciliation(), Map.of(
                    "NewType", newSettings.getType(),
                    "NewWritingStyle", newSettings.getWritingStyle(),
                    "NewWritingPOV", newSettings.getWritingPov(),
                    "NewStorySynopsis", newSettings.getStorySynopsis(),
                    "ExistingSummaries", acceptedSummaries
            ));

            String systemPrompt = LocaleHelper.systemPromptFor(lang, "consistency_reviewer_json");

            String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (rawResp == null || rawResp.isEmpty()) {
                throw new Exception("API 调用失败或被取消");
            }
            rawResp = OpenAIClient.cleanJSONFences(rawResp);

            ReconciliationResult result = openAIClient.getObjectMapper().readValue(rawResp, ReconciliationResult.class);

            logger.stepInfo(2, 3, "正在更新设定...");

            StoryConfig adjustedStory = cfg.getStory();
            adjustedStory.setType(result.getType());
            adjustedStory.setWritingStyle(result.getWritingStyle());
            adjustedStory.setWritingPov(result.getWritingPov());
            adjustedStory.setStorySynopsis(result.getStorySynopsis());

            state.setStoryConfigSnapshot(adjustedStory);

            // Check if there are pending chapters that need outline regeneration
            boolean hasPending = false;
            if (state.getChapters() != null) {
                for (ChapterState ch : state.getChapters()) {
                    if ("pending".equals(ch.getStatus())) {
                        hasPending = true;
                        break;
                    }
                }
            }

            if (hasPending) {
                logger.stepInfo(3, 3, "正在基于新设定重新生成待定章节大纲...");
                try {
                    regeneratePendingOutlines(ctx, apiCfg, cfg, state, adjustedStory);
                } catch (Exception e) {
                    logger.warnKey("log.reconcile_pending_outline_failed", e.getMessage());
                }
            }

            cfg.setStory(adjustedStory);

            stateService.saveConfig(cfgPath, cfg);
            stateService.saveProgress(progressPath, state);

            foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(ctx);

            logger.successKey("log.reconcile_done_explain", result.getExplanation());

            // Track which fields were changed by AI
            List<String> changedFields = new ArrayList<>();
            if (!Objects.equals(result.getType(), newSettings.getType())) changedFields.add("type");
            if (!Objects.equals(result.getWritingStyle(), newSettings.getWritingStyle())) changedFields.add("writing_style");
            if (!Objects.equals(result.getWritingPov(), newSettings.getWritingPov())) changedFields.add("writing_pov");
            if (!Objects.equals(result.getStorySynopsis(), newSettings.getStorySynopsis())) changedFields.add("story_synopsis");

            Map<String, Object> reconcileInfo = new LinkedHashMap<>();
            reconcileInfo.put("explanation", result.getExplanation());
            reconcileInfo.put("changed_fields", changedFields);
            logger.settingsReconciled(reconcileInfo);

            logger.taskEnd("settings_reconciliation", true);
            broadcastProgress();
        } catch (Exception e) {
            log.error("Settings reconciliation failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.settings_reconcile_cancelled");
            } else {
                logger.errorKey("log.settings_reconcile_failed", e.getMessage());
            }
            logger.taskEnd("settings_reconciliation", false);
        }
    }

    /**
     * Go: regeneratePendingOutlines - re-generate pending chapter outlines based on new settings.
     */
    private void regeneratePendingOutlines(OpenAIClient.CancellationToken ctx, APIConfig apiCfg,
                                            Config cfg, Progress state, StoryConfig adjustedStory) throws Exception {
        String lang = cfg.getLanguage();
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        String pendingChapters = "";
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if ("pending".equals(ch.getStatus())) {
                    pendingChapters += ForeshadowService.formatChapterLine(ch, lang);
                }
            }
        }

        String lockedChapters = "";
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if ("accepted".equals(ch.getStatus())) {
                    if (en) {
                        lockedChapters += String.format("Chapter %d \"%s\" (summary): %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                    } else {
                        lockedChapters += String.format("第%d章《%s》（摘要）: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                    }
                }
            }
        }
        if (lockedChapters.isEmpty()) {
            lockedChapters = en ? "(no locked chapters)" : "无已锁定章节。";
        }

        String feedback;
        if (en) {
            feedback = String.format("Story settings updated to: type=%s, writing_style=%s, writing_pov=%s, synopsis=%s. Adjust the pending chapter outlines so they stay consistent with the new settings.",
                    adjustedStory.getType(), adjustedStory.getWritingStyle(), adjustedStory.getWritingPov(), adjustedStory.getStorySynopsis());
        } else {
            feedback = String.format("故事设定已更新为：类型=%s，写作风格=%s，叙述视角=%s，故事梗概=%s。请根据新设定调整待定章节大纲。",
                    adjustedStory.getType(), adjustedStory.getWritingStyle(), adjustedStory.getWritingPov(), adjustedStory.getStorySynopsis());
        }

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getOutlineRevision(), Map.of(
                "CurrentOutline", pendingChapters,
                "UserFeedback", feedback,
                "LockedChapters", lockedChapters
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_locked_json");

        String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new Exception("API 调用失败或被取消");
        }
        rawResp = OpenAIClient.cleanJSONFences(rawResp);

        OutlineResponse resp = openAIClient.getObjectMapper().readValue(rawResp, OutlineResponse.class);
        OutlineService.applyOutlineRevision(resp, state);
    }

    private void broadcastProgress() {
        Progress state = projectService.getProgress();
        int accepted = 0, total = 0;
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
        logger.progressUpdate(data);
    }
}
