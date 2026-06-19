package com.showmethestory.i18n;

import com.showmethestory.model.Character;
import com.showmethestory.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Language-aware builders that produce context blocks injected into
 * prompt templates at runtime. Each builder takes project state and
 * returns a localised text fragment.
 * <p>
 * Mirrors Go's i18n_inject.go.
 */
public final class I18nContextBuilder {

    /** Maximum runes to include from the previous chapter's tail. */
    private static final int PREV_TAIL_MAX_RUNES = 800;

    /** Window of future chapters to include in outline constraints. */
    private static final int FUTURE_OUTLINE_WINDOW = 10;

    private I18nContextBuilder() {}

    // ---------------------------------------------------------------
    // Outline constraints (reverse constraint block)
    // ---------------------------------------------------------------

    /**
     * Build the "full-novel chapter arc" reverse-constraint block.
     * Shows past chapters that already happened and upcoming chapters
     * whose events must not be pre-empted.
     */
    public static String buildOutlineConstraints(Progress state, int idx, String lang) {
        if (state == null || state.getChapters() == null) return "";
        List<ChapterState> chapters = state.getChapters();
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        StringBuilder past = new StringBuilder();
        StringBuilder future = new StringBuilder();

        // Past chapters (before idx)
        for (int i = 0; i < idx && i < chapters.size(); i++) {
            ChapterState ch = chapters.get(i);
            if (ch.getOutline() != null && !ch.getOutline().trim().isEmpty()) {
                past.append(formatChapterLine(ch.getNum(), ch.getTitle(), ch.getOutline(), lang));
            }
        }

        // Future chapters (idx+1 to idx+1+window)
        int end = Math.min(idx + 1 + FUTURE_OUTLINE_WINDOW, chapters.size());
        for (int i = idx + 1; i < end; i++) {
            ChapterState ch = chapters.get(i);
            if (ch.getOutline() != null && !ch.getOutline().trim().isEmpty()) {
                future.append(formatChapterLine(ch.getNum(), ch.getTitle(), ch.getOutline(), lang));
            }
        }

        if (past.length() == 0 && future.length() == 0) return "";

        StringBuilder sb = new StringBuilder();
        if (en) {
            sb.append("[Full-novel chapter arc (reverse constraint, must be obeyed strictly)]\n");
            if (future.length() > 0) {
                sb.append("- Upcoming chapters — the following character debuts, first meetings, identity reveals etc. are already assigned to specific later chapters. This chapter MUST NOT make them happen early, nor hint at or spoil them:\n");
                sb.append(future);
            }
            if (past.length() > 0) {
                sb.append("- Already happened — the events below have already occurred. This chapter must not re-enact them as new events (especially one-time events like first meetings or identity reveals — only continue them as established facts):\n");
                sb.append(past);
            }
        } else {
            sb.append("【全书章节脉络（反向约束，必须严格遵守）】\n");
            if (future.length() > 0) {
                sb.append("◆ 后续章节安排——以下人物登场、初遇、身份揭示等事件已安排在对应章节，本章严禁提前发生，也不得以任何形式暗示或剧透：\n");
                sb.append(future);
            }
            if (past.length() > 0) {
                sb.append("◆ 前文已发生——以下事件已经发生，本章不得将其作为新事件重复发生（尤其是初次见面、身份揭示等一次性事件，只能作为既成事实延续）：\n");
                sb.append(past);
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatChapterLine(int num, String title, String outline, String lang) {
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            return String.format("Chapter %d \"%s\": %s\n", num, title, outline);
        }
        return String.format("第%d章《%s》：%s\n", num, title, outline);
    }

    // ---------------------------------------------------------------
    // Previous chapter tail
    // ---------------------------------------------------------------

    /**
     * Build the previous chapter's ending text for seamless scene continuation.
     */
    public static String buildPreviousChapterTail(Progress state, int idx, String lang) {
        if (state == null || state.getChapters() == null) return "";
        if (idx <= 0 || idx >= state.getChapters().size()) return "";
        ChapterState prev = state.getChapters().get(idx - 1);
        if (prev.getContent() == null || prev.getContent().isEmpty()) return "";

        String tail = tailAtParagraph(prev.getContent(), PREV_TAIL_MAX_RUNES);
        if (tail.isEmpty()) return "";

        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));
        if (en) {
            return String.format("[Previous chapter ending (for seamless scene/mood continuation only — do NOT recap or rewrite)]\n%s\n\n", tail);
        }
        return String.format("【上一章结尾原文（仅供无缝承接场景与情绪，禁止复述或改写）】\n%s\n\n", tail);
    }

