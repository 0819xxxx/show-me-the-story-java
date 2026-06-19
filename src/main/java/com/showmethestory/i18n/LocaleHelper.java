package com.showmethestory.i18n;

import java.util.Map;

/**
 * Localisation helper: language normalisation, bilingual system prompts,
 * error messages, and general message resolution.
 * <p>
 * Mirrors Go's locale.go: SystemPromptFor, T, NormalizeLanguage.
 */
public final class LocaleHelper {

    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private LocaleHelper() {}

    // ---------------------------------------------------------------
    // Language normalisation
    // ---------------------------------------------------------------

    /**
     * Normalise a language string to "zh" or "en".
     * Unknown values fall back to "zh".
     */
    public static String normalizeLanguage(String lang) {
        if (lang == null) return LANG_ZH;
        switch (lang.trim()) {
            case LANG_EN:
            case "en-US":
            case "en-GB":
                return LANG_EN;
            default:
                return LANG_ZH;
        }
    }

    // ---------------------------------------------------------------
    // System prompts (bilingual AI role descriptions)
    // ---------------------------------------------------------------

    /**
     * Bilingual system prompt catalog: key -> {zh, en}.
     */
    private static final Map<String, String[]> SYSTEM_PROMPTS = Map.ofEntries(
            entry("outline_editor_json",
                    "你是一位专业的小说策划编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。",
                    "You are a professional novel-planning editor. Output strict JSON exactly as requested — no extra prose, no markdown code fences."),
            entry("outline_editor_locked_json",
                    "你是一位小说策划编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。已锁定的章节内容不可修改。",
                    "You are a novel-planning editor. Output strict JSON exactly as requested — no extra prose, no markdown code fences. Locked chapters may not be modified."),
            entry("outline_editor_brief_json",
                    "你是一位严谨的小说策划编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字。",
                    "You are a strict novel-planning editor. Output strict JSON exactly as requested — no extra prose."),
            entry("summary_analyst",
                    "你是一位精准的小说叙事状态分析师。",
                    "You are a precise novel narrative-state analyst."),
            entry("fact_checker_json",
                    "你是一位严谨的小说事实核查员。请严格按照要求的JSON格式输出。",
                    "You are a strict novel fact-checker. Output strict JSON exactly as requested."),
            entry("narrative_architect_json",
                    "你是一位资深的小说叙事架构师。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。",
                    "You are a senior narrative architect. Output strict JSON exactly as requested — no extra prose, no markdown code fences."),
            entry("foreshadow_tracker_json",
                    "你是一位严谨的小说伏笔追踪员。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。",
                    "You are a strict novel foreshadow tracker. Output strict JSON exactly as requested — no extra prose, no markdown code fences."),
            entry("foreshadow_outline_checker_json",
                    "你是一位严谨的小说叙事一致性编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字。拿不准时视为无冲突。",
                    "You are a strict narrative-consistency editor. Output strict JSON exactly as requested — no extra prose. When unsure, treat as no conflict."),
            entry("writing_conflict_analyst_json",
                    "你是一位资深小说编辑，擅长诊断大纲、伏笔与前情之间的矛盾。请严格按照要求的JSON格式输出，不要添加任何额外文字。",
                    "You are a senior novel editor who diagnoses contradictions among outlines, foreshadows, and prior story. Output strict JSON exactly as requested — no extra prose."),
            entry("consistency_reviewer_json",
                    "你是一位专业的小说一致性审查编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。",
                    "You are a professional novel-consistency reviewer. Output strict JSON exactly as requested — no extra prose, no markdown code fences."),
            entry("content_analyst_json",
                    "你是一位专业的小说分析编辑。请严格按照要求的JSON格式输出，不要添加任何额外文字或markdown代码块标记。",
                    "You are a professional novel-analysis editor. Output strict JSON exactly as requested — no extra prose, no markdown code fences."),
            entry("transition_editor",
                    "你是一位资深小说编辑，擅长打磨章节之间的衔接。请严格按要求输出。",
                    "You are a senior novel editor specialising in chapter-to-chapter transitions. Follow the output instructions strictly."),
            entry("polish_editor",
                    "你是一位专业的中文小说润色编辑。请严格按照规则修改文本，输出修改后的完整章节正文。不要添加章节标题、章节号、「本章完」等任何解释、标记或元信息。",
                    "You are a professional novel-polish editor. Apply the rules strictly and output the full revised chapter prose. No chapter titles, numbers, meta lines like \"End of chapter\", explanations, or markers."),
            entry("book_diagnosis",
                    "你是一位资深网文总编辑，擅长长篇完稿后的通读审阅。请严格按要求输出诊断报告，不要改写正文。",
                    "You are a senior editor-in-chief specialising in full-novel post-completion review. Output the diagnostic report strictly per the requested format — do not rewrite the prose."),
            entry("book_consistency_check",
                    "你是一位严谨的小说事实核查员。请输出结构化核查报告，不要改写正文。",
                    "You are a strict novel fact-checker. Output a structured consistency report — do not rewrite the prose."),
            entry("book_roadmap",
                    "你是一位资深小说编辑。请根据报告生成可执行的修改工单 JSON，不要输出正文改写。",
                    "You are a senior novel editor. Produce an executable revision-roadmap JSON from the reports — do not output rewritten prose."),
            entry("author_default",
                    "你是一位小说作者。只输出小说正文，不要输出章节标题、章节号、作者说明或「本章完」等元信息。严格保持用户指定的叙述视角统一。",
                    "You are a novelist. Output story prose only — no chapter titles, numbers, author notes, or meta lines like \"End of chapter\". Keep the specified narrative POV consistent throughout."),
            entry("chapter_revision_suffix",
                    "\n你正在执行章节修订任务：只做修改意见要求的改动，其余原文保持不变，输出修改后的完整正文；不要添加任何元信息或说明性文字。",
                    "\nYou are performing a chapter revision: make only the changes the feedback requires; leave everything else identical; output the full revised prose with no meta or explanatory text.")
    );

