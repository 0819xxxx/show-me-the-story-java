package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Writing conflict analysis and resolution.
 * Maps to Go: writing_conflict.go.
 */
@Service
public class WritingConflictService {

    private static final Logger log = LoggerFactory.getLogger(WritingConflictService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProjectService projectService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logBroadcaster;
    private final ForeshadowService foreshadowService;

    public WritingConflictService(ProjectService projectService, OpenAIClient openAIClient,
                                   LogBroadcaster logBroadcaster, ForeshadowService foreshadowService) {
        this.projectService = projectService;
        this.openAIClient = openAIClient;
        this.logBroadcaster = logBroadcaster;
        this.foreshadowService = foreshadowService;
    }

    /**
     * Analyze a writing conflict using AI.
     */
    public WritingConflictAnalysis analyzeWritingConflict(OpenAIClient.CancellationToken ct,
                                                           Progress state, int idx,
                                                           String content, List<String> failedIssues) throws Exception {
        Config cfg = projectService.getConfig();
        APIConfig apiCfg = projectService.getAPIConfig();
        String lang = cfg.getLanguage();
        ChapterState ch = state.getChapters().get(idx);

        String excerpt = content;
        int runeCount = excerpt.codePointCount(0, excerpt.length());
        if (runeCount > 1200) {
            int[] cps = excerpt.codePoints().toArray();
            excerpt = new String(cps, 0, 600) + "\n...\n" + new String(cps, cps.length - 600, 600);
        }

        String foreshadowBlock = foreshadowService.formatActiveForeshadowsForChapter(
                state.getForeshadows(), ch.getNum());
        String outlineConstraints = buildOutlineConstraints(state, idx, lang);

        String userPrompt = ForeshadowService.renderPrompt(cfg.getPrompts().getWritingConflictAnalysis(), Map.of(
                "ChapterNum", String.valueOf(ch.getNum()),
                "ChapterTitle", ch.getTitle() != null ? ch.getTitle() : "",
                "ChapterOutline", ch.getOutline() != null ? ch.getOutline() : "",
                "HistorySummary", ForeshadowService.buildHistorySummary(state, idx, lang),
                "OutlineConstraints", outlineConstraints,
                "Foreshadows", foreshadowBlock,
                "FailedIssues", String.join("\n", failedIssues),
                "ContentExcerpt", excerpt
        ));

        String systemPrompt = LocaleHelper.systemPromptFor(lang, "writing_conflict_analyst_json");
        String rawResp = openAIClient.callAPIWithRetry(ct, apiCfg, systemPrompt, userPrompt, logBroadcaster, null);
        if (rawResp == null || rawResp.isEmpty()) {
            throw new RuntimeException("API 调用失败或被取消");
        }

        String jsonStr = ForeshadowConsistencyService.extractJSON(ForeshadowService.cleanJSONResponse(rawResp));
        if (jsonStr == null || jsonStr.isEmpty()) {
            throw new RuntimeException("无法解析写作冲突分析结果");
        }

        WritingConflictAnalysis analysis = MAPPER.readValue(jsonStr, WritingConflictAnalysis.class);
        analysis.setSuggestedActions(ensureConflictActions(analysis.getSuggestedActions(), lang));
        return analysis;
    }

    /**
     * Build a WritingConflict from analysis results.
     */
    public WritingConflict buildWritingConflict(Progress state, int idx, List<String> failedIssues,
                                                 WritingConflictAnalysis analysis) {
        ChapterState ch = state.getChapters().get(idx);
        String summary = "事实核查多次失败，需人工决定修改方向";
        String rootCause = "other";
        boolean reconcilable = false;
        List<ConflictActionOption> actions = ensureConflictActions(null, "zh");

        if (analysis != null) {
            if (analysis.getSummary() != null && !analysis.getSummary().trim().isEmpty()) {
                summary = analysis.getSummary().trim();
            }
            rootCause = analysis.getRootCause() != null ? analysis.getRootCause() : rootCause;
            reconcilable = analysis.isReconcilable();
            if (analysis.getSuggestedActions() != null && !analysis.getSuggestedActions().isEmpty()) {
                actions = analysis.getSuggestedActions();
            }
        }

        WritingConflict conflict = new WritingConflict();
        conflict.setChapterIndex(idx);
        conflict.setChapterNum(ch.getNum());
        conflict.setChapterTitle(ch.getTitle());
        conflict.setIssues(failedIssues);
        conflict.setSummary(summary);
        conflict.setRootCause(rootCause);
        conflict.setReconcilable(reconcilable);
        conflict.setSuggestedActions(actions);
        return conflict;
    }

    /**
     * Ensure default conflict actions are present.
     */
    public List<ConflictActionOption> ensureConflictActions(List<ConflictActionOption> actions, String lang) {
        boolean en = "en".equals(LocaleHelper.normalizeLanguage(lang));
        Map<String, ConflictActionOption> byID = new HashMap<>();
        if (actions != null) {
            for (ConflictActionOption a : actions) {
                if (a.getId() != null && !a.getId().isEmpty()) {
                    byID.put(a.getId(), a);
                }
            }
        }

        List<ConflictActionOption> defaults = List.of(
                new ConflictActionOption("edit_outline",
                        en ? "Edit chapter outline" : "修改本章大纲",
                        en ? "Adjust this or later chapter outlines on the Outline page"
                                : "在大纲页调整本章或后续章节大纲，使情节与伏笔/前情一致"),
                new ConflictActionOption("adjust_foreshadow",
                        en ? "Adjust foreshadows" : "调整伏笔",
                        en ? "Change plant/payoff chapters or descriptions on the Foreshadows page"
                                : "在伏笔页修改埋设/回收章节或描述，或放弃无法实现的伏笔"),
                new ConflictActionOption("retry",
                        en ? "Retry after edits" : "修改后重试生成",
                        en ? "Regenerate this chapter after you have edited the outline or foreshadows"
                                : "完成大纲或伏笔调整后，重新生成本章"),
                new ConflictActionOption("force_review",
                        en ? "Keep draft for review" : "保留当前稿进入审核",
                        en ? "Accept the current draft and review it manually"
                                : "接受当前版本，进入人工审核后再决定修订或确认")
        );

        List<ConflictActionOption> out = new ArrayList<>();
        for (ConflictActionOption d : defaults) {
            ConflictActionOption a = byID.get(d.getId());
            if (a != null) {
                if (a.getLabel() == null || a.getLabel().isEmpty()) a.setLabel(d.getLabel());
                if (a.getDescription() == null || a.getDescription().isEmpty()) a.setDescription(d.getDescription());
                out.add(a);
            } else {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Merge unique issues from multiple lists.
     */
    public static List<String> mergeUniqueIssues(List<String>... issueLists) {
        Set<String> seen = new LinkedHashSet<>();
        for (List<String> list : issueLists) {
            if (list == null) continue;
            for (String item : list) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    seen.add(trimmed);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Split fact-check issues string into individual items.
     */
    public static List<String> splitFactCheckIssues(String issues) {
        if (issues == null || issues.trim().isEmpty()) return new ArrayList<>();
        String[] parts = issues.split("；");
        if (parts.length == 1) {
            parts = issues.split(";");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) unique.add(trimmed);
        }
        return new ArrayList<>(unique);
    }

    private String buildOutlineConstraints(Progress state, int idx, String lang) {
        boolean en = "en".equals(LocaleHelper.normalizeLanguage(lang));
        StringBuilder sb = new StringBuilder();
        ChapterState ch = state.getChapters().get(idx);

        if (ch.getOutline() != null && !ch.getOutline().isEmpty()) {
            sb.append(en ? "Current chapter outline:\n" : "本章大纲:\n");
            sb.append(ch.getOutline()).append("\n\n");
        }

        // Future chapters outline (window of 10)
        int futureWindow = 10;
        StringBuilder futureOutline = new StringBuilder();
        for (int i = idx + 1; i < state.getChapters().size() && i <= idx + futureWindow; i++) {
            ChapterState fc = state.getChapters().get(i);
            futureOutline.append(ForeshadowService.formatChapterLine(fc, lang));
        }
        if (!futureOutline.isEmpty()) {
            sb.append(en ? "Upcoming chapters outline:\n" : "后续章节大纲:\n");
            sb.append(futureOutline).append("\n");
        }

        return sb.toString();
    }
}
