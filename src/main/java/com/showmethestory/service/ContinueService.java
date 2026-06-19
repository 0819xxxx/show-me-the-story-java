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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content import and analysis for continuing an existing story.
 * Ported from Go continue.go.
 */
@Service
public class ContinueService {

    private static final Logger log = LoggerFactory.getLogger(ContinueService.class);
    private static final Pattern CHAPTER_SPLIT_RE = Pattern.compile(
            "(?m)^[\\s]*(第[一二三四五六七八九十百千\\d]+章|Chapter\\s+\\d+|#\\s+Chapter\\s+\\d+|第\\d+章)");

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;

    private volatile String pendingContinueContent = "";

    public ContinueService(ProjectService projectService,
                           StateService stateService,
                           OpenAIClient openAIClient,
                           LogBroadcaster logger) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logger = logger;
    }

    /**
     * Go: PostContinueImport handler + AnalyzeExistingContent.
     * Called in background thread.
     */
    public void analyzeContent(OpenAIClient.CancellationToken ctx, String content) {
        try {
            logger.taskStart("continue_analysis");

            APIConfig apiCfg = projectService.getAPIConfig();
            Config cfg = projectService.getConfig();

            logger.infoKey("log.continue_analyzing");

            String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getContentAnalysis(),
                    Map.of("ExistingContent", content));
            String systemPrompt = LocaleHelper.systemPromptFor(cfg.getLanguage(), "content_analyst_json");

            String rawResp = openAIClient.callAPIWithRetry(ctx, apiCfg, systemPrompt, userPrompt, logger, null);
            if (rawResp == null || rawResp.isEmpty()) {
                throw new Exception("API 调用失败或被取消");
            }
            rawResp = OpenAIClient.cleanJSONFences(rawResp);

            ContinueAnalysis analysis = openAIClient.getObjectMapper().readValue(rawResp, ContinueAnalysis.class);

            pendingContinueContent = content;

            logger.successKey("log.continue_analyze_done",
                    analysis.getChapters() != null ? analysis.getChapters().size() : 0);
            logger.taskEnd("continue_analysis", true);
            logger.continueAnalysisResult(analysis);
        } catch (Exception e) {
            log.error("Continue analysis failed", e);
            if (ctx != null && ctx.isCancelled()) {
                logger.warnKey("log.continue_analyze_cancelled");
            } else {
                logger.errorKey("log.continue_analyze_failed", e.getMessage());
            }
            logger.taskEnd("continue_analysis", false);
        }
    }

    /**
     * Returns true if there is pending continue content awaiting confirmation.
     */
    public boolean hasPendingContent() {
        return pendingContinueContent != null && !pendingContinueContent.isEmpty();
    }

    /**
     * Go: PostContinueConfirm handler + ImportContinueAction.
     */
    public ResponseEntity<?> confirmAndImport(ContinueAnalysis analysis) {
        try {
            Progress state = projectService.getProgress();
            Config cfg = projectService.getConfig();
            String progressPath = projectService.getProgressPath();
            String cfgPath = projectService.getCfgPath();

            if (!"outline".equals(state.getPhase())) {
                return ResponseEntity.badRequest().body(Map.of("error", "continue_reset_first"));
            }
            if (pendingContinueContent == null || pendingContinueContent.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "continue_analyze_first"));
            }
            if (analysis.getChapters() == null || analysis.getChapters().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "analysis_no_chapters"));
            }

            String content = pendingContinueContent;
            pendingContinueContent = "";

            importContinueAction(cfg, state, analysis, content, progressPath, cfgPath);

            logger.successKey("log.continue_import_done");
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Continue import failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "continue_import_failed", "detail", e.getMessage()));
        }
    }

    /**
     * Go: ImportContinueAction - import analyzed content into project state.
     */
    private void importContinueAction(Config cfg, Progress state, ContinueAnalysis analysis,
                                       String content, String progressPath, String cfgPath) throws Exception {
        state.setTitle(analysis.getTitle());
        state.setCorePrompt(analysis.getCorePrompt());
        state.setStorySynopsis(analysis.getStorySynopsis());

        List<String> segments = splitContentByChapters(content, analysis.getChapters());

        List<ChapterState> chapters = new ArrayList<>();
        for (int i = 0; i < analysis.getChapters().size(); i++) {
            ContinueChapter ach = analysis.getChapters().get(i);
            String chapterContent = (i < segments.size()) ? segments.get(i) : "";
            ChapterState cs = new ChapterState();
            cs.setNum(i + 1);
            cs.setTitle(ach.getTitle());
            cs.setOutline(ach.getOutline());
            cs.setContent(chapterContent);
            cs.setSummary(ach.getSummary());
            cs.setStatus("accepted");
            chapters.add(cs);
        }
        state.setChapters(chapters);
        state.setCurrentChapterIndex(analysis.getChapters().size());
        state.setPhase("outline");

        StoryConfig snapshot = new StoryConfig();
        snapshot.setType(analysis.getStoryType());
        snapshot.setTitle(analysis.getTitle());
        snapshot.setChapterCount(chapters.size());
        snapshot.setTargetWordsPerChapter(cfg.getStory().getTargetWordsPerChapter());
        snapshot.setWritingStyle(analysis.getWritingStyle());
        snapshot.setWritingPov(analysis.getWritingPov());
        snapshot.setStorySynopsis(analysis.getStorySynopsis());
        state.setStoryConfigSnapshot(snapshot);

        // Update config
        cfg.getStory().setType(analysis.getStoryType());
        cfg.getStory().setTitle(analysis.getTitle());
        cfg.getStory().setWritingStyle(analysis.getWritingStyle());
        cfg.getStory().setWritingPov(analysis.getWritingPov());
        cfg.getStory().setStorySynopsis(analysis.getStorySynopsis());

        stateService.saveProgress(progressPath, state);
        stateService.saveConfig(cfgPath, cfg);
    }

    /**
     * Go: splitContentByChapters - split content into segments by chapter headers.
     */
    private List<String> splitContentByChapters(String content, List<ContinueChapter> chapters) {
        if (chapters == null || chapters.isEmpty()) return null;

        Matcher matcher = CHAPTER_SPLIT_RE.matcher(content);
        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }

        if (matches.isEmpty()) {
            return List.of(content);
        }

        List<String> segments = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            int start = matches.get(i)[0];
            int end = (i + 1 < matches.size()) ? matches.get(i + 1)[0] : content.length();
            String seg = content.substring(start, end).trim();
            if (!seg.isEmpty()) {
                segments.add(seg);
            }
        }

        if (segments.isEmpty()) {
            return List.of(content);
        }
        return segments;
    }
}
