package com.showmethestory.i18n;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static bilingual message catalog.
 * Each entry is a String[2] where [0]=zh, [1]=en.
 * Templates use %s / %d / %v format verbs (converted to Java by LocaleHelper).
 * <p>
 * Mirrors Go's messages.go (messageCatalog) and locale.go (errorCatalog).
 */
public final class Messages {

    private Messages() {}

    /**
     * Message catalog: key -> [zh, en].
     */
    public static final Map<String, String[]> messageCatalog = new LinkedHashMap<>();

    /**
     * Error catalog: key -> [zh, en].
     */
    public static final Map<String, String[]> errorCatalog = new LinkedHashMap<>();

    static {
        // ============================================================
        // messageCatalog  (from Go messages.go)
        // ============================================================

        // ---- Task / handler logs ----
        m("log.autoconfirm_on",
          "已开启自动确认模式：每章生成完成后将自动确认并继续生成下一章",
          "Auto-confirm enabled: each chapter is automatically confirmed once generation completes, then the next chapter begins.");
        m("log.autoconfirm_off",
          "已关闭自动确认模式",
          "Auto-confirm disabled");
        m("log.outline_cleared_pending",
          "已自动清除旧的大纲（pending 章节）",
          "Cleared previous outline (pending chapters)");
        m("log.outline_generating",
          "正在生成小说大纲...",
          "Generating novel outline...");
        m("log.outline_generate_cancelled",
          "大纲生成已取消",
          "Outline generation cancelled");
        m("log.outline_generate_failed",
          "大纲生成失败: %s",
          "Outline generation failed: %s");
        m("log.outline_generate_done",
          "大纲生成完成！",
          "Outline generation complete.");
        m("log.outline_confirmed",
          "大纲已确认，进入写作阶段。",
          "Outline confirmed. Entering writing phase.");
        m("log.outline_revising",
          "正在根据意见修订大纲...",
          "Revising outline based on feedback...");
        m("log.outline_revise_cancelled",
          "大纲修订已取消",
          "Outline revision cancelled");
        m("log.outline_revise_failed",
          "大纲修订失败: %s",
          "Outline revision failed: %s");
        m("log.outline_revised",
          "大纲已修订。",
          "Outline revised.");
        m("log.chapter_writing",
          "正在创作第 %d 章...",
          "Writing chapter %d...");
        m("log.chapter_write_cancelled",
          "章节创作已取消",
          "Chapter writing cancelled");
        m("log.chapter_write_conflict_pause",
          "章节创作因事实核查冲突暂停，等待你选择处理方向",
          "Chapter writing paused due to fact-check conflict — choose how to proceed");
        m("log.chapter_write_failed",
          "章节创作失败: %s",
          "Chapter writing failed: %s");
        m("log.chapter_write_done",
          "第 %d 章《%s》创作完成！",
          "Chapter %d \"%s\" complete!");
        m("log.chapter_autoconfirm_failed",
          "自动确认失败: %s",
          "Auto-confirm failed: %s");
        m("log.chapter_autoconfirmed",
          "第 %d 章《%s》已自动确认。",
          "Chapter %d \"%s\" auto-confirmed.");
        m("log.all_chapters_done",
          "全部章节已创作完成！",
          "All chapters generated.");
        m("log.autowrite_cancelled",
          "任务已取消，停止自动续写",
          "Task cancelled; auto-continue stopped");
        m("log.chapter_kept_review",
          "第 %d 章已保留当前稿并进入审核。",
          "Chapter %d kept as draft and moved to review.");
        m("log.chapter_confirmed",
          "第 %d 章已确认。",
          "Chapter %d confirmed.");
        m("log.chapter_revising",
          "正在根据意见修改当前章节...",
          "Revising current chapter based on feedback...");
        m("log.chapter_revise_cancelled",
          "章节修订已取消",
          "Chapter revision cancelled");
        m("log.chapter_revise_failed",
          "章节修订失败: %s",
          "Chapter revision failed: %s");
        m("log.chapter_revised",
          "章节已修订。",
          "Chapter revised.");
        m("log.chapter_specific_revising",
          "正在定向修订第 %d 章...",
          "Targeted revision of chapter %d...");
        m("log.smooth_transitions_cancelled",
          "章节衔接优化已取消（已完成部分不会丢失）",
          "Transition smoothing cancelled (completed parts are preserved)");
        m("log.smooth_transitions_failed",
          "章节衔接优化失败: %s",
          "Transition smoothing failed: %s");
        m("log.chapter_deleted",
          "已删除第 %d 章。",
          "Deleted chapter %d.");
        m("log.outline_deleted",
          "大纲已删除。",
          "Outline deleted.");
        m("log.chapter_outline_updated",
          "第 %d 章大纲已更新。",
          "Chapter %d outline updated.");
        m("log.settings_reconciling",
          "正在协调新设定与已有内容...",
          "Reconciling new settings with existing content...");
        m("log.settings_reconcile_cancelled",
          "设定协调已取消",
          "Settings reconciliation cancelled");
        m("log.settings_reconcile_failed",
          "设定协调失败: %s",
          "Settings reconciliation failed: %s");
        m("log.settings_reconcile_done",
          "设定协调完成！",
          "Settings reconciliation complete.");
        m("log.delete_file_failed",
          "删除文件 %s 失败: %v",
          "Failed to delete file %s: %v");
        m("log.chapters_deleted_from",
          "已从第 %d 章删除到末尾，共删除 %d 章。",
          "Deleted from chapter %d to end (%d chapters).");
        m("log.foreshadow_roadmap_save_failed",
          "伏笔路线图保存失败: %v",
          "Failed to save foreshadow roadmap: %v");
        m("log.foreshadow_suggesting",
          "正在分析大纲，设计伏笔方案...",
          "Analysing outline and designing foreshadow plan...");
        m("log.foreshadow_suggest_cancelled",
          "伏笔建议已取消",
          "Foreshadow suggestions cancelled");
        m("log.foreshadow_suggest_failed",
          "伏笔建议生成失败: %s",
          "Foreshadow suggestion failed: %s");
        m("log.foreshadow_suggest_done",
          "伏笔建议生成完成，共 %d 条",
          "Foreshadow suggestions ready (%d items)");
        m("log.continue_analyzing",
          "正在分析已有内容...",
          "Analysing existing content...");
        m("log.continue_analyze_cancelled",
          "内容分析已取消",
          "Content analysis cancelled");
        m("log.continue_analyze_failed",
          "内容分析失败: %s",
          "Content analysis failed: %s");
        m("log.continue_analyze_done",
          "内容分析完成，发现 %d 章",
          "Content analysis complete — found %d chapters");
        m("log.continue_import_done",
          "续写导入完成，已进入大纲阶段。",
          "Continuation import complete — entered outline phase.");
        m("log.continuation_outline_generating",
          "正在生成续写大纲...",
          "Generating continuation outline...");
        m("log.continuation_outline_cancelled",
          "续写大纲生成已取消",
          "Continuation outline cancelled");
        m("log.continuation_outline_failed",
          "续写大纲生成失败: %s",
          "Continuation outline failed: %s");
        m("log.continuation_outline_done",
          "续写大纲生成完成！",
          "Continuation outline complete.");
        m("log.chapter_polish_cancelled",
          "章节润色已取消",
          "Chapter polish cancelled");
        m("log.chapter_polish_failed",
          "章节润色失败: %s",
          "Chapter polish failed: %s");
        m("log.postprocess_diagnose_cancelled",
          "全书优化分析已取消",
          "Full-book analysis cancelled");
        m("log.postprocess_diagnose_failed",
          "全书优化分析失败: %s",
          "Full-book analysis failed: %s");
        m("log.postprocess_consistency_cancelled",
          "全书一致性核查已取消",
          "Consistency check cancelled");
        m("log.postprocess_consistency_failed",
          "全书一致性核查失败: %s",
          "Consistency check failed: %s");
        m("log.postprocess_roadmap_cancelled",
          "路线图生成已取消",
          "Roadmap generation cancelled");
        m("log.postprocess_roadmap_failed",
          "路线图生成失败: %s",
          "Roadmap generation failed: %s");
        m("log.postprocess_execute_cancelled",
          "全书优化执行已取消（已完成项不会丢失）",
          "Optimisation execution cancelled (completed items are preserved)");
        m("log.postprocess_execute_failed",
          "全书优化执行失败: %s",
          "Optimisation execution failed: %s");
        m("log.child_task_start_failed",
          "无法启动子任务 %s：主任务已结束",
          "Cannot start child task %s: parent task already ended");
        m("log.save_session_failed",
          "保存会话失败: %v",
          "Failed to save chat session: %v");
        m("log.chat_cancelled",
          "助理对话已取消",
          "Assistant chat cancelled");
        m("log.chat_failed",
          "助理回复失败: %v",
          "Assistant reply failed: %v");
        m("log.chat_done",
          "助理回复完成",
          "Assistant reply complete.");
        m("log.project_deleted",
          "项目「%s」已删除",
          "Project \"%s\" deleted");
        m("log.project_created",
          "项目「%s」创建成功",
          "Project \"%s\" created");

        // ---- Writing pipeline logs ----
        m("log.chapter_start",
          "开始创作第 %d 章: 《%s》",
          "Starting chapter %d: \"%s\"");
        m("log.outline_check_failed",
          "大纲一致性检查失败: %v（按原大纲继续）",
          "Outline consistency check failed: %v (continuing with original outline)");
        m("log.outline_auto_revised",
          "本章大纲已自动修订以匹配当前剧情",
          "Chapter outline auto-revised to match current story");
        m("log.outline_consistent",
          "本章大纲与当前剧情一致 ✓",
          "Chapter outline consistent with story ✓");
        m("log.prose_done",
          "正文撰写完毕，共 %d 字",
          "Prose complete — %d characters");
        m("log.summary_done",
          "摘要提炼完成",
          "Summary extraction complete");
        m("log.factcheck_retry",
          "[事实核查] 发现问题，正在重新生成第 %d 章（第 %d 次重试）...",
          "[Fact-check] Issues found — regenerating chapter %d (retry %d)...");
        m("log.factcheck_details",
          "核查详情: %s",
          "Check details: %s");
        m("log.factcheck_max_retries",
          "[事实核查] 已达最大重试次数，正在分析冲突根因...",
          "[Fact-check] Max retries reached — analysing conflict root cause...");
        m("log.conflict_analyze_failed",
          "冲突分析失败: %v，保留当前版本",
          "Conflict analysis failed: %v — keeping current version");
        m("log.conflict_retry",
          "检测到可调和冲突，正在按补充约束进行最后一次尝试...",
          "Resolvable conflict detected — final attempt with extra constraints...");
        m("log.factcheck_constraint_pass",
          "[事实核查] 补充约束尝试通过 ✓",
          "[Fact-check] Constrained retry passed ✓");
        m("log.factcheck_pass",
          "[事实核查] 通过 ✓",
          "[Fact-check] Passed ✓");
        m("log.chapter_write_complete",
          "第 %d 章创作完成！",
          "Chapter %d writing complete!");
        m("log.outline_conflict",
          "第 %d 章大纲与当前剧情冲突: %s",
          "Chapter %d outline conflicts with story: %s");
        m("log.chapter_modifying",
          "正在修改第 %d 章《%s》...",
          "Revising chapter %d \"%s\"...");
        m("log.prose_revised",
          "正文修改完毕，共 %d 字",
          "Revision complete — %d characters");
        m("log.subsequent_outline_failed",
          "后续大纲修订失败: %v（不影响当前章节）",
          "Subsequent outline revision failed: %v (current chapter unaffected)");
        m("log.subsequent_outline_done",
          "后续大纲修订完成",
          "Subsequent outline revision complete");
        m("log.chapter_specific_revising_long",
          "正在对第 %d 章《%s》进行定向修订（不影响其他章节）...",
          "Targeted revision of chapter %d \"%s\" (other chapters untouched)...");
        m("log.prose_specific_revised",
          "正文修订完毕，共 %d 字",
          "Targeted revision complete — %d characters");
        m("log.chapter_specific_done",
          "第 %d 章定向修订完成（其余章节未受影响）。",
          "Chapter %d targeted revision complete (other chapters untouched).");
        m("log.fatal_no_retry",
          "致命错误: %v，不再重试",
          "Fatal error: %v — not retrying");
        m("log.content_gen_retry",
          "正文生成失败: %v。第 %d 次重试，等待 %ds...",
          "Prose generation failed: %v. Retry %d, waiting %ds...");
        m("log.summary_retry",
          "摘要提炼失败: %v。第 %d 次重试，等待 %ds...",
          "Summary extraction failed: %v. Retry %d, waiting %ds...");
        m("log.factcheck_api_retry",
          "事实核查失败: %v。第 %d 次重试，等待 %ds...",
          "Fact-check failed: %v. Retry %d, waiting %ds...");
        m("log.smooth_start",
          "开始章节衔接优化，共 %d 章待检查",
          "Starting transition smoothing — %d chapters to check");
        m("log.smooth_natural",
          "第 %d 章衔接自然，无需修改",
          "Chapter %d transition is smooth — no change needed");
        m("log.smooth_optimized",
          "第 %d 章开头已优化并保存",
          "Chapter %d opening optimised and saved");
        m("log.smooth_done",
          "章节衔接优化完成：检查 %d 章，优化 %d 章",
          "Transition smoothing complete: checked %d, optimised %d");
        m("log.outline_generate_summary",
          "大纲生成完成，共 %d 章，标题: 《%s》",
          "Outline complete — %d chapters, title: \"%s\"");
        m("log.outline_revise_summary",
          "大纲已修订，共 %d 章",
          "Outline revised — %d chapters");
        m("log.reconcile_pending_outline_failed",
          "待定章节大纲重新生成失败: %v（设定已更新）",
          "Failed to regenerate pending outlines: %v (settings updated)");
        m("log.reconcile_done_explain",
          "设定协调完成。%s",
          "Settings reconciliation complete. %s");
        m("log.continuation_outline_summary",
          "续写大纲生成完成，新增 %d 章，总计 %d 章",
          "Continuation outline complete — added %d chapters (total %d)");
        m("log.foreshadow_outline_check_failed",
          "伏笔-大纲一致性检查失败: %v",
          "Foreshadow/outline consistency check failed: %v");
        m("log.foreshadow_outline_report_save_failed",
          "保存伏笔-大纲检查报告失败: %v",
          "Failed to save foreshadow/outline check report: %v");
        m("log.foreshadow_outline_check_pass",
          "伏笔与大纲一致性检查通过 ✓",
          "Foreshadow/outline consistency check passed ✓");
        m("log.foreshadow_plan_parsed",
          "伏笔方案解析完成，共 %d 条",
          "Foreshadow plan parsed — %d items");
        m("log.foreshadow_status_updated",
          "伏笔状态更新完成，处理 %d 条变更",
          "Foreshadow status updated — %d changes");
        m("log.foreshadow_sync_failed",
          "伏笔状态更新失败: %v（不影响本章）",
          "Foreshadow sync failed: %v (chapter unaffected)");
        m("log.foreshadow_sync_summary",
          "伏笔状态已更新（活跃: %d, 已回收: %d）",
          "Foreshadow status updated (active: %d, resolved: %d)");
        m("log.postprocess_material",
          "全书材料：约 %d 字，预估 %d tokens，诊断模式：%s",
          "Book material: ~%d chars, ~%d tokens, diagnosis mode: %s");
        m("log.postprocess_consistency_single",
          "开始全书一致性核查（单卷）...",
          "Starting full-book consistency check (single volume)...");
        m("log.postprocess_consistency_multi",
          "正文较长，分 %d 卷进行一致性核查...",
          "Long text — consistency check split into %d volumes...");
        m("log.postprocess_roadmap_items",
          "已生成 %d 条优化工单",
          "Generated %d optimisation roadmap items");
        m("log.postprocess_smooth_preface",
          "前置步骤：优化章节衔接...",
          "Preface: optimising chapter transitions...");
        m("log.postprocess_smooth_skip",
          "章节衔接优化跳过或失败: %v",
          "Transition smoothing skipped or failed: %v");
        m("log.postprocess_batch_failed",
          "第 %d 章工单失败: %v",
          "Chapter %d roadmap batch failed: %v");
        m("log.postprocess_batch_done",
          "第 %d 章已完成（合并 %d 条意见）",
          "Chapter %d done (merged %d items)");
        m("log.postprocess_batch_skip",
          "第 %d 章内容无变化，已跳过",
          "Chapter %d unchanged — skipped");
        m("log.postprocess_execute_summary",
          "全书优化执行完成：处理 %d 章（共 %d 条工单），有效修改 %d 章",
          "Optimisation execution complete: %d chapters (%d items), %d modified");
        m("log.api_fatal",
          "致命错误: %v，不再重试",
          "Fatal error: %v — not retrying");
        m("log.api_retry",
          "API调用失败: %v。第 %d 次重试，等待 %ds...",
          "API call failed: %v. Retry %d, waiting %ds...");
        m("log.api_stream_retry",
          "流式API调用失败: %v。第 %d 次重试，等待 %ds...",
          "Streaming API call failed: %v. Retry %d, waiting %ds...");

        // ---- Agent tool status messages ----
        m("agent.task_cancelled",
          "任务已取消",
          "Task cancelled");
        m("agent.api_failed",
          "Agent API 调用失败: %v",
          "Agent API call failed: %v");
        m("agent.max_steps",
          "已达到最大工具调用步骤限制。",
          "Reached the maximum tool-call step limit.");
        m("agent.tool_exec_error",
          "工具执行错误: %v",
          "Tool execution error: %v");
        m("agent.unknown_tool",
          "未知工具: %s",
          "Unknown tool: %s");
        m("agent.confirm_required",
          "⚠️ 操作未执行：「%s」是不可逆的危险操作。请先向用户复述影响范围并获得明确同意，确认后携带 confirm=true 重新调用。如果用户的本意是修改内容而非删除，请改用对应的修订工具。",
          "⚠️ Not executed: \"%s\" is irreversible. Restate the impact to the user and get explicit consent, then retry with confirm=true. If the user meant to edit content, use the revise tool instead.");
        m("agent.no_characters",
          "暂无角色数据",
          "No character data");
        m("agent.characters_not_found",
          "没有找到匹配的角色",
          "No matching characters found");
        m("agent.character_not_found",
          "未找到角色: %s",
          "Character not found: %s");
        m("agent.no_worldview",
          "暂无世界观数据",
          "No worldview data");
        m("agent.worldview_not_found",
          "没有找到匹配的世界观条目",
          "No matching worldview entries");
        m("agent.no_organizations",
          "暂无组织数据",
          "No organization data");
        m("agent.chapter_not_found",
          "未找到第%d章",
          "Chapter %d not found");
        m("agent.no_outline",
          "暂无大纲",
          "No outline yet");
        m("agent.no_foreshadows",
          "暂无伏笔",
          "No foreshadows yet");
        m("agent.search_keyword_required",
          "请提供搜索关键词",
          "Search keyword required");
        m("agent.search_no_results",
          "未找到相关内容",
          "No matching content found");
        m("agent.character_created",
          "角色「%s」创建成功 (ID: %s)",
          "Character \"%s\" created (ID: %s)");
        m("agent.character_updated",
          "角色「%s」已更新",
          "Character \"%s\" updated");
        m("agent.character_deleted",
          "角色「%s」已删除",
          "Character \"%s\" deleted");
        m("agent.worldview_created",
          "世界观条目「%s」创建成功 (ID: %s)",
          "Worldview entry \"%s\" created (ID: %s)");
        m("agent.worldview_updated",
          "世界观条目「%s」已更新",
          "Worldview entry \"%s\" updated");
        m("agent.worldview_deleted",
          "世界观条目「%s」已删除",
          "Worldview entry \"%s\" deleted");
        m("agent.config_saved_reconciling",
          "故事配置已保存，正在自动协调已有内容...",
          "Story config saved — reconciling existing content...");
        m("agent.config_saved",
          "故事配置已保存",
          "Story config saved");
        m("agent.outline_task_started",
          "大纲生成任务已启动，请等待完成。",
          "Outline generation started — please wait.");
        m("agent.outline_confirmed",
          "大纲已确认，现在进入写作阶段。",
          "Outline confirmed — entering writing phase.");
        m("agent.outline_revise_started",
          "大纲修订任务已启动，请等待完成。",
          "Outline revision started — please wait.");
        m("agent.outline_deleted",
          "大纲已删除。",
          "Outline deleted.");
        m("agent.chapter_outline_updated",
          "第 %d 章大纲已更新。",
          "Chapter %d outline updated.");
        m("agent.chapter_task_started",
          "第 %d 章生成任务已启动，请等待完成。",
          "Chapter %d generation started — please wait.");
        m("agent.chapter_confirmed",
          "第 %d 章《%s》已确认。",
          "Chapter %d \"%s\" confirmed.");
        m("agent.chapter_revise_started",
          "第 %d 章修订任务已启动（仅修改该章，不影响其他章节），请等待完成。",
          "Chapter %d revision started (this chapter only) — please wait.");
        m("agent.chapter_deleted",
          "已删除第 %d 章。",
          "Deleted chapter %d.");
        m("agent.chapter_content_edited",
          "第 %d 章正文已编辑（操作: %s，共 %d 行）。",
          "Chapter %d content edited (op: %s, %d lines total).");
        m("agent.chapter_edit_op_required",
          "缺少 operation 参数，必须为 replace_lines / replace_text / insert_after_line / append 之一",
          "Missing operation parameter; must be one of: replace_lines / replace_text / insert_after_line / append");
        m("agent.chapter_edit_text_required",
          "new_text 不能为空",
          "new_text must not be empty");
        m("agent.chapters_bulk_delete_confirm",
          "⚠️ 操作未执行：这将永久删除第 %d 章到末尾共 %d 章的全部内容。请先向用户复述此影响范围并获得明确同意，确认后携带 confirm=true 重新调用。如果用户的本意是修改章节内容，请改用 revise_chapter。",
          "⚠️ Not executed: this would permanently delete chapters %d through end (%d chapters). Restate the impact and get consent, then retry with confirm=true. To edit content, use revise_chapter.");
        m("agent.chapters_deleted_from",
          "已从第 %d 章删除到末尾，共删除 %d 章。",
          "Deleted from chapter %d to end (%d chapters).");
        m("agent.organization_created",
          "组织「%s」创建成功 (ID: %s)",
          "Organization \"%s\" created (ID: %s)");
        m("agent.organization_updated",
          "组织「%s」已更新",
          "Organization \"%s\" updated");
        m("agent.organization_deleted",
          "组织「%s」已删除",
          "Organization \"%s\" deleted");
        m("agent.organization_not_found",
          "未找到组织: %s",
          "Organization not found: %s");
        m("agent.relation_created",
          "关系创建成功 (ID: %s)",
          "Relation created (ID: %s)");
        m("agent.relation_updated",
          "关系已更新 (ID: %s)",
          "Relation updated (ID: %s)");
        m("agent.relation_deleted",
          "关系已删除",
          "Relation deleted");
        m("agent.relation_not_found",
          "未找到关系: %s",
          "Relation not found: %s");
        m("agent.foreshadow_suggest_started",
          "伏笔建议生成任务已启动，请等待完成。",
          "Foreshadow suggestion task started — please wait.");
        m("agent.foreshadow_created",
          "伏笔「%s」创建成功 (ID: %d)",
          "Foreshadow \"%s\" created (ID: %d)");
        m("agent.foreshadow_updated",
          "伏笔「%s」已更新",
          "Foreshadow \"%s\" updated");
        m("agent.foreshadow_deleted",
          "伏笔「%s」已删除",
          "Foreshadow \"%s\" deleted");
        m("agent.foreshadow_not_found",
          "伏笔 %d 不存在",
          "Foreshadow %d not found");
        m("agent.skill_toggled",
          "技能「%s」已%s",
          "Skill \"%s\" %s");
        m("agent.progress_reset",
          "进度已重置。",
          "Progress reset.");

        // ============================================================
        // errorCatalog  (from Go locale.go)
        // ============================================================

        e("missing_project_name",
          "缺少项目名称",
          "Project name is required");
        e("project_name_invalid_chars",
          "项目名称包含非法字符",
          "Project name contains invalid characters");
        e("project_exists",
          "项目已存在",
          "Project already exists");
        e("create_project_dir_failed",
          "创建项目目录失败: %s",
          "Failed to create project directory: %s");
        e("init_project_config_failed",
          "初始化项目配置失败: %s",
          "Failed to initialise project config: %s");
        e("select_project_first",
          "请先选择一个项目",
          "Please select a project first");
        e("task_running_locked",
          "有AI任务正在运行，暂不能修改，请等待任务完成或先停止任务",
          "An AI task is running; please wait or stop it before editing");
        e("task_running_wait",
          "有任务正在运行，请等待完成",
          "A task is running; please wait until it finishes");
        e("no_task_running",
          "没有正在运行的任务",
          "No task is currently running");
        e("invalid_json",
          "无效的JSON: %s",
          "Invalid JSON: %s");
        e("missing_feedback",
          "缺少 feedback 字段",
          "feedback field is required");
        e("missing_content",
          "缺少 content 字段",
          "content field is required");
        e("invalid_chapter_num",
          "无效的章节编号",
          "Invalid chapter number");
        e("chapter_not_found",
          "章节不存在",
          "Chapter not found");
        e("chapter_n_not_found",
          "章节 %s 不存在",
          "Chapter %s not found");
        e("phase_not_outline",
          "当前不在大纲阶段",
          "Not in outline phase");
        e("phase_not_writing",
          "当前不在写作阶段",
          "Not in writing phase");
        e("outline_empty",
          "大纲为空，请先生成大纲",
          "Outline is empty; generate an outline first");
        e("outline_confirm_failed",
          "确认大纲失败: %s",
          "Failed to confirm outline: %s");
        e("writing_chapter_present",
          "有正在写作/审核中的章节，请先处理后再重新生成大纲",
          "There are chapters in writing/review; finish them before regenerating the outline");
        e("accepted_chapter_present",
          "存在已确认章节，无法整体重新生成大纲。如需追加章节请使用「生成后续大纲」",
          "Confirmed chapters exist; cannot regenerate the full outline. Use \"Generate Continuation Outline\" to append.");
        e("writing_chapter_present_delete",
          "有正在写作/审核中的章节，请先处理后再删除大纲",
          "There are chapters in writing/review; finish them before deleting the outline");
        e("reset_progress_locked",
          "有任务正在运行，无法重置进度",
          "A task is running; cannot reset progress");
        e("delete_chapter_locked",
          "有任务正在运行，无法删除章节",
          "A task is running; cannot delete chapter");
        e("delete_outline_locked",
          "有任务正在运行，无法删除大纲",
          "A task is running; cannot delete outline");
        e("delete_project_locked",
          "有任务正在运行，无法删除项目",
          "A task is running; cannot delete project");
        e("cannot_delete_current_project",
          "不能删除当前正在使用的项目",
          "Cannot delete the currently active project");
        e("project_not_found",
          "项目不存在",
          "Project not found");
        e("delete_project_failed",
          "删除项目失败: %s",
          "Failed to delete project: %s");
        e("delete_progress_failed",
          "删除进度文件失败: %s",
          "Failed to delete progress file: %s");
        e("no_chapters_to_delete",
          "没有可删除的章节",
          "No chapters to delete");
        e("writing_chapter_cannot_delete",
          "正在写作中的章节无法删除",
          "Cannot delete a chapter that is being written");
        e("writing_range_has_writing",
          "删除范围内有正在写作中的章节，无法删除",
          "Delete range contains a chapter being written; cannot delete");
        e("save_progress_failed",
          "保存进度失败: %s",
          "Failed to save progress: %s");
        e("save_failed",
          "保存失败: %s",
          "Save failed: %s");
        e("save_config_failed",
          "保存配置失败: %s",
          "Failed to save config: %s");
        e("save_api_config_failed",
          "保存API配置失败: %s",
          "Failed to save API config: %s");
        e("serialize_config_failed",
          "序列化配置失败: %s",
          "Failed to serialise config: %s");
        e("serialize_api_config_failed",
          "序列化API配置失败: %s",
          "Failed to serialise API config: %s");
        e("api_test_timeout",
          "连接超时（15秒）",
          "Connection timed out (15s)");
        e("api_test_failed",
          "测试失败: %s",
          "Test failed: %s");
        e("api_test_success",
          "连接成功",
          "Connection succeeded");
        e("character_name_empty",
          "角色名不能为空",
          "Character name is required");
        e("character_not_found",
          "角色不存在",
          "Character not found");
        e("worldview_field_empty",
          "名称和描述不能为空",
          "Name and description are required");
        e("worldview_not_found",
          "世界观条目不存在",
          "Worldview entry not found");
        e("organization_name_empty",
          "组织名不能为空",
          "Organization name is required");
        e("organization_not_found",
          "组织不存在",
          "Organization not found");
        e("relation_endpoints_empty",
          "源和目标不能为空",
          "Source and target are required");
        e("relation_not_found",
          "关系不存在",
          "Relation not found");
        e("foreshadow_name_required",
          "缺少 name",
          "name field is required");
        e("foreshadow_desc_required",
          "缺少 description",
          "description field is required");
        e("foreshadow_not_found",
          "伏笔不存在",
          "Foreshadow not found");
        e("invalid_foreshadow_id",
          "无效的伏笔ID",
          "Invalid foreshadow id");
        e("need_generate_outline_first",
          "请先生成大纲",
          "Generate an outline first");
        e("continue_reset_first",
          "续写前请先重置进度",
          "Reset progress before importing continuation");
        e("continue_analyze_first",
          "请先分析内容",
          "Analyse the content first");
        e("analysis_no_chapters",
          "分析结果中没有任何章节",
          "Analysis result contains no chapters");
        e("continue_import_failed",
          "导入续写失败: %s",
          "Failed to import continuation: %s");
        e("book_not_complete",
          "全书尚未完成（需所有章节已确认）",
          "Book is not yet complete (all chapters must be confirmed)");
        e("need_polish_skill",
          "没有启用的润色技能，请先在技能管理页启用 polish 类技能",
          "No polish skill enabled; enable a polish-type skill on the Skills page first");
        e("chapter_content_empty",
          "章节内容为空，无法润色",
          "Chapter content is empty; cannot polish");
        e("chapter_edit_op_required",
          "缺少 operation 参数，必须为 replace_lines / replace_text / insert_after_line / append 之一",
          "Missing operation parameter; must be one of: replace_lines / replace_text / insert_after_line / append");
        e("chapter_edit_text_required",
          "new_text 不能为空",
          "new_text must not be empty");
        e("chapter_edit_failed",
          "章节编辑失败: %s",
          "Chapter edit failed: %s");
        e("chapter_in_writing",
          "章节正在写作中，无法润色",
          "Chapter is being written; cannot polish");
        e("chapter_num_required",
          "请指定章节编号",
          "Chapter number is required");
        e("no_transitions_to_optimize",
          "没有可优化的章节（需要至少两个相邻的已确认章节）",
          "No transitions to optimise (need at least two adjacent confirmed chapters)");
        e("missing_diagnosis_or_consistency",
          "缺少诊断或核查报告，请先运行全书诊断",
          "Diagnosis or consistency report is missing; run book diagnosis first");
        e("no_roadmap_items",
          "没有可执行的优化工单",
          "No roadmap items to execute");
        e("select_at_least_one_item",
          "请至少勾选一条待执行的工单",
          "Select at least one pending roadmap item");
        e("clear_postprocess_failed",
          "清空失败: %s",
          "Failed to clear: %s");
        e("chat_session_not_found",
          "会话不存在",
          "Chat session not found");
        e("load_session_list_failed",
          "加载会话列表失败: %s",
          "Failed to load chat sessions: %s");
        e("create_session_failed",
          "创建会话失败: %s",
          "Failed to create chat session: %s");
        e("save_session_failed",
          "保存会话失败: %s",
          "Failed to save chat session: %s");
        e("delete_session_failed",
          "删除会话失败: %s",
          "Failed to delete chat session: %s");
        e("skill_not_found",
          "技能不存在",
          "Skill not found");
        e("settings_ai_generate_moved",
          "此功能已移至 LLM 对话中，请通过聊天让 AI 帮你生成设定",
          "This action has moved into the LLM chat; ask the assistant to generate settings for you");
        e("settings_polish_moved",
          "此功能已移至 LLM 对话中，请通过聊天让 AI 帮你润色",
          "This action has moved into the LLM chat; ask the assistant to polish for you");
        e("writing_conflict_none",
          "当前没有待处理的写作冲突",
          "No pending writing conflict to resolve");
        e("missing_action",
          "缺少 action 字段",
          "action field is required");
        e("invalid_conflict_chapter_idx",
          "冲突章节索引无效",
          "Invalid conflict chapter index");
        e("unsupported_action",
          "不支持的 action: %s",
          "Unsupported action: %s");
        e("no_foreshadows_to_check",
          "当前没有伏笔，无需检查",
          "No foreshadows to check");
    }

    // ---------------------------------------------------------------
    // Static helpers for populating the maps
    // ---------------------------------------------------------------

    private static void m(String key, String zh, String en) {
        messageCatalog.put(key, new String[]{zh, en});
    }

    private static void e(String key, String zh, String en) {
        errorCatalog.put(key, new String[]{zh, en});
    }
}
