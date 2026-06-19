package com.showmethestory.service;

import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Outline generation, revision, confirmation, continuation, edit, and deletion.
 * Ported from Go outline.go + relevant handler logic.
 */
@Service
public class OutlineService {

    private static final Logger log = LoggerFactory.getLogger(OutlineService.class);

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final ForeshadowConsistencyService foreshadowConsistencyService;

    public OutlineService(ProjectService projectService,
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
     * Go: GenerateOutlineAction - generate a full outline from scratch.
     * Clears old pending chapters, calls AI, saves progress.
     */
    public void generateOutline(OpenAIClient.CancellationToken ctx) {
        try {
            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            openAIClient.validateAPIConfig(apiCfg);

            // Clear old pending chapters
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
                List<ChapterState> kept = new ArrayList<>();
                for (ChapterState ch : state.getChapters()) {
                    if (!"pending".equals(ch.getStatus())) {
                        kept.add(ch);
                    }
                }
                state.setChapters(kept);
                if (kept.isEmpty()) {
                    state.setTitle("");
                    state.setCorePrompt("");
                    state.setStorySynopsis("");
                    state.setStoryConfigSnapshot(null);
                    state.setCurrentChapterIndex(0);
                }
                logger.infoKey("log.outline_cleared_pending");
            }

            logger.taskStart("outline_generation");
            logger.infoKey("log.outline_generating");

            logger.stepInfo(1, 2, "正在调用 AI 生成大纲...");
            OutlineResponse resp = generateOutlineAPI(ctx, apiCfg, cfg);

            logger.stepInfo(2, 2, "正在保存大纲...");

            state.setTitle(resp.getTitle());
            state.setCorePrompt(resp.getCorePrompt());
            state.setStorySynopsis(resp.getStorySynopsis());
            List<ChapterState> chapters = new ArrayList<>();
            if (resp.getChapters() != null) {
                for (OutlineChapter oc : resp.getChapters()) {
                    ChapterState cs = new ChapterState();
                    cs.setNum(oc.getNum());
                    cs.setTitle(oc.getTitle());
                    cs.setOutline(oc.getOutline());
                    cs.setStatus("pending");
                    chapters.add(cs);
                }
            }
            state.setChapters(chapters);

            // Snapshot the story config
            state.setStoryConfigSnapshot(cfg.getStory());

            stateService.saveProgress(progressPath, state);

            logger.successKey("log.outline_generate_summary", chapters.size(), state.getTitle());
            logger.successKey("log.outline_generate_done");
            logger.taskEnd("outline_generation", true);
        } catch (Exception e) {
            log.error("Outline generation failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.outline_generate_cancelled");
            } else {
                logger.errorKey("log.outline_generate_failed", e.getMessage());
            }
            logger.taskEnd("outline_generation", false);
        }
    }

    /**
     * Go: generateOutline - call AI to generate outline JSON.
     */
    private OutlineResponse generateOutlineAPI(OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg) throws Exception {
        String chapterCountStr = String.valueOf(cfg.getStory().getChapterCount());
        String targetWordsStr = String.valueOf(cfg.getStory().getTargetWordsPerChapter());

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getOutlineGeneration(), Map.of(
                "StoryType", cfg.getStory().getType(),
                "ChapterCount", chapterCountStr,
                "TargetWords", targetWordsStr,
                "WritingStyle", cfg.getStory().getWritingStyle(),
                "WritingPOV", cfg.getStory().getWritingPov(),
                "StorySynopsis", cfg.getStory().getStorySynopsis()
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "outline_editor_json");

        String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new Exception("API 调用失败或被取消");
        }

        rawResp = OpenAIClient.cleanJSONFences(rawResp);

        return openAIClient.getObjectMapper().readValue(rawResp, OutlineResponse.class);
    }

    /**
     * Go: ConfirmOutlineAction - transition from outline to writing phase.
     */
    public ResponseEntity<?> confirmOutline() {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            if (state.getChapters() == null || state.getChapters().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "outline_empty"));
            }

            state.setPhase("writing");
            stateService.saveProgress(progressPath, state);

            logger.successKey("log.outline_confirmed");
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Confirm outline failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "outline_confirm_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: ReviseOutlineAction - revise outline based on user feedback.
     */
    public void reviseOutline(OpenAIClient.CancellationToken ctx, String feedback) {
        try {
            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            logger.taskStart("outline_revision");
            logger.infoKey("log.outline_revising");

            logger.stepInfo(1, 2, "正在根据意见修订大纲...");
            reviseOutlineAPI(ctx, apiCfg, cfg, state, feedback);

            logger.stepInfo(2, 2, "正在保存修订后的大纲...");
            stateService.saveProgress(progressPath, state);

            // Run foreshadow consistency check
            foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(ctx);

            int chapterCount = state.getChapters() != null ? state.getChapters().size() : 0;
            logger.successKey("log.outline_revise_summary", chapterCount);
            logger.successKey("log.outline_revised");
            logger.taskEnd("outline_revision", true);
        } catch (Exception e) {
            log.error("Outline revision failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.outline_revise_cancelled");
            } else {
                logger.errorKey("log.outline_revise_failed", e.getMessage());
            }
            logger.taskEnd("outline_revision", false);
        }
    }

    /**
     * Go: reviseOutline - call AI to revise outline and apply changes.
     */
    private void reviseOutlineAPI(OpenAIClient.CancellationToken ctx, APIConfig apiCfg, Config cfg,
                                   Progress state, String userFeedback) throws Exception {
        String lang = cfg.getLanguage();
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        String lockedChapters = "";
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                if ("accepted".equals(ch.getStatus())) {
                    lockedChapters += ForeshadowService.formatChapterLine(ch, lang);
                }
            }
        }
        if (lockedChapters.isEmpty()) {
            lockedChapters = en ? "(no locked chapters)" : "无已锁定章节。";
        }

        String currentOutline = "";
        if (state.getChapters() != null) {
            for (ChapterState ch : state.getChapters()) {
                currentOutline += ForeshadowService.formatChapterLine(ch, lang);
            }
        }

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getOutlineRevision(), Map.of(
                "CurrentOutline", currentOutline,
                "UserFeedback", userFeedback,
                "LockedChapters", lockedChapters
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_locked_json");

        String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new Exception("API 调用失败或被取消");
        }
        rawResp = OpenAIClient.cleanJSONFences(rawResp);

        OutlineResponse resp = openAIClient.getObjectMapper().readValue(rawResp, OutlineResponse.class);
        applyOutlineRevision(resp, state);
    }

    /**
     * Go: applyOutlineRevision - apply AI response to state, skipping locked chapters.
     */
    static void applyOutlineRevision(OutlineResponse resp, Progress state) {
        if (state.getChapters() == null) return;

        // Build locked set
        java.util.Set<Integer> lockedMap = new java.util.HashSet<>();
        for (ChapterState ch : state.getChapters()) {
            if ("accepted".equals(ch.getStatus())) {
                lockedMap.add(ch.getNum());
            }
        }

        if (resp.getChapters() != null) {
            for (OutlineChapter newCh : resp.getChapters()) {
                for (int i = 0; i < state.getChapters().size(); i++) {
                    ChapterState existingCh = state.getChapters().get(i);
                    if (existingCh.getNum() == newCh.getNum() && !lockedMap.contains(newCh.getNum())) {
                        existingCh.setTitle(newCh.getTitle());
                        existingCh.setOutline(newCh.getOutline());
                    }
                }
            }
        }

        if (resp.getTitle() != null && !resp.getTitle().isEmpty()) {
            state.setTitle(resp.getTitle());
        }
        if (resp.getCorePrompt() != null && !resp.getCorePrompt().isEmpty()) {
            state.setCorePrompt(resp.getCorePrompt());
        }
        if (resp.getStorySynopsis() != null && !resp.getStorySynopsis().isEmpty()) {
            state.setStorySynopsis(resp.getStorySynopsis());
        }
    }

    /**
     * Go: GenerateContinuationOutline (from continue.go).
     * Generate additional chapters to append to the existing outline.
     */
    public void generateContinuationOutline(OpenAIClient.CancellationToken ctx, int chapterCount) {
        try {
            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            logger.taskStart("continuation_outline_generation");

            openAIClient.validateAPIConfig(apiCfg);

            String lang = cfg.getLanguage();
            boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

            // Build existing chapter summaries for accepted, outlines for pending
            String existingChapters = "";
            if (state.getChapters() != null) {
                for (ChapterState ch : state.getChapters()) {
                    if ("accepted".equals(ch.getStatus()) && ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                        if (en) {
                            existingChapters += String.format("Chapter %d \"%s\" (summary): %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                        } else {
                            existingChapters += String.format("第%d章《%s》（摘要）: %s\n", ch.getNum(), ch.getTitle(), ch.getSummary());
                        }
                    } else {
                        existingChapters += ForeshadowService.formatChapterLine(ch, lang);
                    }
                }
            }

            int nextNum = 1;
            if (state.getChapters() != null && !state.getChapters().isEmpty()) {
                nextNum = state.getChapters().get(state.getChapters().size() - 1).getNum() + 1;
            }

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getContinuationOutlineGeneration(), Map.of(
                    "ExistingChapters", existingChapters,
                    "ChapterCount", String.valueOf(chapterCount),
                    "NextChapterNum", String.valueOf(nextNum),
                    "StoryType", cfg.getStory().getType(),
                    "WritingStyle", cfg.getStory().getWritingStyle(),
                    "WritingPOV", cfg.getStory().getWritingPov(),
                    "StorySynopsis", cfg.getStory().getStorySynopsis()
            ));

            String systemPrompt = LocaleHelper.systemPromptFor(lang, "outline_editor_json");

            logger.stepInfo(1, 2, "正在生成后续大纲...");
            String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (rawResp == null || rawResp.isEmpty()) {
                throw new Exception("API 调用失败或被取消");
            }
            rawResp = OpenAIClient.cleanJSONFences(rawResp);

            OutlineResponse resp = openAIClient.getObjectMapper().readValue(rawResp, OutlineResponse.class);

            logger.stepInfo(2, 2, "正在保存后续大纲...");

            if (resp.getChapters() != null) {
                if (state.getChapters() == null) {
                    state.setChapters(new ArrayList<>());
                }
                for (OutlineChapter oc : resp.getChapters()) {
                    ChapterState cs = new ChapterState();
                    cs.setNum(oc.getNum());
                    cs.setTitle(oc.getTitle());
                    cs.setOutline(oc.getOutline());
                    cs.setStatus("pending");
                    state.getChapters().add(cs);
                }
            }
            if (resp.getTitle() != null && !resp.getTitle().isEmpty()) {
                state.setTitle(resp.getTitle());
            }
            if (resp.getCorePrompt() != null && !resp.getCorePrompt().isEmpty()) {
                state.setCorePrompt(resp.getCorePrompt());
            }
            if (resp.getStorySynopsis() != null && !resp.getStorySynopsis().isEmpty()) {
                state.setStorySynopsis(resp.getStorySynopsis());
            }

            stateService.saveProgress(progressPath, state);

            logger.successKey("log.continuation_outline_done", resp.getChapters() != null ? resp.getChapters().size() : 0);
            logger.taskEnd("continuation_outline_generation", true);
        } catch (Exception e) {
            log.error("Continuation outline generation failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.continuation_outline_cancelled");
            } else {
                logger.errorKey("log.continuation_outline_failed", e.getMessage());
            }
            logger.taskEnd("continuation_outline_generation", false);
        }
    }

    /**
     * Go: EditChapterOutline - edit a specific chapter's outline (only if pending).
     */
    public ResponseEntity<?> editChapterOutline(int chapterNum, String title, String outline) {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            int idx = -1;
            if (state.getChapters() != null) {
                for (int i = 0; i < state.getChapters().size(); i++) {
                    if (state.getChapters().get(i).getNum() == chapterNum) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx == -1) {
                return ResponseEntity.badRequest().body(Map.of("error", "chapter_not_found", "num", chapterNum));
            }
            if (!"pending".equals(state.getChapters().get(idx).getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "only_pending_editable"));
            }

            state.getChapters().get(idx).setTitle(title);
            state.getChapters().get(idx).setOutline(outline);
            stateService.saveProgress(progressPath, state);

            // Trigger async foreshadow outline check
            if (state.getForeshadows() != null && !state.getForeshadows().isEmpty()) {
                foreshadowConsistencyService.runForeshadowOutlineCheckAndSave(null);
            }

            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Edit chapter outline failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "edit_outline_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: DeleteOutline - delete the entire outline.
     */
    public ResponseEntity<?> deleteOutline() {
        try {
            Progress state = projectService.getProgress();
            String progressPath = projectService.getProgressPath();

            // Reject if any chapter is writing/review
            if (state.getChapters() != null) {
                for (ChapterState ch : state.getChapters()) {
                    if ("writing".equals(ch.getStatus()) || "review".equals(ch.getStatus())) {
                        return ResponseEntity.status(409).body(Map.of("error", "writing_chapter_present_delete"));
                    }
                }
            }

            state.setTitle("");
            state.setCorePrompt("");
            state.setStorySynopsis("");
            state.setChapters(null);
            state.setStoryConfigSnapshot(null);
            state.setCurrentChapterIndex(0);

            stateService.saveProgress(progressPath, state);

            logger.successKey("log.outline_deleted");
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Delete outline failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "delete_outline_failed", "detail", e.getMessage()));
        }
    }
}