    /**
     * Take the last ~maxRunes characters of content, aligning to paragraph boundary.
     */
    static String tailAtParagraph(String content, int maxRunes) {
        String trimmed = content.trim();
        int[] codePoints = trimmed.codePoints().toArray();
        if (codePoints.length <= maxRunes) return trimmed;

        // Take the tail
        String tail = new String(codePoints, codePoints.length - maxRunes, maxRunes);
        int nlIdx = tail.indexOf('\n');
        if (nlIdx >= 0 && nlIdx + 1 < tail.length()) {
            tail = tail.substring(nlIdx + 1);
        }
        return tail.trim();
    }

    // ---------------------------------------------------------------
    // History summary
    // ---------------------------------------------------------------

    /**
     * Build a rolling summary of recent chapters (up to 5 before idx).
     */
    public static String buildHistorySummary(Progress state, int idx, String lang) {
        if (state == null || state.getChapters() == null) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        int startIdx = Math.max(0, idx - 5);
        StringBuilder history = new StringBuilder();
        for (int i = startIdx; i < idx; i++) {
            ChapterState ch = state.getChapters().get(i);
            if (ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                if (en) {
                    history.append(String.format("[Chapter %d summary]: %s\n", ch.getNum(), ch.getSummary()));
                } else {
                    history.append(String.format("[第%d章摘要]: %s\n", ch.getNum(), ch.getSummary()));
                }
            }
        }
        if (history.length() == 0) {
            return en
                    ? "This is the opening of the story; no prior context."
                    : "当前为故事开端，无历史前情。";
        }
        return history.toString();
    }

    // ---------------------------------------------------------------
    // Character context
    // ---------------------------------------------------------------

