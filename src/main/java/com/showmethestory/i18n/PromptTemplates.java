package com.showmethestory.i18n;

import com.showmethestory.model.PromptsConfig;

/**
 * Stores all default prompt templates for both languages.
 * Mirrors Go's DefaultPromptsZH (prompts.go) and DefaultPromptsEN (prompts_en.go).
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    // ================================================================
    // Chinese defaults (18 prompts) – from prompts.go
    // ================================================================
    private static final PromptsConfig DEFAULT_ZH = buildZH();

    // ================================================================
    // English defaults (18 prompts) – from prompts_en.go
    // ================================================================
    private static final PromptsConfig DEFAULT_EN = buildEN();

    /**
     * Return the default PromptsConfig for the given language.
     */
    public static PromptsConfig getDefaults(String lang) {
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(lang))) {
            return copy(DEFAULT_EN);
        }
        return copy(DEFAULT_ZH);
    }

    // ---------------------------------------------------------------
    // Copy helper (so callers get their own mutable instance)
    // ---------------------------------------------------------------

    private static PromptsConfig copy(PromptsConfig src) {
        PromptsConfig c = new PromptsConfig();
        c.setOutlineGeneration(src.getOutlineGeneration());
        c.setChapterWriting(src.getChapterWriting());
        c.setChapterRevision(src.getChapterRevision());
        c.setChapterSummary(src.getChapterSummary());
        c.setFactCheck(src.getFactCheck());
        c.setOutlineRevision(src.getOutlineRevision());
        c.setForeshadowPlanning(src.getForeshadowPlanning());
        c.setForeshadowUpdate(src.getForeshadowUpdate());
        c.setContentAnalysis(src.getContentAnalysis());
        c.setContinuationOutlineGeneration(src.getContinuationOutlineGeneration());
        c.setSettingsReconciliation(src.getSettingsReconciliation());
        c.setTransitionSmoothing(src.getTransitionSmoothing());
        c.setOutlineConsistencyCheck(src.getOutlineConsistencyCheck());
        c.setForeshadowOutlineConsistency(src.getForeshadowOutlineConsistency());
        c.setWritingConflictAnalysis(src.getWritingConflictAnalysis());
        c.setBookDiagnosis(src.getBookDiagnosis());
        c.setBookConsistencyCheck(src.getBookConsistencyCheck());
        c.setBookRoadmap(src.getBookRoadmap());
        return c;
    }

    // ================================================================
    // Build Chinese defaults
    // ================================================================

    private static PromptsConfig buildZH() {
        PromptsConfig p = new PromptsConfig();

        p.setOutlineGeneration("""
你是一位专业的小说策划编辑。请根据以下约束生成小说大纲。

请以JSON格式返回，结构如下：
{
  "title": "小说标题",
  "core_prompt": "核心写作提示词（用于指导后续各章创作的系统级提示）",
  "story_synopsis": "故事梗概",
  "chapters": [
    {"num": 1, "title": "章节标题", "outline": "本章大纲"},
    ...
  ]
}

【故事类型】{{.StoryType}}
【章节数量】{{.ChapterCount}}
【每章字数】{{.TargetWords}}
【写作风格】{{.WritingStyle}}
【叙述视角】{{.WritingPOV}}
【故事梗概】{{.StorySynopsis}}

注意：
1. 大纲需要覆盖完整的故事弧线，从开端到结局
2. 每章大纲应包含具体的情节发展，而非笼统的描述
3. 每章大纲需明确列出本章出场人物；新人物在其首次出现的章节标注「首次登场」，并确保该人物不会出现在更早的章节中
4. 初遇、身份揭示等一次性事件只能安排在一个章节中发生，避免重复
5. core_prompt 应包含指导整部小说写作的核心提示词，包括写作风格与叙述视角，并明确要求全书视角统一
6. 请严格以JSON格式输出，不要添加任何额外文字""");

        p.setChapterWriting("""
请为小说《{{.Title}}》创作第 {{.ChapterNum}} 章的正文。

【核心写作提示词】
{{.CorePrompt}}

【故事梗概】
{{.StorySynopsis}}

【前情提要（滚动最近章节进展，请严格承接状态）】
{{.HistorySummary}}

{{.PreviousEnding}}{{.Foreshadows}}{{.OutlineConstraints}}【本章创作任务】
章节标题：《{{.ChapterTitle}}》
核心大纲：{{.ChapterOutline}}

【写作风格】{{.WritingStyle}}
【叙述视角】{{.WritingPOV}}
{{.CharacterContext}}
{{.WorldviewContext}}
创作要求：
1. 严格承接前情提要中的人物状态、时间线和已发生事件，不得与之矛盾
2. 只写本章大纲范围内的情节，不要提前透支后续章节的内容
3. 严禁让按章节脉络安排在后续章节才登场或发生的人物、初遇、身份揭示等事件提前出现，也不得以任何形式暗示或剧透
4. 前文已发生的一次性事件（初次见面、身份揭示、关系确立等）只能作为既成事实延续，绝不能在本章重新发生一遍
5. 不要复述前情，开篇直接进入本章场景；若提供了上一章结尾原文，开头必须自然承接其场景、时间与情绪，不要重新铺垫已有内容
6. 人物对话要符合各自的性格设定，避免所有角色说话腔调雷同
7. 多用具体的动作、感官细节和对话推进情节，少用抽象的总结性叙述
8. 章节结尾留出自然的悬念或情绪钩子，但不要写"欲知后事如何"之类的套话
9. 全书叙述视角必须严格统一：按【叙述视角】要求写作，不得擅自切换人称或视角主体（若设定为交替视角，须按既定规则切换）
10. 字数 {{.TargetWords}} 字左右
11. 只输出小说正文：禁止出现章节标题、章节号、大纲复述、作者说明、分隔线，以及「第X章」「（第X章正文）」「本章完」「待续」「以下为修订后的第X章完整正文」「以下是第X章」等任何元信息或说明性文字。正文前不要有任何引导语，正文后不要有任何总结语""");

        p.setChapterRevision("""
你是这部小说的作者，现在需要根据修改意见修订第 {{.ChapterNum}} 章《{{.ChapterTitle}}》。

【核心写作提示词】
{{.CorePrompt}}

【前情提要】
{{.HistorySummary}}

【写作风格】{{.WritingStyle}}
【叙述视角】{{.WritingPOV}}
{{.CharacterContext}}
{{.WorldviewContext}}
【本章原文】
{{.OriginalContent}}

【修改意见】
{{.UserFeedback}}

修订要求（必须严格遵守）：
1. 这是"修订"而不是"重写"：仅针对修改意见涉及的部分做必要修改，其余内容保持原文不变（包括措辞、段落结构）
2. 修改后必须与前情提要及未修改部分保持事实一致（人名、时间线、设定）
3. 不要改变本章的整体情节走向，除非修改意见明确要求
4. 全书叙述视角必须严格统一：按【叙述视角】要求写作，不得擅自切换人称或视角主体
5. 输出修改后的完整章节正文（包含未修改的部分）：禁止出现章节标题、章节号、作者说明、分隔线，以及「第X章」「（第X章正文）」「本章完」「待续」「以下为修订后的第X章完整正文」「以下是第X章」等任何元信息或说明性文字。正文前不要有任何引导语，正文后不要有任何总结语""");

        p.setChapterSummary("""
你是一位精准的小说叙事状态分析师，擅长从文学性文本中提取关键叙事要素和人物心理轨迹。你的摘要将作为后续章节创作的前情提要，因此必须保留可延续的状态信息。

请将以下章节压缩为结构化摘要（总字数控制在250字以内）。

请严格按以下格式输出：

【本章核心】一句话概括本章发生了什么（或主角处于什么状态）。
【人物动态】本章出场人物及其关系进展，特别标注初次见面、身份揭示、关系确立等一次性事件（如"A与B初次相识"），无则写"无新进展"。
【心理轨迹】主角当前的心理状态、情绪基调、有无关键的心理转折点。
【状态变化】本章相比上一章，主角在外在（外貌/穿着/行为）或内在（态度/认知）上发生了什么具体变化。如无明显变化则写"延续上章状态"。
【关键细节】提取1-2个最具叙事延续价值的细节，后续章节可能会引用。
【情绪色调】用2-3个词概括本章的整体情绪氛围。

【章节正文】
{{.ChapterContent}}""");

        p.setFactCheck("""
你是一位严谨的小说事实核查员。你的任务是检查小说章节中的客观事实矛盾。

请核查以下小说章节与前情提要、章节脉络之间是否存在事实矛盾。

【前情提要】
{{.HistorySummary}}

【本章大纲】
{{.ChapterOutline}}

{{.OutlineConstraints}}【待核查章节】
{{.ChapterContent}}

核查范围（仅限以下客观矛盾，其他一概不算问题）：
1. 角色姓名、称呼前后不一致
2. 时间线倒错（如前文已是夜晚，本章无缘由地变回同日清晨）
3. 与前情明确矛盾的事实（如已死亡角色无解释地出现、已损毁物品完好如初）
4. 角色能力/身份与已确立设定直接冲突
5. 提前引入按章节脉络安排在后续章节才登场或发生的人物、初遇、身份揭示等事件
6. 前文已发生的一次性事件（初次见面、身份揭示等）在本章作为新事件重复发生

注意：
- 文风、节奏、详略取舍、剧情合理性等主观问题不属于事实错误，必须判 PASS
- 前情提要和章节脉络都未提及的新信息不算矛盾
- 只有确凿的客观矛盾才判 FAIL，拿不准时一律判 PASS

请以JSON格式返回（不要输出任何其他文字）：
{"result": "PASS", "issues": []}
或
{"result": "FAIL", "issues": ["具体矛盾描述1", "具体矛盾描述2"]}""");

        p.setOutlineRevision("""
你是一位小说策划编辑。用户对当前大纲提出了修改意见，请根据用户意见修订大纲。

【当前大纲】
{{.CurrentOutline}}

【用户意见】
{{.UserFeedback}}

【已确认章节（不可修改）】
{{.LockedChapters}}

请以JSON格式返回修订后的完整大纲：
{
  "title": "小说标题",
  "core_prompt": "核心写作提示词",
  "story_synopsis": "故事梗概",
  "chapters": [
    {"num": 1, "title": "章节标题", "outline": "本章大纲"},
    ...
  ]
}

注意：
1. 已锁定的章节内容不可修改，只能修改未锁定的章节
2. 保持章节总数和编号不变，除非用户意见明确要求增删章节
3. 与用户意见无关的章节保持原样返回，不要顺手改写
4. 请严格以JSON格式输出，不要添加任何额外文字""");

        p.setForeshadowPlanning("""
你是一位资深的小说叙事架构师，擅长设计伏笔系统。请根据以下小说大纲，设计一组伏笔（foreshadowing）方案。

【小说标题】{{.Title}}
【核心写作提示词】{{.CorePrompt}}
【故事梗概】{{.StorySynopsis}}

【完整大纲】
{{.Outline}}

请设计 3-8 条伏笔，遵循以下原则：
1. 伏笔应服务于故事主线和人物弧线，而非为了悬疑而悬疑
2. 每条伏笔应有明确的"埋设点"（在哪章埋下）和"回收点"（预计在哪章回收）
3. 伏笔之间可以相互关联，形成线索网络
4. 伏笔类型多样化：可以是物件、对话中的暗示、环境细节、人物行为的矛盾、未解释的现象等
5. 回收点应分散在不同章节，避免扎堆回收
6. 伏笔从第1章即可开始埋设，但大部分应在故事中段埋设、后半段回收

请以JSON格式返回：
{
  "foreshadows": [
    {
      "name": "伏笔简称（10字以内）",
      "description": "伏笔的详细描述：埋设方式、暗示内容、预期回收时读者应产生的'原来如此'的顿悟感",
      "plant_chapter": 埋设章节编号,
      "target_chapter": 预计回收章节编号
    }
  ]
}

请严格以JSON格式输出，不要添加任何额外文字。""");

        p.setForeshadowUpdate("""
你是一位严谨的小说伏笔追踪员。你的任务是根据最新完成的章节内容，更新伏笔系统的状态。

【小说标题】{{.Title}}

【当前伏笔列表】
{{.Foreshadows}}

【本章信息】
章节编号：第{{.ChapterNum}}章
章节标题：《{{.ChapterTitle}}》

【本章正文】
{{.ChapterContent}}

【前情提要】
{{.HistorySummary}}

请分析本章内容，判断每条伏笔在本章中的状态变化：

1. 如果伏笔在本章被首次提及/埋设，status 设为 "planted"
2. 如果伏笔在本章有新的线索/推进，status 设为 "progressing"
3. 如果伏笔在本章被完全揭示/回收，status 设为 "resolved"
4. 如果伏笔在本章没有出现，保持原状态不变
5. 注意区分"真正回收"和"仅仅是推进"——只有当伏笔的谜底被完全揭开时才算 resolved

请以JSON格式返回：
{
  "updates": [
    {
      "id": 伏笔ID,
      "status": "新状态（如果变化）",
      "event": "本章对该伏笔做了什么（如果有的话，一句话描述）",
      "resolution": "如果resolved，描述回收方式"
    }
  ]
}

只返回有变化的伏笔。如果某条伏笔在本章完全没有被提及，不要包含在返回结果中。
请严格以JSON格式输出，不要添加任何额外文字。""");

        p.setContentAnalysis("""
你是一位专业的小说分析编辑。请分析以下已有小说文本，提取故事元数据、为每章生成大纲和摘要。

请以JSON格式返回，结构如下：
{
  "title": "小说标题",
  "story_type": "故事类型（如：奇幻/都市/科幻/悬疑等）",
  "core_prompt": "核心写作提示词（用于指导后续各章创作的系统级提示）",
  "story_synopsis": "故事梗概",
  "writing_style": "写作风格描述",
  "writing_pov": "叙述视角描述（如：第三人称限知、第一人称女主视角、第一人称交替男女主视角等）",
  "chapters": [
    {
      "num": 1,
      "title": "章节标题",
      "outline": "本章内容概要（描述本章发生了什么，100-200字）",
      "summary": "结构化摘要（用于后续创作的前情提要，200字以内，包含核心事件、心理轨迹、状态变化、关键细节）"
    }
  ]
}

分析要求：
1. 从文本中识别章节边界（支持"第X章"、"# Chapter X"、空行分隔等常见格式）
2. 为每章生成：outline（本章内容概要）和 summary（用于后续创作的结构化摘要）
3. summary 需保留可延续的状态信息：核心事件、心理轨迹、关键细节、情绪色调
4. 提取故事元数据：故事类型、写作风格、叙述视角、角色设定、世界观设定
5. 生成 core_prompt 和 story_synopsis，用于指导后续创作

【已有小说文本】
{{.ExistingContent}}

请严格以JSON格式输出，不要添加任何额外文字。""");

        p.setContinuationOutlineGeneration("""
你是一位专业的小说策划编辑。请根据已有章节的大纲和摘要，为后续章节生成大纲。

【小说标题】{{.Title}}
【故事类型】{{.StoryType}}
【核心写作提示词】{{.CorePrompt}}
【故事梗概】{{.StorySynopsis}}
【写作风格】{{.WritingStyle}}
【叙述视角】{{.WritingPOV}}

【已有章节】
{{.ExistingOutline}}

请为后续 {{.NewChapterCount}} 章生成大纲，从第 {{.StartNum}} 章开始。

请以JSON格式返回：
{
  "chapters": [
    {"num": {{.StartNum}}, "title": "章节标题", "outline": "本章大纲"},
    ...
  ]
}

注意：
1. 大纲需要承接已有章节的故事线，保持连贯性
2. 每章大纲应包含具体的情节发展，而非笼统的描述
3. 每章大纲需明确列出本章出场人物；新人物在其首次出现的章节标注「首次登场」
4. 已有章节中发生过的初遇、身份揭示等一次性事件不得在新章节中重复安排
5. 请严格以JSON格式输出，不要添加任何额外文字""");

        p.setSettingsReconciliation("""
你是一位专业的小说一致性审查编辑。用户修改了故事设定，但已有部分已确认章节。请检查新设定与已有内容的一致性，并自动调整设定使其兼容。

【用户的新设定】
故事类型：{{.NewType}}
写作风格：{{.NewWritingStyle}}
叙述视角：{{.NewWritingPOV}}
故事梗概：{{.NewStorySynopsis}}

【已有已确认章节摘要】
{{.ExistingSummaries}}

请以JSON格式返回调整后的设定：
{
  "type": "...",
  "writing_style": "...",
  "writing_pov": "...",
  "story_synopsis": "...",
  "explanation": "说明做了哪些调整及原因"
}

调整原则：
1. 已有章节内容不可更改，设定必须与之兼容
2. 尽量保留用户修改的意图
3. 如有不可调和矛盾，以已有内容为准微调新设定
4. 不冲突的部分直接保留用户新设定""");

        p.setTransitionSmoothing("""
你是一位资深小说编辑，负责优化章节之间的衔接。下面给出上一章的结尾和本章的开头片段，请判断本章开头是否自然承接上一章结尾。

【上一章结尾】
{{.PrevTail}}

【本章（第{{.ChapterNum}}章《{{.ChapterTitle}}》）开头片段】
{{.Opening}}

【本章大纲（仅供理解剧情，不要据此扩写）】
{{.ChapterOutline}}

处理规则（必须严格遵守）：
1. 如果本章开头已经自然承接上一章结尾（场景过渡、时间线、人物状态、情绪基调连贯），只输出 NO_CHANGE 这一个词，不要输出任何其他文字
2. 如果衔接生硬（如场景突兀跳转、重复铺垫已发生内容、人物状态断裂），请重写上面的"本章开头片段"，使其无缝承接上一章结尾
3. 重写是"最小化修改"：保留开头片段中的全部情节和信息，篇幅与原片段相近，只调整承接方式、过渡句和必要细节
4. 只输出重写后的开头片段正文，不要输出标题、解释说明、前后缀标记或上一章内容，不要续写开头片段之外的新内容""");

        p.setOutlineConsistencyCheck("""
你是一位严谨的小说策划编辑。在创作本章正文之前，请检查本章大纲是否已与实际写出的前文剧情冲突。

【前情提要（已发生剧情，不可更改）】
{{.HistorySummary}}

{{.PreviousEnding}}【待检查的本章大纲】
第{{.ChapterNum}}章《{{.ChapterTitle}}》：{{.ChapterOutline}}

检查要点（仅限以下客观冲突）：
1. 大纲安排的"初次见面/初识"事件，相关人物在前文是否已经认识
2. 大纲假设的前置条件（人物状态、所在地点、持有物品、信息知晓情况）是否与前文实际情况一致
3. 大纲安排的事件是否在前文已经发生过

处理规则：
- 没有冲突时，conflict 为 false，revised_outline 留空
- 有冲突时，conflict 为 true，并给出修订后的本章大纲：保持本章原有的情节目标、出场人物和在全书中的作用，只做使其与已发生剧情兼容的最小修改（例如把"初次见面"改为"再次相遇"）
- 不要扩写新剧情，不要改变本章篇幅定位，拿不准是否冲突时一律视为不冲突

请以JSON格式返回（不要输出任何其他文字）：
{"conflict": false, "issues": [], "revised_outline": ""}
或
{"conflict": true, "issues": ["冲突描述"], "revised_outline": "修订后的本章大纲"}""");

        p.setForeshadowOutlineConsistency("""
你是一位严谨的小说叙事一致性编辑。请检查伏笔计划与完整章节大纲是否一致。

【小说标题】{{.Title}}
【完整大纲】
{{.Outline}}

【伏笔列表】
{{.Foreshadows}}

【已确认章节摘要】
{{.AcceptedSummaries}}

检查要点（仅限客观可判定的问题）：
1. 每条未回收、未放弃的伏笔，其 plant_chapter 是否在大纲对应章节中有合理的埋设空间
2. target_chapter 对应章节的大纲是否包含回收该伏笔的情节空间（不要求逐字对应，但逻辑上应能承接）
3. 伏笔描述是否与大纲主线结构性矛盾（按现有大纲不可能实现）
4. plant_chapter / target_chapter 是否超出实际章节总数
5. 已确认章节摘要是否与伏笔的埋设/回收计划明显冲突

请以JSON格式返回（不要输出任何其他文字）：
{
  "has_conflicts": false,
  "conflicts": [],
  "summary": "一句话总结"
}
或
{
  "has_conflicts": true,
  "conflicts": [
    {
      "foreshadow_id": 1,
      "foreshadow_name": "伏笔简称",
      "conflict_type": "missing_payoff|weak_payoff|missing_plant|structural|out_of_range",
      "description": "具体冲突描述",
      "suggested_fix": "revise_outline|adjust_foreshadow|abandon"
    }
  ],
  "summary": "一句话总结"
}

无冲突时 has_conflicts 必须为 false 且 conflicts 为空数组。拿不准时视为无冲突。""");

        p.setWritingConflictAnalysis("""
你是一位资深小说编辑。章节正文在事实核查环节已连续多次失败，请分析根本原因并给出处理建议。

【本章信息】
第{{.ChapterNum}}章《{{.ChapterTitle}}》

【本章大纲】
{{.ChapterOutline}}

【前情提要】
{{.HistorySummary}}

{{.OutlineConstraints}}{{.Foreshadows}}【事实核查累计失败项】
{{.FailedIssues}}

【当前章节正文节选（供参考）】
{{.ContentExcerpt}}

分析任务：
1. 判断失败是否由大纲、伏笔、前情之间的不可调和矛盾导致
2. 若可在不改大纲/伏笔的前提下调和：给出一段可直接注入写作 prompt 的「补充约束」（extra_constraints），指导 AI 写出能通过事实核查的正文
3. 若不可调和：说明原因，并建议用户应修改大纲还是调整伏笔等

返回 JSON（不要输出任何其他文字）：
{
  "reconcilable": true,
  "summary": "一句话总结根因",
  "root_cause": "foreshadow_outline|outline_history|foreshadow_history|mixed|other",
  "extra_constraints": "补充约束全文（reconcilable 为 true 时必填）",
  "suggested_actions": [
    {"id": "edit_outline", "label": "修改本章大纲", "description": "说明应如何改大纲"},
    {"id": "adjust_foreshadow", "label": "调整伏笔", "description": "说明应如何改伏笔"},
    {"id": "force_review", "label": "保留当前稿进入审核", "description": "接受当前版本，人工后续处理"}
  ]
}

reconcilable 为 false 时 extra_constraints 留空；suggested_actions 至少包含 edit_outline、adjust_foreshadow、force_review 三项。""");

        p.setBookDiagnosis("""
你是一位资深网文总编辑，擅长长篇完稿后的通读审阅。

【任务】
通读下方材料，输出《全书优化诊断报告》。本轮只诊断，不改写正文。

{{.ModeNote}}

=== 设定与风格 ===
{{.SettingsText}}

=== 章节摘要索引 ===
{{.SummaryIndex}}

=== 全书正文 ===
{{.FullText}}

【输出格式（严格遵守）】
## 一、总评（200字内）
## 二、结构与节奏（标出拖沓段、高潮段、断档段，定位到章节号）
## 三、人设与台词（角色是否脸谱化、口吻是否统一、主角弧光是否完整）
## 四、设定与逻辑硬伤（时间线、战力、地理、伏笔未收/误收）
## 五、文风与 AI 痕迹（套话、排比堆砌、情绪标签化、对话书面化）
## 六、优先修改清单（P0/P1/P2，每条必须包含：章节号、问题类型、一句话描述、建议改法）
- P0 = 影响阅读的逻辑/设定错误
- P1 = 明显影响质感的文风/节奏问题
- P2 = 锦上添花

【约束】
- 不要泛泛而谈，每条问题必须能定位到具体章节
- 不要输出改写后的正文
- 拿不准的问题标注「需精读复核」""");

        p.setBookConsistencyCheck("""
你是一位严谨的小说事实核查员。请核查整部小说与设定之间的一致性。

{{.VolumeNote}}

=== 设定 ===
{{.SettingsText}}

=== 章节摘要索引（全书） ===
{{.SummaryIndex}}

=== 正文（本卷） ===
{{.FullText}}

【核查维度】
1. 时间线矛盾（年龄、季节、事件先后）
2. 人物设定矛盾（外貌、能力、称呼、关系）
3. 地理/组织/道具前后不一致
4. 伏笔：已埋未收、误收、重复发生的一次性事件（如初遇写了两次）
5. 章间衔接断裂（上一章结尾与本章开头对不上）

【输出格式】
用 Markdown 表格输出：
| 严重度 | 章节 | 原文摘录（≤30字）| 矛盾说明 | 建议修法（最小改动）|

严重度：致命 / 重要 / 轻微
不要改写全文，只给修法。""");

        p.setBookRoadmap("""
你是一位资深小说编辑。请根据以下诊断与核查报告，生成可执行的修改工单。

【诊断报告】
{{.DiagnosisReport}}

【核查报告】
{{.ConsistencyReport}}

【要求】
1. 合并去重，按章节号排序
2. 每章最多 3 条修改项，超出标为二轮
3. type 取值：logic（逻辑）、transition（衔接）、style（文风）、rhythm（节奏）、dialogue（对话）、polish（去AI味润色）
4. priority 取值：P0 / P1 / P2
5. feedback 必须可直接作为修订意见（50–150字），强调最小改动
6. **同一章节的所有问题合并为一条工单**（每章最多 1 条 items），不要在同一章输出多条
7. 建议执行顺序：衔接类 → P0 逻辑 → 文风润色

【输出格式】
只输出 JSON，不要其他文字：
{"items": [{"chapter_num": 1, "type": "logic", "priority": "P0", "feedback": "具体修改意见", "selected": true}]}""");

        return p;
    }

    // ================================================================
    // Build English defaults
    // ================================================================

    private static PromptsConfig buildEN() {
        PromptsConfig p = new PromptsConfig();

        p.setOutlineGeneration("""
You are a professional novel-planning editor. Generate a novel outline that satisfies the constraints below.

Return JSON in exactly this structure:
{
  "title": "Novel title",
  "core_prompt": "Core writing prompt (a system-level guideline that will steer every later chapter)",
  "story_synopsis": "Synopsis of the story",
  "chapters": [
    {"num": 1, "title": "Chapter title", "outline": "Outline for this chapter"},
    ...
  ]
}

[Story type] {{.StoryType}}
[Chapter count] {{.ChapterCount}}
[Words per chapter] {{.TargetWords}}
[Writing style] {{.WritingStyle}}
[Narrative POV] {{.WritingPOV}}
[Synopsis] {{.StorySynopsis}}

Rules:
1. The outline must cover the full story arc, from inciting incident to resolution.
2. Each chapter outline must describe concrete plot beats, not vague summaries.
3. Each chapter outline must list the characters who appear; mark each new character with "first appearance" in the chapter they debut, and ensure they do not appear in any earlier chapter.
4. One-time events such as first meetings and identity reveals must happen in exactly one chapter — never repeat them.
5. core_prompt should bundle the directives that guide the whole novel, including writing style and narrative POV, and must require a consistent POV throughout.
6. Output strict JSON only. No extra prose.""");

        p.setChapterWriting("""
Write the prose for chapter {{.ChapterNum}} of the novel "{{.Title}}".

[Core writing prompt]
{{.CorePrompt}}

[Synopsis]
{{.StorySynopsis}}

[Story-so-far (rolling recap of recent chapters — continue from this state strictly)]
{{.HistorySummary}}

{{.PreviousEnding}}{{.Foreshadows}}{{.OutlineConstraints}}[Task for this chapter]
Chapter title: "{{.ChapterTitle}}"
Outline: {{.ChapterOutline}}

[Writing style] {{.WritingStyle}}
[Narrative POV] {{.WritingPOV}}
{{.CharacterContext}}
{{.WorldviewContext}}
Writing rules:
1. Strictly continue from the character states, timeline, and established facts in the story-so-far. Do not contradict them.
2. Stay inside this chapter's outline. Do not borrow material from later chapters.
3. Do NOT preemptively introduce characters, first meetings, identity reveals, or other one-time events that the outline assigns to later chapters — and do not hint at or spoil them.
4. One-time events already played out (first meetings, identity reveals, relationships established) must be treated as established facts and never re-enacted in this chapter.
5. Do not re-summarise the story-so-far. Open straight into this chapter's scene. If a previous-chapter ending is provided, your opening must seamlessly continue its setting, time, and mood without re-establishing what's already there.
6. Each character's dialogue must match their established voice; do not let everyone sound alike.
7. Drive the plot with concrete action, sensory detail, and dialogue. Avoid abstract, summarising narration.
8. Close on a natural cliffhanger or emotional hook. Do not write meta lines like "to be continued".
9. Keep the narrative POV strictly consistent: follow [Narrative POV] throughout; do not switch person or viewpoint subject unless the POV spec explicitly allows alternation.
10. Target length: about {{.TargetWords}} words.
11. Output ONLY the chapter prose — no chapter title, chapter number, outline recap, author notes, dividers, or meta lines such as "Chapter X", "(Chapter X text)", "End of chapter", "To be continued", "Here is the revised chapter", "Below is the full text". Do not add any preamble before the prose or any summary after it.""");

        p.setChapterRevision("""
You are the author of this novel. Revise chapter {{.ChapterNum}} "{{.ChapterTitle}}" according to the feedback below.

[Core writing prompt]
{{.CorePrompt}}

[Story-so-far]
{{.HistorySummary}}

[Writing style] {{.WritingStyle}}
[Narrative POV] {{.WritingPOV}}
{{.CharacterContext}}
{{.WorldviewContext}}
[Current chapter text]
{{.OriginalContent}}

[Revision feedback]
{{.UserFeedback}}

Revision rules (strict):
1. This is a "revision", not a "rewrite". Change only what the feedback requires; leave everything else exactly as written (wording, paragraph structure).
2. The revised chapter must remain consistent with the story-so-far and the unchanged portions (names, timeline, established facts).
3. Do not alter the chapter's overall plot direction unless the feedback explicitly requests it.
4. Keep the narrative POV strictly consistent: follow [Narrative POV] throughout; do not switch person or viewpoint subject unless the POV spec explicitly allows alternation.
5. Output the full revised chapter prose (including the unchanged portions). No chapter title, chapter number, author notes, dividers, or meta lines such as "Chapter X", "(Chapter X text)", "End of chapter", "To be continued", "Here is the revised chapter", "Below is the full text". Do not add any preamble before the prose or any summary after it.""");

        p.setChapterSummary("""
You are a precise novel narrative-state analyst. You distil literary text into the narrative elements and psychological beats that downstream chapters need.

Compress the chapter below into a structured summary of 250 words or fewer.

Use exactly this format:

[Chapter core] One sentence describing what happens (or the protagonist's current state).
[Character beats] Characters that appear and how their relationships move. Explicitly note one-time events such as "A and B meet for the first time" or "B's identity is revealed". If nothing changes, write "no new progress".
[Psychological arc] The protagonist's current mental state, emotional tone, and any pivotal internal turn.
[State changes] What changed about the protagonist (outward: appearance/clothing/behaviour; inward: attitude/perception) compared to the previous chapter. If nothing changed, write "carries over from previous chapter".
[Key details] One or two details with the highest narrative continuation value that later chapters may reference.
[Emotional palette] Two or three words capturing the chapter's mood.

[Chapter text]
{{.ChapterContent}}""");

        p.setFactCheck("""
You are a strict novel fact-checker. Your task is to detect objective factual contradictions in the chapter.

Check whether the chapter below contradicts the story-so-far or the outline arc.

[Story-so-far]
{{.HistorySummary}}

[Chapter outline]
{{.ChapterOutline}}

{{.OutlineConstraints}}[Chapter under review]
{{.ChapterContent}}

Scope (only the following count as problems, nothing else):
1. Character names or honorifics inconsistent with prior text.
2. Timeline contradictions (e.g. previous text ended at night, this chapter inexplicably reverts to morning of the same day).
3. Facts that directly contradict established events (a dead character reappearing without explanation, a destroyed object intact again).
4. Character abilities or identity directly clashing with established setting.
5. Premature introduction of characters, first meetings, identity reveals, or other events that the outline assigns to later chapters.
6. One-time events already played out in prior chapters being re-enacted as new in this chapter.

Notes:
- Style, pacing, scene-length choices, and plot plausibility are subjective issues — always PASS them.
- New information that neither the story-so-far nor the outline mentions is not a contradiction.
- Only solid objective contradictions warrant FAIL. When in doubt, PASS.

Return JSON only (no other text):
{"result": "PASS", "issues": []}
or
{"result": "FAIL", "issues": ["concrete contradiction 1", "concrete contradiction 2"]}""");

        p.setOutlineRevision("""
You are a novel-planning editor. The user gave revision feedback on the outline. Revise accordingly.

[Current outline]
{{.CurrentOutline}}

[User feedback]
{{.UserFeedback}}

[Locked chapters (must not be changed)]
{{.LockedChapters}}

Return the revised full outline as JSON:
{
  "title": "Novel title",
  "core_prompt": "Core writing prompt",
  "story_synopsis": "Synopsis",
  "chapters": [
    {"num": 1, "title": "Chapter title", "outline": "Outline for this chapter"},
    ...
  ]
}

Rules:
1. Locked chapter contents may not be changed; only unlocked chapters may be edited.
2. Keep the total chapter count and numbering unchanged unless the feedback explicitly requires adding or removing chapters.
3. Return chapters unrelated to the feedback verbatim. Do not refactor them while you're at it.
4. Output strict JSON only. No extra prose.""");

        p.setForeshadowPlanning("""
You are a senior narrative architect who specialises in foreshadow design. Design a foreshadow plan for the novel outline below.

[Title] {{.Title}}
[Core writing prompt] {{.CorePrompt}}
[Synopsis] {{.StorySynopsis}}

[Full outline]
{{.Outline}}

Design 3 to 8 foreshadows following these principles:
1. Each foreshadow should serve the main plot or character arc, not exist for mystery's sake.
2. Each foreshadow has a clear "plant point" (chapter it is seeded) and "payoff point" (chapter where it is expected to be resolved).
3. Foreshadows may interconnect into a web of clues.
4. Vary the types: objects, hinted dialogue, environmental detail, contradictions in behaviour, unexplained phenomena, etc.
5. Spread payoff points across different chapters; do not cluster them.
6. Foreshadows can begin as early as chapter 1, but most should be planted in the middle and paid off in the latter half.

Return JSON:
{
  "foreshadows": [
    {
      "name": "Short label (under 10 words)",
      "description": "Detailed description: how it is planted, what it hints at, what the 'oh-I-see' feeling should be when it pays off",
      "plant_chapter": chapter_number,
      "target_chapter": expected_payoff_chapter
    }
  ]
}

Output strict JSON only.""");

        p.setForeshadowUpdate("""
You are a strict foreshadow tracker. Update the foreshadow system based on the just-completed chapter.

[Title] {{.Title}}

[Current foreshadows]
{{.Foreshadows}}

[Chapter info]
Chapter number: {{.ChapterNum}}
Chapter title: "{{.ChapterTitle}}"

[Chapter text]
{{.ChapterContent}}

[Story-so-far]
{{.HistorySummary}}

For each foreshadow, decide whether its state changed in this chapter:

1. First time it is hinted/planted in this chapter → status = "planted".
2. New clue or progress in this chapter → status = "progressing".
3. Fully revealed/resolved in this chapter → status = "resolved".
4. Not present in this chapter → keep the existing status.
5. Distinguish "true resolution" from "mere progress": only mark resolved when the mystery is fully unveiled.

Return JSON:
{
  "updates": [
    {
      "id": foreshadow_id,
      "status": "new state if changed",
      "event": "one-sentence description of what this chapter did with this foreshadow",
      "resolution": "how it was resolved, if status = resolved"
    }
  ]
}

Only return foreshadows whose state changed. Omit any foreshadow not touched in this chapter.
Output strict JSON only.""");

        p.setContentAnalysis("""
You are a professional novel analysis editor. Analyse the existing novel text, extract story metadata, and produce per-chapter outline + summary entries.

Return JSON in this structure:
{
  "title": "Novel title",
  "story_type": "Genre (fantasy/urban/sci-fi/mystery, etc.)",
  "core_prompt": "Core writing prompt (system-level guideline for downstream chapters)",
  "story_synopsis": "Synopsis",
  "writing_style": "Writing-style description",
  "writing_pov": "Narrative POV (e.g. third-person limited, first-person heroine, alternating first-person leads)",
  "chapters": [
    {
      "num": 1,
      "title": "Chapter title",
      "outline": "Chapter outline (what happens, 100-200 words)",
      "summary": "Structured summary (for downstream story-so-far, under 200 words: core events, psychological arc, state changes, key details)"
    }
  ]
}

Requirements:
1. Detect chapter boundaries (common formats: "Chapter X", "# Chapter X", blank-line separators, etc.).
2. For each chapter produce: outline (what happens) and summary (structured story-so-far for downstream chapters).
3. summary should retain continuation-relevant state: core events, psychological arc, key details, emotional palette.
4. Extract story metadata: genre, writing style, narrative POV, character settings, worldview.
5. Generate core_prompt and story_synopsis to guide downstream writing.

[Existing novel text]
{{.ExistingContent}}

Output strict JSON only.""");

        p.setContinuationOutlineGeneration("""
You are a professional novel-planning editor. Based on existing chapters' outlines and summaries, produce the outline for the next chapters.

[Title] {{.Title}}
[Story type] {{.StoryType}}
[Core writing prompt] {{.CorePrompt}}
[Synopsis] {{.StorySynopsis}}
[Writing style] {{.WritingStyle}}
[Narrative POV] {{.WritingPOV}}

[Existing chapters]
{{.ExistingOutline}}

Produce outlines for {{.NewChapterCount}} more chapters, starting at chapter {{.StartNum}}.

Return JSON:
{
  "chapters": [
    {"num": {{.StartNum}}, "title": "Chapter title", "outline": "Outline for this chapter"},
    ...
  ]
}

Rules:
1. The outlines must continue the existing storyline coherently.
2. Each outline should describe concrete plot beats, not vague summaries.
3. List the characters appearing in each chapter; mark new characters with "first appearance" in their debut chapter.
4. One-time events already used in prior chapters (first meeting, identity reveal, etc.) must not be re-scheduled.
5. Output strict JSON only.""");

        p.setSettingsReconciliation("""
You are a professional novel-consistency editor. The user changed the story settings, but some chapters are already confirmed. Check whether the new settings are consistent with the existing chapters, and auto-adjust the settings to remain compatible.

[User's new settings]
Story type: {{.NewType}}
Writing style: {{.NewWritingStyle}}
Narrative POV: {{.NewWritingPOV}}
Synopsis: {{.NewStorySynopsis}}

[Summaries of existing confirmed chapters]
{{.ExistingSummaries}}

Return the adjusted settings as JSON:
{
  "type": "...",
  "writing_style": "...",
  "writing_pov": "...",
  "story_synopsis": "...",
  "explanation": "Describe what was adjusted and why"
}

Adjustment principles:
1. Existing chapters cannot be changed; the settings must be compatible with them.
2. Preserve the user's intent as much as possible.
3. Where conflicts are irreconcilable, prefer existing content and adjust the new settings minimally.
4. Non-conflicting parts keep the user's new settings.""");

        p.setTransitionSmoothing("""
You are a senior novel editor in charge of polishing chapter-to-chapter transitions. Below are the end of the previous chapter and the opening of the current chapter. Decide whether the opening naturally continues from the previous ending.

[Previous chapter ending]
{{.PrevTail}}

[Opening of current chapter (chapter {{.ChapterNum}} "{{.ChapterTitle}}")]
{{.Opening}}

[Chapter outline (for context only — do not expand it)]
{{.ChapterOutline}}

Rules (strict):
1. If the opening already continues naturally from the previous ending (scene transition, timeline, character state, emotional tone all coherent), output exactly the single word NO_CHANGE and nothing else.
2. If the transition is rough (abrupt scene jump, re-establishing what already happened, character-state break), rewrite the opening above so it seamlessly continues from the previous ending.
3. The rewrite is "minimal": keep every plot beat and piece of information in the opening, similar length to the original, only adjust the bridging beats, transitional sentences, and necessary detail.
4. Output only the rewritten opening prose — no title, explanation, prefix/suffix marker, or previous-chapter content. Do not continue past the opening.""");

        p.setOutlineConsistencyCheck("""
You are a strict novel-planning editor. Before drafting this chapter's prose, check whether this chapter's outline already conflicts with the actual prior storyline.

[Story-so-far (already happened, cannot be changed)]
{{.HistorySummary}}

{{.PreviousEnding}}[Outline under check]
Chapter {{.ChapterNum}} "{{.ChapterTitle}}": {{.ChapterOutline}}

Checklist (objective conflicts only):
1. Outline schedules a "first meeting" between characters who already know each other in prior text.
2. Outline assumes a precondition (character state, location, possessed item, knowledge) that contradicts prior text.
3. Outline schedules an event that has already happened in prior text.

Rules:
- If no conflict: conflict = false, revised_outline left empty.
- If there is a conflict: conflict = true, and provide a revised outline for this chapter that keeps its original plot goals, characters, and role in the overall arc — only the minimum changes needed to make it compatible with prior text (e.g. change "first meeting" to "reunion").
- Do not expand new plot. Do not change the chapter's length tier. When unsure, treat as no conflict.

Return JSON only (no other text):
{"conflict": false, "issues": [], "revised_outline": ""}
or
{"conflict": true, "issues": ["conflict description"], "revised_outline": "revised outline for this chapter"}""");

        p.setForeshadowOutlineConsistency("""
You are a strict narrative-consistency editor. Check whether the foreshadow plan matches the full chapter outline.

[Title] {{.Title}}
[Full outline]
{{.Outline}}

[Foreshadow list]
{{.Foreshadows}}

[Summaries of confirmed chapters]
{{.AcceptedSummaries}}

Checklist (objective issues only):
1. Each active foreshadow (not resolved/abandoned) has reasonable planting space in its plant_chapter outline.
2. The target_chapter outline has plot space to pay off that foreshadow (logical fit, not word-for-word match).
3. Foreshadow description structurally contradicts the outline (cannot be achieved on this story path).
4. plant_chapter / target_chapter exceed the actual chapter count.
5. Confirmed-chapter summaries clearly contradict the foreshadow plan.

Return JSON only (no other text):
{
  "has_conflicts": false,
  "conflicts": [],
  "summary": "one-sentence summary"
}
or
{
  "has_conflicts": true,
  "conflicts": [
    {
      "foreshadow_id": 1,
      "foreshadow_name": "short name",
      "conflict_type": "missing_payoff|weak_payoff|missing_plant|structural|out_of_range",
      "description": "specific conflict",
      "suggested_fix": "revise_outline|adjust_foreshadow|abandon"
    }
  ],
  "summary": "one-sentence summary"
}

When unsure, treat as no conflict.""");

        p.setWritingConflictAnalysis("""
You are a senior novel editor. Fact-checking has failed repeatedly for this chapter. Diagnose the root cause and recommend next steps.

[Chapter]
Chapter {{.ChapterNum}} "{{.ChapterTitle}}"

[Chapter outline]
{{.ChapterOutline}}

[Story-so-far]
{{.HistorySummary}}

{{.OutlineConstraints}}{{.Foreshadows}}[Accumulated fact-check failures]
{{.FailedIssues}}

[Current draft excerpt (reference)]
{{.ContentExcerpt}}

Tasks:
1. Decide whether failures come from irreconcilable contradictions among outline, foreshadows, and prior story.
2. If reconcilable without changing outline/foreshadows: provide extra_constraints text to inject into the writing prompt so the next draft can pass fact-check.
3. If not reconcilable: explain why and whether the user should edit the outline or adjust foreshadows.

Return JSON only (no other text):
{
  "reconcilable": true,
  "summary": "one-sentence root cause",
  "root_cause": "foreshadow_outline|outline_history|foreshadow_history|mixed|other",
  "extra_constraints": "full constraint text (required when reconcilable is true)",
  "suggested_actions": [
    {"id": "edit_outline", "label": "Edit chapter outline", "description": "..."},
    {"id": "adjust_foreshadow", "label": "Adjust foreshadows", "description": "..."},
    {"id": "force_review", "label": "Keep draft for manual review", "description": "..."}
  ]
}

When reconcilable is false, leave extra_constraints empty. suggested_actions must include edit_outline, adjust_foreshadow, and force_review.""");

        p.setBookDiagnosis("""
You are a senior editor-in-chief for serialised fiction, specialising in full-novel reviews after the manuscript is complete.

[Task]
Read the materials below and produce a "Full-Novel Optimisation Diagnostic Report". Only diagnose this round — do not rewrite prose.

{{.ModeNote}}

=== Settings and style ===
{{.SettingsText}}

=== Chapter summary index ===
{{.SummaryIndex}}

=== Full novel text ===
{{.FullText}}

[Output format (strict)]
## 1. Overall assessment (under 200 words)
## 2. Structure and pacing (point out dragging sections, peak sections, lull sections — anchor every issue to a chapter number)
## 3. Characterisation and dialogue (flat archetypes, inconsistent voice, completeness of protagonist arc)
## 4. Setting and logic faults (timeline, power level, geography, foreshadow misses or wrong payoffs)
## 5. Style and AI fingerprints (cliches, parallel-clause pile-ups, emotion labelling, overly written dialogue)
## 6. Prioritised fix list (P0/P1/P2; every entry must contain: chapter number, issue type, one-line description, suggested fix)
- P0 = logic/setting error that blocks reading
- P1 = style/pacing problem with clear quality impact
- P2 = polish

[Constraints]
- No vague generalities. Every issue must anchor to a specific chapter.
- Do not output rewritten prose.
- When unsure, mark "needs close re-read".""");

        p.setBookConsistencyCheck("""
You are a strict novel fact-checker. Check the entire novel for consistency with its settings.

{{.VolumeNote}}

=== Settings ===
{{.SettingsText}}

=== Chapter summary index (whole novel) ===
{{.SummaryIndex}}

=== Prose (this volume) ===
{{.FullText}}

[Audit dimensions]
1. Timeline contradictions (age, season, event order)
2. Character-setting contradictions (appearance, abilities, address, relationships)
3. Inconsistent geography / organisations / props
4. Foreshadows: planted-but-never-paid, wrong payoffs, one-time events re-enacted (e.g. a first meeting written twice)
5. Transition breaks between chapters (previous ending and current opening do not match)

[Output format]
Use a Markdown table:
| Severity | Chapter | Original excerpt (<= 30 words) | Contradiction description | Suggested fix (minimum change) |

Severity: critical / major / minor
Do not rewrite the prose, only describe the fix.""");

        p.setBookRoadmap("""
You are a senior novel editor. Based on the diagnostic and consistency reports below, produce an executable revision task list.

[Diagnostic report]
{{.DiagnosisReport}}

[Consistency report]
{{.ConsistencyReport}}

[Requirements]
1. Merge duplicates and sort by chapter number.
2. At most 3 revision items per chapter; anything beyond goes to round two.
3. type takes values: logic, transition, style, rhythm, dialogue, polish (AI-flavour removal).
4. priority takes values: P0 / P1 / P2.
5. feedback must be ready-to-use revision instructions (50 to 150 words) emphasising minimum changes.
6. **Merge all issues for the same chapter into ONE task** (at most one items entry per chapter).
7. Suggested execution order: transitions -> P0 logic -> style polish.

[Output format]
JSON only, nothing else:
{"items": [{"chapter_num": 1, "type": "logic", "priority": "P0", "feedback": "concrete revision instruction", "selected": true}]}""");

        return p;
    }
}
