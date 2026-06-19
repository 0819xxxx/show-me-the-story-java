package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Foreshadow-outline consistency checking.
 * Maps to Go: foreshadow_consistency.go.
 */
@Service
public class ForeshadowConsistencyService {

    private static final Logger log = LoggerFactory.getLogger(ForeshadowConsistencyService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final StateService stateService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logBroadcaster;

    public ForeshadowConsistencyService(ProjectService projectService, StateService stateService,
                                         OpenAIClient openAIClient, LogBroadcaster logBroadcaster) {
        this.projectService = projectService;
        this.stateService = stateService;
        this.openAIClient = openAIClient;
        this.logBroadcaster = logBroadcaster;
    }

    /**
     * Run foreshadow-outline consistency check and save results.
     */
    public void runForeshadowOutlineCheckAndSave(OpenAIClient.CancellationToken ct) {
        Progress state = projectService.getProgress();
        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) return;

        try {
            ForeshadowOutlineReport report = checkForeshadowOutlineConsistency(ct);
            applyForeshadowOutlineReport(state, report);
            stateService.saveProgress(projectService.getProgressPath(), state);

            if (report.isHasConflicts()) {
                logBroadcaster.foreshadowOutlineConflicts(report);
            } else {
                logBroadcaster.infoKey("log.foreshadow_outline_check_pass");
            }
        } catch (Exception e) {
            logBroadcaster.warnKey("log.foreshadow_outline_check_failed", e.getMessage());
        }
    }

    public ForeshadowOutlineReport checkForeshadowOutlineConsistency(OpenAIClient.CancellationToken ct) throws Exception {
        Progress state = projectService.getProgress();
        if (state.getForeshadows() == null || state.getForeshadows().isEmpty()) {
            ForeshadowOutlineReport empty = new ForeshadowOutlineReport();
            empty.setSummary("无伏笔");
            return empty;
        }

        Config cfg = projectService.getConfig();
        APIConfig apiCfg = projectService.getAPIConfig();
        String lang = cfg.getLanguage();

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getForeshadowOutlineConsistency(), Map.of(
                "Title", preferNonEmpty(cfg.getStory().getTitle(), state.getTitle()),
                "Outline", buildFullOutlineText(state, lang),
                "Foreshadows", new ForeshadowService(projectService, stateService, openAIClient, logBroadcaster)
                        .formatForeshadowsForPrompt(state.getForeshadows()),
                "AcceptedSummaries", buildAcceptedSummariesText(state, lang)
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "foreshadow_outline_checker_json");
        String rawResp = openAIClient.callAPIWithRetry(ct, apiCfg, systemPrompt, userPrompt, logBroadcaster, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new RuntimeException("API 调用失败或被取消");
        }

        String jsonStr = extractJSON(ForeshadowService.cleanJSONResponse(rawResp));
        if (jsonStr == null || jsonStr.isEmpty()) {
            throw new RuntimeException("无法解析伏笔-大纲一致性检查结果");
        }

        return MAPPER.readValue(jsonStr, ForeshadowOutlineReport.class);
    }

    private void applyForeshadowOutlineReport(Progress state, ForeshadowOutlineReport report) {
        if (report != null) {
            state.setLastForeshadowOutlineReport(report);
        }
    }

    private String buildFullOutlineText(Progress state, String lang) {
        StringBuilder sb = new StringBuilder();
        for (ChapterState ch : state.getChapters()) {
            sb.append(ForeshadowService.formatChapterLine(ch, lang));
        }
        return sb.toString();
    }

    private String buildAcceptedSummariesText(Progress state, String lang) {
        boolean en = "en".equals(LocaleHelper.normalizeLanguage(lang));
        StringBuilder sb = new StringBuilder();
        for (ChapterState ch : state.getChapters()) {
            if (!"accepted".equals(ch.getStatus()) || ch.getSummary() == null || ch.getSummary().isEmpty()) continue;
            if (en) {
                sb.append("Chapter ").append(ch.getNum()).append(" \"").append(ch.getTitle())
                        .append("\": ").append(ch.getSummary()).append("\n");
            } else {
                sb.append("第").append(ch.getNum()).append("章《").append(ch.getTitle())
                        .append("》：").append(ch.getSummary()).append("\n");
            }
        }
        if (sb.isEmpty()) {
            return en ? "(no confirmed chapters yet)" : "尚无已确认章节。";
        }
        return sb.toString();
    }

    private String preferNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : (b != null ? b : "");
    }

    static String extractJSON(String s) {
        if (s == null) return "";
        // Find first { and last }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        // Try array
        start = s.indexOf('[');
        end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}