    /**
     * Return the AI system prompt for the given key and language.
     * Falls back to zh if the key or language is not found.
     */
    public static String systemPromptFor(String lang, String key) {
        lang = normalizeLanguage(lang);
        String[] pair = SYSTEM_PROMPTS.get(key);
        if (pair == null) return "";
        int idx = LANG_EN.equals(lang) ? 1 : 0;
        String val = pair[idx];
        if (val != null && !val.isEmpty()) return val;
        return pair[0]; // fallback to zh
    }

    // ---------------------------------------------------------------
    // Error messages
    // ---------------------------------------------------------------

    /**
     * Return a localised error message for the given key.
     * Arguments are formatted via String.format.
     */
    public static String errorMsg(String lang, String key, Object... args) {
        return t(lang, key, args);
    }

    // ---------------------------------------------------------------
    // General message resolution
    // ---------------------------------------------------------------

    /**
     * Return a localised message for the given key and args.
     * Looks up both Messages.messageCatalog and the error catalog.
     * Falls back to zh, then the raw key.
     */
    public static String t(String lang, String key, Object... args) {
        lang = normalizeLanguage(lang);
        int idx = LANG_EN.equals(lang) ? 1 : 0;

        // Search Messages catalog first
        String[] pair = Messages.messageCatalog.get(key);
        if (pair == null) {
            pair = Messages.errorCatalog.get(key);
        }
        if (pair == null) return key;

        String tpl = pair[idx];
        if (tpl == null || tpl.isEmpty()) tpl = pair[0]; // fallback zh
        if (tpl == null || tpl.isEmpty()) return key;

        if (args == null || args.length == 0) return tpl;

        // Convert Go-style %s/%d/%v to Java format
        String javaFmt = tpl
                .replace("%v", "%s")
                .replace("%d", "%s");
        try {
            return String.format(javaFmt, args);
        } catch (Exception e) {
            return tpl;
        }
    }

    /**
     * Shortcut: getMessage delegates to t().
     */
    public static String getMessage(String lang, String key, Object... args) {
        return t(lang, key, args);
    }

    // ---------------------------------------------------------------
    // Internal helper
    // ---------------------------------------------------------------

    private static Map.Entry<String, String[]> entry(String key, String zh, String en) {
        return Map.entry(key, new String[]{zh, en});
    }
}