    /**
     * Build structured character details for injection into writing prompts.
     * Filters to characters mentioned in the chapter outline, or shows all
     * if none match.
     */
    public static String buildCharacterContext(ProjectSettings settings, String chapterOutline, String lang) {
        if (settings == null || settings.getCharacters() == null || settings.getCharacters().isEmpty()) {
            return "";
        }
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        List<Character> relevant = new ArrayList<>();
        for (Character c : settings.getCharacters()) {
            if (chapterOutline != null && chapterOutline.contains(c.getName())) {
                relevant.add(c);
            }
        }
        if (relevant.isEmpty()) relevant = settings.getCharacters();

        StringBuilder sb = new StringBuilder();
        for (Character c : relevant) {
            sb.append(String.format("【%s】", c.getName()));
            if (c.getAge() != null && !c.getAge().isEmpty()) {
                sb.append(en
                        ? String.format(" Age: %s", c.getAge())
                        : String.format(" 年龄:%s", c.getAge()));
            }
            sb.append("\n");

            if (en) {
                writeField(sb, "Appearance", c.getAppearance());
                writeField(sb, "Personality", c.getPersonality());
                writeField(sb, "Background", c.getBackground());
                writeField(sb, "Motivation", c.getMotivation());
                writeField(sb, "Abilities", c.getAbilities());
                writeField(sb, "Notes", c.getNotes());
            } else {
                writeField(sb, "外貌", c.getAppearance());
                writeField(sb, "性格", c.getPersonality());
                writeField(sb, "背景", c.getBackground());
                writeField(sb, "动机", c.getMotivation());
                writeField(sb, "能力", c.getAbilities());
                writeField(sb, "备注", c.getNotes());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void writeField(StringBuilder sb, String label, String val) {
        if (val != null && !val.isEmpty()) {
            sb.append(String.format("  %s: %s\n", label, val));
        }
    }

    // ---------------------------------------------------------------
    // Worldview context
    // ---------------------------------------------------------------

    /**
     * Build worldview and organization context for injection into writing prompts.
     */
    public static String buildWorldviewContext(ProjectSettings settings, String chapterOutline, String lang) {
        if (settings == null) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        StringBuilder sb = new StringBuilder();

        // Worldview entries
        if (settings.getWorldview() != null && !settings.getWorldview().isEmpty()) {
            List<WorldviewEntry> relevant = new ArrayList<>();
            for (WorldviewEntry w : settings.getWorldview()) {
                if (chapterOutline != null
                        && (chapterOutline.contains(w.getName())
                            || (w.getCategory() != null && chapterOutline.contains(w.getCategory())))) {
                    relevant.add(w);
                }
            }
            if (relevant.isEmpty()) relevant = settings.getWorldview();

            for (WorldviewEntry w : relevant) {
                sb.append(String.format("【%s】(%s)\n  %s\n\n",
                        w.getName(), w.getCategory(), w.getDescription()));
            }
        }

        // Organizations
        if (settings.getOrganizations() != null && !settings.getOrganizations().isEmpty()) {
            List<Organization> relevantOrgs = new ArrayList<>();
            for (Organization o : settings.getOrganizations()) {
                if (chapterOutline != null && chapterOutline.contains(o.getName())) {
                    relevantOrgs.add(o);
                }
            }
            if (relevantOrgs.isEmpty()) relevantOrgs = settings.getOrganizations();

            for (Organization o : relevantOrgs) {
                if (en) {
                    sb.append(String.format("[Organization: %s] (%s)\n  %s\n",
                            o.getName(), o.getType(), o.getDescription()));
                    if (o.getMembers() != null && !o.getMembers().isEmpty()) {
                        sb.append(String.format("  Member IDs: %s\n", String.join(", ", o.getMembers())));
                    }
                } else {
                    sb.append(String.format("【组织:%s】(%s)\n  %s\n",
                            o.getName(), o.getType(), o.getDescription()));
                    if (o.getMembers() != null && !o.getMembers().isEmpty()) {
                        sb.append(String.format("  成员IDs: %s\n", String.join(", ", o.getMembers())));
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Foreshadow formatting
    // ---------------------------------------------------------------

    /**
     * Render the "active foreshadows" block for a specific chapter.
     * Shows planted/progressing foreshadows with overdue warnings.
     */
    public static String formatActiveForeshadows(List<Foreshadow> foreshadows, int chapterNum, String lang) {
        if (foreshadows == null || foreshadows.isEmpty()) return "";
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        List<Foreshadow> active = new ArrayList<>();
        List<Foreshadow> overdue = new ArrayList<>();

        for (Foreshadow fs : foreshadows) {
            String status = fs.getStatus();
            if (Foreshadow.STATUS_PLANTED.equals(status) || Foreshadow.STATUS_PROGRESSING.equals(status)) {
                active.add(fs);
                if (fs.getTargetChapter() > 0 && chapterNum >= fs.getTargetChapter()) {
                    overdue.add(fs);
                }
            }
        }

        if (active.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(en
                ? "[Active foreshadows (you must advance or pay them off when writing)]\n"
                : "【活跃伏笔（写作时必须注意推进或回收）】\n");

        for (Foreshadow fs : active) {
            if (en) {
                sb.append(String.format("#%d \"%s\" [planted in chapter %d", fs.getId(), fs.getName(), fs.getPlantChapter()));
                if (fs.getTargetChapter() > 0) {
                    sb.append(String.format(", expected payoff chapter %d", fs.getTargetChapter()));
                }
                sb.append("]\n");
                sb.append(String.format("   Description: %s\n", fs.getDescription()));
            } else {
                sb.append(String.format("#%d \"%s\" [第%d章埋设", fs.getId(), fs.getName(), fs.getPlantChapter()));
                if (fs.getTargetChapter() > 0) {
                    sb.append(String.format("，预计第%d章回收", fs.getTargetChapter()));
                }
                sb.append("]\n");
                sb.append(String.format("   描述: %s\n", fs.getDescription()));
            }

            // Events / progress
            if (fs.getEvents() != null && !fs.getEvents().isEmpty()) {
                sb.append(en ? "   Progress so far:\n" : "   已有进展:\n");
                for (ForeshadowEvent ev : fs.getEvents()) {
                    if (en) {
                        sb.append(String.format("   - Chapter %d: %s\n", ev.getChapter(), ev.getNote()));
                    } else {
                        sb.append(String.format("   - 第%d章: %s\n", ev.getChapter(), ev.getNote()));
                    }
                }
            }

            // Overdue / approaching warning
            boolean isOverdue = overdue.contains(fs);
            if (isOverdue) {
                if (en) {
                    sb.append(String.format("   ⚠️ This foreshadow is past its expected payoff chapter (%d); this chapter should prioritise paying it off.\n", fs.getTargetChapter()));
                } else {
                    sb.append(String.format("   ⚠️ 该伏笔已超过预计回收章节（第%d章），本章应优先考虑回收\n", fs.getTargetChapter()));
                }
            } else if (fs.getTargetChapter() > 0 && chapterNum >= fs.getTargetChapter() - 2) {
                if (en) {
                    sb.append(String.format("   → Approaching the expected payoff (chapter %d); start closing it.\n", fs.getTargetChapter()));
                } else {
                    sb.append(String.format("   → 接近预计回收节点（第%d章），可开始收束\n", fs.getTargetChapter()));
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Render the full foreshadow list for the update tracker prompt.
     */
    public static String formatForeshadowsForPrompt(List<Foreshadow> foreshadows, String lang) {
        if (foreshadows == null || foreshadows.isEmpty()) {
            return LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang)) ? "(none)" : "无";
        }
        boolean en = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang));

        StringBuilder sb = new StringBuilder();
        for (Foreshadow fs : foreshadows) {
            sb.append(String.format("#%d [%s] %s\n", fs.getId(), fs.getStatus(), fs.getName()));
            if (en) {
                sb.append(String.format("   Description: %s\n", fs.getDescription()));
                sb.append(String.format("   Planted at: chapter %d", fs.getPlantChapter()));
                if (fs.getTargetChapter() > 0) {
                    sb.append(String.format(", expected payoff: chapter %d", fs.getTargetChapter()));
                }
            } else {
                sb.append(String.format("   描述: %s\n", fs.getDescription()));
                sb.append(String.format("   埋设于: 第%d章", fs.getPlantChapter()));
                if (fs.getTargetChapter() > 0) {
                    sb.append(String.format("，预计回收: 第%d章", fs.getTargetChapter()));
                }
            }
            sb.append("\n");

            if (fs.getEvents() != null && !fs.getEvents().isEmpty()) {
                sb.append(en ? "   Progress so far:\n" : "   已有进展:\n");
                for (ForeshadowEvent ev : fs.getEvents()) {
                    if (en) {
                        sb.append(String.format("   - Chapter %d: %s\n", ev.getChapter(), ev.getNote()));
                    } else {
                        sb.append(String.format("   - 第%d章: %s\n", ev.getChapter(), ev.getNote()));
                    }
                }
            }

            if (fs.getResolution() != null && !fs.getResolution().isEmpty()) {
                sb.append(en
                        ? String.format("   Resolution: %s\n", fs.getResolution())
                        : String.format("   回收方式: %s\n", fs.getResolution()));
            }

            sb.append("\n");
        }
        return sb.toString();
    }
}
