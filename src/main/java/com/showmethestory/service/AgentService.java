package com.showmethestory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.showmethestory.i18n.LocaleHelper;
import com.showmethestory.model.Character;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final StateService stateService;
    private final SettingsService settingsService;
    private final OpenAIClient openAIClient;
    private final LogBroadcaster logger;
    private final SkillService skillService;
    private final ProjectService projectService;
    private final TaskManager taskManager;
    private final FileSystemService fileSystemService;
    private final EditingService editingService;
    private final ForeshadowService foreshadowService;
    private final OutlineService outlineService;
    private final ChapterService chapterService;
    private final ReconcileService reconcileService;

    private String toolMsgKey = "";
    private String toolMsgArgsStr = "";

    public AgentService(StateService stateService, SettingsService settingsService,
                        OpenAIClient openAIClient, LogBroadcaster logger,
                        SkillService skillService, ProjectService projectService,
                        TaskManager taskManager, FileSystemService fileSystemService,
                        EditingService editingService, ForeshadowService foreshadowService,
                        OutlineService outlineService, ChapterService chapterService,
                        ReconcileService reconcileService) {
        this.stateService = stateService;
        this.settingsService = settingsService;
        this.openAIClient = openAIClient;
        this.logger = logger;
        this.skillService = skillService;
        this.projectService = projectService;
        this.taskManager = taskManager;
        this.fileSystemService = fileSystemService;
        this.editingService = editingService;
        this.foreshadowService = foreshadowService;
        this.outlineService = outlineService;
        this.chapterService = chapterService;
        this.reconcileService = reconcileService;
    }

    @FunctionalInterface
    interface ToolExecutor { String execute(JsonNode args, AgentContext ctx) throws Exception; }

    static class Tool {
        final String name, description, parameters;
        final ToolExecutor executor;
        Tool(String n, String d, String p, ToolExecutor e) { name=n; description=d; parameters=p; executor=e; }
    }

    public void runChatAgentLoop(OpenAIClient.CancellationToken ct, ChatSession session,
                                  String content, String contextPage) {
        try {
            AgentContext ctx = buildAgentContext();
            ctx.setContextPage(contextPage);
            List<AgentStep> history = sessionToHistory(session);
            String[] result = runAgentLoop(ct, ctx, content, history, 20);
            ChatMessage msg = new ChatMessage();
            msg.setRole("assistant"); msg.setContent(result[0]); msg.setTimestamp(session.getUpdatedAt());
            session.getMessages().add(msg);
        } catch (Exception e) {
            log.error("[Agent] failed", e);
            ChatMessage em = new ChatMessage(); em.setRole("assistant");
            em.setContent("Agent error: " + e.getMessage()); session.getMessages().add(em);
        }
    }

    private AgentContext buildAgentContext() {
        AgentContext ctx = new AgentContext();
        ctx.setApiCfg(projectService.getAPIConfig()); ctx.setConfig(projectService.getConfig());
        ctx.setState(projectService.getProgress()); ctx.setSettings(projectService.getSettings());
        ctx.setSkills(projectService.getSkills()); ctx.setProgressPath(projectService.getProgressPath());
        ctx.setCfgPath(projectService.getCfgPath()); ctx.setSettingsPath(projectService.getSettingsPath());
        ctx.setSessionsDir(projectService.getSessionsDir()); ctx.setProjectDir(projectService.getProjectDir());
        return ctx;
    }

    private List<AgentStep> sessionToHistory(ChatSession session) {
        List<AgentStep> h = new ArrayList<>();
        if (session.getMessages() == null) return h;
        for (ChatMessage m : session.getMessages()) {
            AgentStep s = new AgentStep(); s.setRole(m.getRole()); s.setContent(m.getContent()); h.add(s);
        }
        return h;
    }

    private String[] runAgentLoop(OpenAIClient.CancellationToken ct, AgentContext ctx,
                                   String userMessage, List<AgentStep> history, int maxSteps) {
        List<Tool> tools = getBuiltinTools();
        String toolDesc = buildToolDescriptions(tools);
        String systemPrompt = buildAgentSystemPrompt(ctx, toolDesc);
        String lang = projectLang(ctx);
        String trl = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(ctx.getConfig().getLanguage()))
                ? "[Tool result]" : "[\u5de5\u5177\u7ed3\u679c]";
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        int lastUserIdx = -1;
        for (int i = history.size() - 1; i >= 0; i--)
            if ("user".equals(history.get(i).getRole())) { lastUserIdx = i; break; }
        for (int i = 0; i < history.size(); i++) {
            AgentStep step = history.get(i);
            if ("user".equals(step.getRole())) {
                if (i == lastUserIdx) continue;
                messages.add(new Message("user", step.getContent()));
            } else if ("assistant".equals(step.getRole())) {
                if (step.getToolCall() != null) {
                    try { String tc = OM.writeValueAsString(step.getToolCall());
                        messages.add(new Message("assistant", "<tool_call>\n" + tc + "\n</tool_call>"));
                    } catch (Exception e) { messages.add(new Message("assistant", step.getContent())); }
                } else messages.add(new Message("assistant", step.getContent()));
            } else if ("tool".equals(step.getRole())) {
                messages.add(new Message("user", trl + "\n" + step.getToolResult()));
            }
        }
        messages.add(new Message("user", userMessage));

        for (int step = 0; step < maxSteps; step++) {
            if (ct != null && ct.isCancelled()) return new String[]{agentMsg(ctx, "agent.task_cancelled")};
            logger.info(String.format("[Agent] Step %d/%d: %d msgs", step+1, maxSteps, messages.size()));
            StringBuilder fullResp = new StringBuilder();
            try {
                APIConfig ac = cloneApiConfig(ctx.getApiCfg());
                if (ac.getMaxTokens() < 8192) ac.setMaxTokens(8192);
                openAIClient.callAPIStreamMessages(ct, ac, messages, fullResp::append, null);
            } catch (Exception se) {
                if (ct != null && ct.isCancelled()) return new String[]{agentMsg(ctx, "agent.task_cancelled")};
                try {
                    APIConfig ac = cloneApiConfig(ctx.getApiCfg());
                    if (ac.getMaxTokens() < 8192) ac.setMaxTokens(8192);
                    String r = openAIClient.callAPIMessages(ct, ac, messages, null);
                    fullResp.setLength(0); fullResp.append(r);
                } catch (Exception fe) {
                    logger.error("[Agent] API failed: " + fe.getMessage());
                    return new String[]{agentMsg(ctx, "agent.api_failed", fe.getMessage())};
                }
            }
            String resp = fullResp.toString();
            logger.info("[Agent] Step " + (step+1) + ": " + resp.length() + " chars");
            ToolCall tc = parseToolCall(resp);
            if (tc == null) {
                AgentStep as = new AgentStep(); as.setRole("assistant"); as.setContent(resp); history.add(as);
                return new String[]{resp};
            }
            logger.info("[Agent] Step " + (step+1) + ": Tool -> " + tc.getName());
            AgentStep aStep = new AgentStep();
            aStep.setRole("assistant"); aStep.setContent(stripToolCallTags(resp)); aStep.setToolCall(tc);
            history.add(aStep);
            logger.toolCallStart("", tc.getName(), tc.getArguments() != null ? tc.getArguments().toString() : "{}");
            clearToolMsg();
            String result = null, rKey = null, rArgs = "";
            boolean found = false;
            for (Tool t : tools) {
                if (t.name.equals(tc.getName())) {
                    found = true;
                    try {
                        result = t.executor.execute(tc.getArguments(), ctx);
                        String[] m = takeToolMsg(); rKey = m[0]; rArgs = m[1];
                    } catch (Exception e) {
                        result = LocaleHelper.t(lang, "agent.tool_exec_error", e.getMessage());
                        rKey = "agent.tool_exec_error"; rArgs = e.getMessage();
                    }
                    break;
                }
            }
            if (!found) {
                result = LocaleHelper.t(lang, "agent.unknown_tool", tc.getName());
                rKey = "agent.unknown_tool"; rArgs = tc.getName();
            }
            if (result == null) result = "";
            logger.info("[Agent] " + tc.getName() + " -> " + truncate(result, 100));
            AgentStep tStep = new AgentStep();
            tStep.setRole("tool"); tStep.setToolResult(result); tStep.setToolResultKey(rKey);
            if (rArgs != null && !rArgs.isEmpty()) tStep.setToolResultArgs(Arrays.asList(rArgs.split(",")));
            history.add(tStep);
            String[] ra = (rArgs != null && !rArgs.isEmpty()) ? rArgs.split(",") : new String[0];
            logger.toolCallEnd("", tc.getName(), truncate(result, 200), rKey, ra);
            try { String j = OM.writeValueAsString(tc);
                messages.add(new Message("assistant", "<tool_call>\n" + j + "\n</tool_call>"));
            } catch (Exception e) { messages.add(new Message("assistant", "")); }
            messages.add(new Message("user", trl + "\n" + result));
        }
        return new String[]{agentMsg(ctx, "agent.max_steps")};
    }

    private APIConfig cloneApiConfig(APIConfig s) {
        APIConfig c = new APIConfig();
        c.setBaseUrl(s.getBaseUrl()); c.setApiKey(s.getApiKey()); c.setModel(s.getModel());
        c.setMaxTokens(s.getMaxTokens()); c.setTemperature(s.getTemperature()); c.setTimeout(s.getTimeout());
        return c;
    }

    private String buildAgentSystemPrompt(AgentContext ctx, String toolDesc) {
        if (LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(ctx.getConfig().getLanguage())))
            return buildAgentSystemPromptEN(ctx, toolDesc);
        return buildAgentSystemPromptZH(ctx, toolDesc);
    }

    private String buildAgentSystemPromptZH(AgentContext ctx, String toolDesc) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("\u4f60\u662f\u4e00\u4e2a\u5c0f\u8bf4\u521b\u4f5c\u52a9\u624b\uff0c\u5168\u6743\u8d1f\u8d23\u7ba1\u7406\u9879\u76ee\u7684\u4e00\u5207\u64cd\u4f5c\u3002\n\n## \u9879\u76ee\u4fe1\u606f\n");
        if (nvl(ctx.getState().getTitle()).length() > 0) sb.append(String.format("\u5c0f\u8bf4\u6807\u9898: \u300a%s\u300b\n", ctx.getState().getTitle()));
        sb.append(String.format("\u5f53\u524d\u9636\u6bb5: %s\n\u7ae0\u8282\u6570: %d\n", nvl(ctx.getState().getPhase()),
                ctx.getState().getChapters() != null ? ctx.getState().getChapters().size() : 0));
        if (ctx.getSettings() != null) sb.append(String.format("\u89d2\u8272\u6570: %d\n\u4e16\u754c\u89c2: %d\n\u7ec4\u7ec7\u6570: %d\n",
                listSize(ctx.getSettings().getCharacters()), listSize(ctx.getSettings().getWorldview()), listSize(ctx.getSettings().getOrganizations())));
        appendContextPage(sb, ctx, "zh"); sb.append("\n");
        appendEnabledSkills(sb, ctx);
        sb.append("## \u53ef\u7528\u5de5\u5177\n").append(toolDesc).append("\n\n");
        sb.append("## \u5de5\u5177\u8c03\u7528\u683c\u5f0f\n\u4e25\u683c\u4f7f\u7528\u4ee5\u4e0b\u683c\u5f0f\uff0c\u5fc5\u987b\u662f\u5408\u6cd5JSON\uff1a\n<tool_call>\n");
        sb.append("{\"name\": \"\u5de5\u5177\u540d\", \"arguments\": {\"\u53c2\u6570\": \"\u503c\"}}");
        sb.append("\n</tool_call>\n\n\u4e00\u6b21\u53ea\u80fd\u8c03\u7528\u4e00\u4e2a\u5de5\u5177\u3002\u4e0d\u9700\u8981\u65f6\u76f4\u63a5\u56de\u590d\u3002\n\n");
        sb.append("## \u5b89\u5168\u89c4\u5219\n1. \u4fee\u6539\u2260\u5220\u9664\uff0c\u7528 revise_chapter\u3002\n2. \u5220\u9664\u5de5\u5177\u4ec5\u5f53\u7528\u6237\u660e\u786e\u8981\u6c42\u65f6\u4f7f\u7528\u3002\n\n");
        sb.append("## \u91cd\u8981\u89c4\u5219\n- \u5f02\u6b65\u5de5\u5177\u7acb\u5373\u8fd4\u56de\u3002\n- \u8c03\u7528\u5de5\u5177\u65f6\u4e0d\u8981\u89e3\u91ca\u3002\n- \u5b8c\u6210\u540e\u5efa\u8bae\u540e\u7eed\u64cd\u4f5c\u3002\n");
        return sb.toString();
    }

    private String buildAgentSystemPromptEN(AgentContext ctx, String toolDesc) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("You are a novel-writing assistant.\n\n## Project info\n");
        if (nvl(ctx.getState().getTitle()).length() > 0) sb.append(String.format("Title: \"%s\"\n", ctx.getState().getTitle()));
        sb.append(String.format("Phase: %s\nChapters: %d\n", nvl(ctx.getState().getPhase()),
                ctx.getState().getChapters() != null ? ctx.getState().getChapters().size() : 0));
        if (ctx.getSettings() != null) sb.append(String.format("Characters: %d\nWorldview: %d\nOrgs: %d\n",
                listSize(ctx.getSettings().getCharacters()), listSize(ctx.getSettings().getWorldview()), listSize(ctx.getSettings().getOrganizations())));
        appendContextPage(sb, ctx, "en"); sb.append("\n");
        appendEnabledSkills(sb, ctx);
        sb.append("## Available tools\n").append(toolDesc).append("\n\n");
        sb.append("## Tool-call format\nUse valid JSON:\n<tool_call>\n");
        sb.append("{\"name\": \"tool_name\", \"arguments\": {\"arg\": \"val\"}}");
        sb.append("\n</tool_call>\n\nCall one tool at a time. If no tool needed, reply directly.\n\n");
        sb.append("## Safety rules\n1. Edit != Delete. Use revise_chapter.\n2. Delete only when user explicitly asks.\n\n");
        sb.append("## Important rules\n- Async tools return immediately.\n- No explanatory text when calling tools.\n- Suggest next actions after operations.\n");
        return sb.toString();
    }

    private void appendContextPage(StringBuilder sb, AgentContext ctx, String lang) {
        if (ctx.getContextPage() == null || ctx.getContextPage().isEmpty()) return;
        Map<String,String> zh = Map.of("config","\u914d\u7f6e","outline","\u5927\u7eb2","writing","\u5199\u4f5c","relations","\u56fe\u8c31","skills","\u6280\u80fd");
        Map<String,String> en = Map.of("config","Config","outline","Outline","writing","Writing","relations","Relations","skills","Skills");
        Map<String,String> names = "en".equals(lang) ? en : zh;
        String name = names.get(ctx.getContextPage());
        if (name != null) sb.append("en".equals(lang)
                ? String.format("\nUser viewing \"%s\" page.\n", name)
                : String.format("\n\u7528\u6237\u6b63\u5728\u67e5\u770b\u300c%s\u300d\u9875\u9762\u3002\n", name));
    }

    private void appendEnabledSkills(StringBuilder sb, AgentContext ctx) {
        SkillConfig sc = ctx.getConfig() != null ? ctx.getConfig().getSkillConfig() : null;
        List<Skill> en = skillService.getEnabledSkills(ctx.getSkills() != null ? ctx.getSkills() : Collections.emptyList(), sc);
        if (en != null && !en.isEmpty()) {
            boolean isEn = LocaleHelper.LANG_EN.equals(LocaleHelper.normalizeLanguage(ctx.getConfig().getLanguage()));
            sb.append(isEn ? "## Enabled skills\n" : "## \u5df2\u542f\u7528\u6280\u80fd\n");
            sb.append(skillService.formatSkillsContent(en)).append("\n");
        }
    }

    private String buildToolDescriptions(List<Tool> tools) {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools) sb.append(String.format("- **%s**: %s\n  \u53c2\u6570: %s\n", t.name, t.description, t.parameters));
        return sb.toString();
    }

    private String stripToolCallTags(String content) {
        StringBuilder result = new StringBuilder();
        String rem = content;
        while (true) {
            int start = rem.indexOf("<tool_call>");
            if (start == -1) { result.append(rem); break; }
            result.append(rem, 0, start);
            int end = rem.indexOf("</tool_call>", start);
            if (end == -1) break;
            rem = rem.substring(end + 12);
        }
        return result.toString().trim();
    }

    ToolCall parseToolCall(String content) {
        if (content == null) return null;
        content = content.trim();
        int idx = content.indexOf("<tool_call>");
        if (idx == -1) {
            ToolCall tc = parseToolCallFunctionName(content);
            if (tc != null) return tc;
            return parseToolCallJSON(content);
        }
        int endIdx = content.indexOf("</tool_call>", idx);
        if (endIdx == -1) {
            String inner = content.substring(idx + 11).trim();
            ToolCall tc = parseToolCallFromJSON(inner);
            if (tc != null) return tc;
            tc = parseToolCallJSON(inner);
            if (tc != null) return tc;
            tc = parseToolCallFunctionName(content);
            if (tc != null) return tc;
            return parseToolCallJSON(content);
        }
        String inner = content.substring(idx + 11, idx + endIdx).trim();
        ToolCall tc = parseToolCallFromJSON(inner);
        if (tc != null) return tc;
        tc = parseToolCallFromXML(inner);
        if (tc != null) return tc;
        String remaining = content.substring(idx + endIdx + 12);
        tc = parseToolCallJSON(remaining);
        if (tc != null) return tc;
        tc = parseToolCallJSON(content);
        if (tc != null) return tc;
        return parseToolCallFunctionName(content);
    }

    private ToolCall parseToolCallFunctionName(String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (!line.startsWith("function.")) continue;
            String rest = line.substring("function.".length());
            int pi = rest.indexOf("(");
            if (pi == -1) continue;
            String name = rest.substring(0, pi);
            if (name.isEmpty()) continue;
            String argsStr = rest.substring(pi + 1).trim();
            if (argsStr.endsWith(")")) argsStr = argsStr.substring(0, argsStr.length() - 1).trim();
            JsonNode args;
            try { args = OM.readTree(argsStr.isEmpty() ? "{}" : argsStr); }
            catch (Exception e) { args = OM.createObjectNode(); }
            return new ToolCall(name, args);
        }
        return null;
    }

    private ToolCall parseToolCallFromXML(String inner) {
        int ns = inner.indexOf("<name>"), ne = inner.indexOf("</name>");
        if (ns == -1 || ne == -1 || ne <= ns) return null;
        String name = inner.substring(ns + 6, ne).trim();
        if (name.isEmpty()) return null;
        JsonNode args = OM.createObjectNode();
        int as2 = inner.indexOf("<arguments>"), ae = inner.indexOf("</arguments>");
        if (as2 != -1 && ae != -1 && ae > as2) {
            String aStr = inner.substring(as2 + 11, ae).trim();
            if (!aStr.isEmpty()) try { args = OM.readTree(aStr); } catch (Exception e) {}
        }
        return new ToolCall(name, args);
    }

    private ToolCall parseToolCallJSON(String content) {
        if (content == null) return null;
        String rem = content;
        while (true) {
            int start = rem.indexOf("{");
            if (start == -1) return null;
            rem = rem.substring(start);
            String jsonStr = extractJSON(rem);
            if (jsonStr == null || jsonStr.isEmpty()) return null;
            ToolCall tc = parseToolCallFromJSON(jsonStr);
            if (tc != null) return tc;
            rem = rem.substring(jsonStr.length());
        }
    }

    private ToolCall parseToolCallFromJSON(String jsonStr) {
        try {
            JsonNode node = OM.readTree(jsonStr);
            if (!node.isObject()) return null;
            JsonNode nameNode = node.has("name") ? node.get("name") : node.get("tool");
            if (nameNode == null) return null;
            String name = nameNode.asText();
            JsonNode args = node.has("arguments") ? node.get("arguments") : OM.createObjectNode();
            return new ToolCall(name, args);
        } catch (Exception e) { return null; }
    }

    private String extractJSON(String content) {
        int start = content.indexOf('{');
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            if (content.charAt(i) == '{') depth++;
            else if (content.charAt(i) == '}') { depth--; if (depth == 0) return content.substring(start, i + 1); }
        }
        return null;
    }


    // ==================== i18n helpers ====================
    private void clearToolMsg() { toolMsgKey = ""; toolMsgArgsStr = ""; }
    private void setToolMsg(String key, String args) { toolMsgKey = key; toolMsgArgsStr = args; }
    private String[] takeToolMsg() { String[] r = {toolMsgKey, toolMsgArgsStr}; clearToolMsg(); return r; }
    private String projectLang(AgentContext ctx) {
        if (ctx == null || ctx.getConfig() == null) return LocaleHelper.LANG_ZH;
        return LocaleHelper.normalizeLanguage(ctx.getConfig().getLanguage());
    }
    private String agentMsg(AgentContext ctx, String key, Object... args) {
        String lang = projectLang(ctx);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) { if (i > 0) sb.append(","); sb.append(String.valueOf(args[i])); }
        setToolMsg(key, sb.toString());
        return LocaleHelper.t(lang, key, args);
    }
    private Exception agentErr(AgentContext ctx, String key, Object... args) {
        return new Exception(LocaleHelper.t(projectLang(ctx), key, args));
    }

    // ==================== utilities ====================
    private static String nvl(String s) { return s != null ? s : ""; }
    private static int listSize(List<?> l) { return l != null ? l.size() : 0; }
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
    private static int findChapterIdx(Progress state, int num) {
        if (state.getChapters() == null) return -1;
        for (int i = 0; i < state.getChapters().size(); i++)
            if (state.getChapters().get(i).getNum() == num) return i;
        return -1;
    }
    private static String foreshadowStatusLabel(String status) {
        if (status == null) return "planted";
        return switch (status) { case "resolved" -> "resolved"; case "progressing" -> "progressing";
                case "abandoned" -> "abandoned"; default -> "planted"; };
    }
    private void saveChapterMarkdown(String projectDir, ChapterState ch, String title) {
        try {
            String path = projectService.chapterMarkdownPath(projectDir, ch.getNum());
            Files.writeString(Paths.get(path), ch.getContent() != null ? ch.getContent() : "");
        } catch (Exception e) { log.warn("Failed to save chapter markdown", e); }
    }
    private String requireConfirm(AgentContext ctx, JsonNode args, String action) {
        boolean confirm = args != null && args.has("confirm") && args.get("confirm").asBoolean(false);
        if (confirm) return "";
        return agentMsg(ctx, "agent.confirm_required", action);
    }
    private JsonNode strArg(String val) {
        ObjectNode n = OM.createObjectNode(); n.put("v", val); return n;
    }
    private String argStr(JsonNode args, String field) {
        return args != null && args.has(field) ? args.get(field).asText("") : "";
    }
    private int argInt(JsonNode args, String field, int def) {
        return args != null && args.has(field) ? args.get(field).asInt(def) : def;
    }
    private boolean argBool(JsonNode args, String field) {
        return args != null && args.has(field) && args.get(field).asBoolean(false);
    }

    // ==================== 40+ built-in tools ====================
    private List<Tool> getBuiltinTools() {
        List<Tool> tools = new ArrayList<>();

        tools.add(new Tool("read_characters", "\u83b7\u53d6\u89d2\u8272\u5217\u8868", "{\"filter\": \"\u53ef\u9009\"}",
            (args, ctx) -> {
                String filter = argStr(args, "filter");
                if (ctx.getSettings() == null) return agentMsg(ctx, "agent.no_characters");
                StringBuilder sb = new StringBuilder();
                for (Character c : ctx.getSettings().getCharacters()) {
                    if (!filter.isEmpty() && !c.getName().contains(filter)) continue;
                    sb.append(String.format("\u3010%s\u3011(ID:%s)\n", c.getName(), c.getId()));
                    if (nvl(c.getAge()).length() > 0) sb.append("  \u5e74\u9f84: " + c.getAge() + "\n");
                    if (nvl(c.getPersonality()).length() > 0) sb.append("  \u6027\u683c: " + c.getPersonality() + "\n");
                    if (nvl(c.getBackground()).length() > 0) sb.append("  \u80cc\u666f: " + c.getBackground() + "\n");
                    sb.append("\n");
                }
                return sb.length() == 0 ? agentMsg(ctx, "agent.characters_not_found") : sb.toString();
            }));

        tools.add(new Tool("read_character", "\u83b7\u53d6\u5355\u4e2a\u89d2\u8272\u8be6\u60c5", "{\"id\": \"\u89d2\u8272ID\u6216\u540d\u79f0\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                if (ctx.getSettings() == null) return agentMsg(ctx, "agent.no_characters");
                for (Character c : ctx.getSettings().getCharacters())
                    if (id.equals(c.getId()) || id.equals(c.getName())) return OM.writerWithDefaultPrettyPrinter().writeValueAsString(c);
                return agentMsg(ctx, "agent.character_not_found", id);
            }));

        tools.add(new Tool("read_worldview", "\u83b7\u53d6\u4e16\u754c\u89c2\u5217\u8868", "{\"category\": \"\u53ef\u9009\"}",
            (args, ctx) -> {
                String cat = argStr(args, "category");
                if (ctx.getSettings() == null || listSize(ctx.getSettings().getWorldview()) == 0) return agentMsg(ctx, "agent.no_worldview");
                StringBuilder sb = new StringBuilder();
                for (WorldviewEntry w : ctx.getSettings().getWorldview()) {
                    if (!cat.isEmpty() && !cat.equals(w.getCategory())) continue;
                    sb.append(String.format("\u3010%s\u3011(%s)\n  %s\n\n", w.getName(), w.getCategory(), w.getDescription()));
                }
                return sb.length() == 0 ? agentMsg(ctx, "agent.worldview_not_found") : sb.toString();
            }));

        tools.add(new Tool("read_organizations", "\u83b7\u53d6\u7ec4\u7ec7\u5217\u8868", "{}",
            (args, ctx) -> {
                if (ctx.getSettings() == null || listSize(ctx.getSettings().getOrganizations()) == 0) return agentMsg(ctx, "agent.no_organizations");
                StringBuilder sb = new StringBuilder();
                for (Organization o : ctx.getSettings().getOrganizations()) {
                    sb.append(String.format("\u3010%s\u3011(ID:%s, %s)\n  %s\n", o.getName(), o.getId(), o.getType(), o.getDescription()));
                    if (o.getMembers() != null && !o.getMembers().isEmpty()) sb.append("  Members: " + String.join(", ", o.getMembers()) + "\n");
                    sb.append("\n");
                }
                return sb.toString();
            }));

        tools.add(new Tool("read_chapter", "\u83b7\u53d6\u6307\u5b9a\u7ae0\u8282\u5185\u5bb9", "{\"num\": 1}",
            (args, ctx) -> {
                int num = argInt(args, "num", 0);
                if (ctx.getState().getChapters() != null) for (ChapterState ch : ctx.getState().getChapters()) {
                    if (ch.getNum() == num) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("\u7b2c%d\u7ae0\u300a%s\u300b[%s]\n\n", ch.getNum(), ch.getTitle(), ch.getStatus()));
                        if (nvl(ch.getOutline()).length() > 0) sb.append("\u5927\u7eb2: " + ch.getOutline() + "\n\n");
                        if (nvl(ch.getSummary()).length() > 0) sb.append("\u6458\u8981: " + ch.getSummary() + "\n\n");
                        sb.append(nvl(ch.getContent()).isEmpty() ? "(\u5c1a\u672a\u751f\u6210\u5185\u5bb9)" : ch.getContent());
                        return sb.toString();
                    }
                }
                return agentMsg(ctx, "agent.chapter_not_found", num);
            }));

        tools.add(new Tool("read_outline", "\u83b7\u53d6\u5b8c\u6574\u5927\u7eb2", "{}",
            (args, ctx) -> {
                if (listSize(ctx.getState().getChapters()) == 0) return agentMsg(ctx, "agent.no_outline");
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("\u300a%s\u300b\n\n", ctx.getState().getTitle()));
                for (ChapterState ch : ctx.getState().getChapters()) {
                    String st = switch(nvl(ch.getStatus())) { case "accepted" -> "\u2705"; case "review" -> "\ud83d\udc40"; case "writing" -> "\u23f3"; default -> ""; };
                    sb.append(String.format("\u7b2c%d\u7ae0 %s\u300a%s\u300b: %s\n", ch.getNum(), st, ch.getTitle(), nvl(ch.getOutline())));
                }
                return sb.toString();
            }));

        tools.add(new Tool("read_foreshadows", "\u83b7\u53d6\u4f0f\u7b14\u5217\u8868", "{}",
            (args, ctx) -> {
                if (listSize(ctx.getState().getForeshadows()) == 0) return agentMsg(ctx, "agent.no_foreshadows");
                StringBuilder sb = new StringBuilder();
                for (Foreshadow fs : ctx.getState().getForeshadows()) {
                    sb.append(String.format("#%d [%s] %s\n  \u63cf\u8ff0: %s\n  \u57cb\u8bbe: \u7b2c%d\u7ae0", fs.getId(), foreshadowStatusLabel(fs.getStatus()), fs.getName(), fs.getDescription(), fs.getPlantChapter()));
                    if (fs.getTargetChapter() > 0) sb.append(String.format(" -> \u56de\u6536: \u7b2c%d\u7ae0", fs.getTargetChapter()));
                    sb.append("\n");
                    if (fs.getEvents() != null) for (ForeshadowEvent ev : fs.getEvents())
                        sb.append(String.format("    - \u7b2c%d\u7ae0: %s\n", ev.getChapter(), ev.getNote()));
                    if (nvl(fs.getResolution()).length() > 0) sb.append("  \u56de\u6536\u65b9\u5f0f: " + fs.getResolution() + "\n");
                    sb.append("\n");
                }
                return sb.toString();
            }));

        tools.add(new Tool("search_project", "\u5168\u6587\u641c\u7d22\u9879\u76ee\u6570\u636e", "{\"query\": \"\u5173\u952e\u8bcd\"}",
            (args, ctx) -> {
                String q = argStr(args, "query").toLowerCase();
                if (q.isEmpty()) return agentMsg(ctx, "agent.search_keyword_required");
                List<String> results = new ArrayList<>();
                if (ctx.getSettings() != null) {
                    for (Character c : ctx.getSettings().getCharacters())
                        if ((nvl(c.getName()) + nvl(c.getBackground())).toLowerCase().contains(q))
                            results.add("[\u89d2\u8272] " + c.getName() + ": " + truncate(c.getBackground(), 100));
                    for (WorldviewEntry w : ctx.getSettings().getWorldview())
                        if ((nvl(w.getName()) + nvl(w.getDescription())).toLowerCase().contains(q))
                            results.add("[\u4e16\u754c\u89c2] " + w.getName() + ": " + truncate(w.getDescription(), 100));
                }
                if (ctx.getState().getChapters() != null)
                    for (ChapterState ch : ctx.getState().getChapters())
                        if ((nvl(ch.getTitle()) + nvl(ch.getOutline())).toLowerCase().contains(q))
                            results.add(String.format("[\u7ae0\u8282] \u7b2c%d\u7ae0\u300a%s\u300b: %s", ch.getNum(), ch.getTitle(), truncate(ch.getOutline(), 100)));
                return results.isEmpty() ? agentMsg(ctx, "agent.search_no_results") : String.join("\n", results);
            }));

        tools.add(new Tool("create_character", "\u521b\u5efa\u65b0\u89d2\u8272", "{\"name\": \"\u89d2\u8272\u540d\", \"age\": \"\", \"personality\": \"\", \"background\": \"\"}",
            (args, ctx) -> {
                Character c = OM.treeToValue(args, Character.class);
                if (nvl(c.getName()).isEmpty()) throw agentErr(ctx, "character_name_empty");
                c.setId(settingsService.nextCharacterID(ctx.getSettings()));
                ctx.getSettings().getCharacters().add(c);
                stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                logger.settingsUpdated();
                return agentMsg(ctx, "agent.character_created", c.getName(), c.getId());
            }));

        tools.add(new Tool("update_character", "\u66f4\u65b0\u89d2\u8272", "{\"id\": \"ID\", \"name\": \"\", \"age\": \"\", \"personality\": \"\", \"background\": \"\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                for (int i = 0; i < ctx.getSettings().getCharacters().size(); i++) {
                    Character c = ctx.getSettings().getCharacters().get(i);
                    if (id.equals(c.getId()) || id.equals(c.getName())) {
                        String v;
                        if (!(v=argStr(args,"name")).isEmpty()) c.setName(v);
                        if (!(v=argStr(args,"age")).isEmpty()) c.setAge(v);
                        if (!(v=argStr(args,"appearance")).isEmpty()) c.setAppearance(v);
                        if (!(v=argStr(args,"personality")).isEmpty()) c.setPersonality(v);
                        if (!(v=argStr(args,"background")).isEmpty()) c.setBackground(v);
                        if (!(v=argStr(args,"motivation")).isEmpty()) c.setMotivation(v);
                        if (!(v=argStr(args,"abilities")).isEmpty()) c.setAbilities(v);
                        if (!(v=argStr(args,"notes")).isEmpty()) c.setNotes(v);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.character_updated", c.getName());
                    }
                }
                return agentMsg(ctx, "agent.character_not_found", id);
            }));

        tools.add(new Tool("delete_character", "\u5220\u9664\u89d2\u8272", "{\"id\": \"ID\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                List<Character> chars = ctx.getSettings().getCharacters();
                for (int i = 0; i < chars.size(); i++)
                    if (id.equals(chars.get(i).getId()) || id.equals(chars.get(i).getName())) {
                        String name = chars.get(i).getName();
                        chars.remove(i);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.character_deleted", name);
                    }
                return agentMsg(ctx, "agent.character_not_found", id);
            }));

        tools.add(new Tool("create_worldview", "\u521b\u5efa\u4e16\u754c\u89c2\u6761\u76ee", "{\"name\": \"\", \"category\": \"\", \"description\": \"\"}",
            (args, ctx) -> {
                WorldviewEntry w = OM.treeToValue(args, WorldviewEntry.class);
                if (nvl(w.getName()).isEmpty() || nvl(w.getDescription()).isEmpty()) throw agentErr(ctx, "worldview_field_empty");
                w.setId(settingsService.nextWorldviewID(ctx.getSettings()));
                ctx.getSettings().getWorldview().add(w);
                stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                logger.settingsUpdated();
                return agentMsg(ctx, "agent.worldview_created", w.getName(), w.getId());
            }));

        tools.add(new Tool("update_worldview", "\u66f4\u65b0\u4e16\u754c\u89c2", "{\"id\": \"ID\", \"name\": \"\", \"category\": \"\", \"description\": \"\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                for (int i = 0; i < ctx.getSettings().getWorldview().size(); i++) {
                    WorldviewEntry w = ctx.getSettings().getWorldview().get(i);
                    if (id.equals(w.getId()) || id.equals(w.getName())) {
                        String v;
                        if (!(v=argStr(args,"name")).isEmpty()) w.setName(v);
                        if (!(v=argStr(args,"category")).isEmpty()) w.setCategory(v);
                        if (!(v=argStr(args,"description")).isEmpty()) w.setDescription(v);
                        if (!(v=argStr(args,"tags")).isEmpty()) w.setTags(v);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.worldview_updated", w.getName());
                    }
                }
                return agentMsg(ctx, "agent.worldview_not_found", id);
            }));

        tools.add(new Tool("delete_worldview", "\u5220\u9664\u4e16\u754c\u89c2", "{\"id\": \"ID\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                List<WorldviewEntry> wv = ctx.getSettings().getWorldview();
                for (int i = 0; i < wv.size(); i++)
                    if (id.equals(wv.get(i).getId()) || id.equals(wv.get(i).getName())) {
                        String name = wv.get(i).getName(); wv.remove(i);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.worldview_deleted", name);
                    }
                return agentMsg(ctx, "agent.worldview_not_found", id);
            }));

        tools.add(new Tool("create_organization", "\u521b\u5efa\u7ec4\u7ec7", "{\"name\": \"\", \"type\": \"\", \"description\": \"\"}",
            (args, ctx) -> {
                Organization o = OM.treeToValue(args, Organization.class);
                if (nvl(o.getName()).isEmpty()) throw agentErr(ctx, "organization_name_empty");
                o.setId(settingsService.nextOrganizationID(ctx.getSettings()));
                ctx.getSettings().getOrganizations().add(o);
                stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                logger.settingsUpdated();
                return agentMsg(ctx, "agent.organization_created", o.getName(), o.getId());
            }));

        tools.add(new Tool("update_organization", "\u66f4\u65b0\u7ec4\u7ec7", "{\"id\": \"ID\", \"name\": \"\", \"type\": \"\", \"description\": \"\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                for (int i = 0; i < ctx.getSettings().getOrganizations().size(); i++) {
                    Organization o = ctx.getSettings().getOrganizations().get(i);
                    if (id.equals(o.getId()) || id.equals(o.getName())) {
                        String v;
                        if (!(v=argStr(args,"name")).isEmpty()) o.setName(v);
                        if (!(v=argStr(args,"type")).isEmpty()) o.setType(v);
                        if (!(v=argStr(args,"description")).isEmpty()) o.setDescription(v);
                        if (args.has("members")) o.setMembers(Arrays.asList(OM.treeToValue(args.get("members"), String[].class)));
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.organization_updated", o.getName());
                    }
                }
                return agentMsg(ctx, "agent.organization_not_found", id);
            }));

        tools.add(new Tool("delete_organization", "\u5220\u9664\u7ec4\u7ec7", "{\"id\": \"ID\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                List<Organization> orgs = ctx.getSettings().getOrganizations();
                for (int i = 0; i < orgs.size(); i++)
                    if (id.equals(orgs.get(i).getId()) || id.equals(orgs.get(i).getName())) {
                        String name = orgs.get(i).getName(); orgs.remove(i);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.organization_deleted", name);
                    }
                return agentMsg(ctx, "agent.organization_not_found", id);
            }));

        tools.add(new Tool("create_relation", "\u521b\u5efa\u5173\u7cfb", "{\"source_id\": \"\", \"source_type\": \"\", \"target_id\": \"\", \"target_type\": \"\", \"label\": \"\"}",
            (args, ctx) -> {
                Relation rel = OM.treeToValue(args, Relation.class);
                if (nvl(rel.getSourceId()).isEmpty() || nvl(rel.getTargetId()).isEmpty()) throw agentErr(ctx, "relation_endpoints_empty");
                rel.setId(settingsService.nextRelationID(ctx.getSettings()));
                ctx.getSettings().getRelations().add(rel);
                stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                logger.settingsUpdated();
                return agentMsg(ctx, "agent.relation_created", rel.getId());
            }));

        tools.add(new Tool("update_relation", "\u66f4\u65b0\u5173\u7cfb", "{\"id\": \"ID\", \"source_id\": \"\", \"label\": \"\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                for (Relation rel : ctx.getSettings().getRelations())
                    if (id.equals(rel.getId())) {
                        String v;
                        if (!(v=argStr(args,"source_id")).isEmpty()) rel.setSourceId(v);
                        if (!(v=argStr(args,"source_type")).isEmpty()) rel.setSourceType(v);
                        if (!(v=argStr(args,"target_id")).isEmpty()) rel.setTargetId(v);
                        if (!(v=argStr(args,"target_type")).isEmpty()) rel.setTargetType(v);
                        if (!(v=argStr(args,"label")).isEmpty()) rel.setLabel(v);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.relation_updated", rel.getId());
                    }
                return agentMsg(ctx, "agent.relation_not_found", id);
            }));

        tools.add(new Tool("delete_relation", "\u5220\u9664\u5173\u7cfb", "{\"id\": \"ID\"}",
            (args, ctx) -> {
                String id = argStr(args, "id");
                List<Relation> rels = ctx.getSettings().getRelations();
                for (int i = 0; i < rels.size(); i++)
                    if (id.equals(rels.get(i).getId())) {
                        rels.remove(i);
                        stateService.saveSettings(ctx.getSettingsPath(), ctx.getSettings());
                        logger.settingsUpdated();
                        return agentMsg(ctx, "agent.relation_deleted");
                    }
                return agentMsg(ctx, "agent.relation_not_found", id);
            }));

        tools.add(new Tool("read_project_config", "\u8bfb\u53d6\u6545\u4e8b\u914d\u7f6e", "{}",
            (args, ctx) -> {
                StoryConfig snap = ctx.getState().getStoryConfigSnapshot();
                if (snap == null) snap = ctx.getConfig().getStory();
                return OM.writerWithDefaultPrettyPrinter().writeValueAsString(snap);
            }));

        tools.add(new Tool("update_project_config", "\u66f4\u65b0\u6545\u4e8b\u914d\u7f6e", "{\"type\": \"\", \"title\": \"\", \"chapter_count\": 30, \"writing_style\": \"\", \"story_synopsis\": \"\"}",
            (args, ctx) -> {
                StoryConfig sc = ctx.getConfig().getStory();
                String v;
                if (!(v=argStr(args,"type")).isEmpty()) sc.setType(v);
                if (!(v=argStr(args,"title")).isEmpty()) sc.setTitle(v);
                int ci = argInt(args,"chapter_count",0); if (ci > 0) sc.setChapterCount(ci);
                int tw = argInt(args,"target_words_per_chapter",0); if (tw > 0) sc.setTargetWordsPerChapter(tw);
                if (!(v=argStr(args,"writing_style")).isEmpty()) sc.setWritingStyle(v);
                if (!(v=argStr(args,"writing_pov")).isEmpty()) sc.setWritingPov(v);
                if (!(v=argStr(args,"story_synopsis")).isEmpty()) sc.setStorySynopsis(v);
                stateService.saveConfig(ctx.getCfgPath(), ctx.getConfig());
                boolean hasAccepted = false;
                if (ctx.getState().getChapters() != null)
                    for (ChapterState ch : ctx.getState().getChapters())
                        if ("accepted".equals(ch.getStatus())) { hasAccepted = true; break; }
                if (hasAccepted) {
                    StoryConfig newSettings = ctx.getConfig().getStory();
                    CompletableFuture.runAsync(() -> {
                        try { reconcileService.reconcileSettings(taskManager.getTaskContext(), newSettings); }
                        catch (Exception e) { logger.error("Settings reconciliation failed: " + e.getMessage()); }
                    });
                    return agentMsg(ctx, "agent.config_saved_reconciling");
                }
                logger.settingsUpdated();
                return agentMsg(ctx, "agent.config_saved");
            }));

        tools.add(new Tool("generate_outline", "\u751f\u6210\u5c0f\u8bf4\u5927\u7eb2\uff08\u5f02\u6b65\uff09", "{}",
            (args, ctx) -> {
                if (ctx.getState().getChapters() != null)
                    for (ChapterState ch : ctx.getState().getChapters()) {
                        if ("accepted".equals(ch.getStatus())) throw agentErr(ctx, "accepted_chapter_present");
                        if ("writing".equals(ch.getStatus()) || "review".equals(ch.getStatus())) throw agentErr(ctx, "writing_chapter_present");
                    }
                CompletableFuture.runAsync(() -> {
                    try { outlineService.generateOutline(taskManager.getTaskContext()); }
                    catch (Exception e) { logger.error("\u5927\u7eb2\u751f\u6210\u5931\u8d25: " + e.getMessage()); }
                });
                return agentMsg(ctx, "agent.outline_task_started");
            }));

        tools.add(new Tool("confirm_outline", "\u786e\u8ba4\u5927\u7eb2", "{}",
            (args, ctx) -> {
                if (!"outline".equals(ctx.getState().getPhase())) throw agentErr(ctx, "phase_not_outline");
                if (listSize(ctx.getState().getChapters()) == 0) throw agentErr(ctx, "outline_empty");
                outlineService.confirmOutline();
                logger.successKey("log.outline_confirmed");
                return agentMsg(ctx, "agent.outline_confirmed");
            }));

        tools.add(new Tool("revise_outline", "\u4fee\u8ba2\u5927\u7eb2\uff08\u5f02\u6b65\uff09", "{\"feedback\": \"\u4fee\u6539\u610f\u89c1\"}",
            (args, ctx) -> {
                String feedback = argStr(args, "feedback");
                if (feedback.isEmpty()) throw agentErr(ctx, "missing_feedback");
                CompletableFuture.runAsync(() -> {
                    try { outlineService.reviseOutline(taskManager.getTaskContext(), feedback); }
                    catch (Exception e) { logger.error("\u5927\u7eb2\u4fee\u8ba2\u5931\u8d25: " + e.getMessage()); }
                });
                return agentMsg(ctx, "agent.outline_revise_started");
            }));

        tools.add(new Tool("delete_outline", "\u3010\u5371\u9669\u3011\u5220\u9664\u6574\u4e2a\u5927\u7eb2", "{\"confirm\": true}",
            (args, ctx) -> {
                String msg = requireConfirm(ctx, args, String.format("\u5220\u9664\u6574\u4e2a\u5927\u7eb2\uff08\u5171 %d \u7ae0\uff09", listSize(ctx.getState().getChapters())));
                if (!msg.isEmpty()) return msg;
                if (ctx.getState().getChapters() != null)
                    for (ChapterState ch : ctx.getState().getChapters())
                        if ("writing".equals(ch.getStatus()) || "review".equals(ch.getStatus())) throw agentErr(ctx, "writing_chapter_present_delete");
                ctx.getState().setTitle(""); ctx.getState().setCorePrompt("");
                ctx.getState().setStorySynopsis(""); ctx.getState().setChapters(null);
                ctx.getState().setStoryConfigSnapshot(null); ctx.getState().setCurrentChapterIndex(0);
                stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                logger.successKey("log.outline_deleted");
                return agentMsg(ctx, "agent.outline_deleted");
            }));

        tools.add(new Tool("edit_chapter_outline", "\u7f16\u8f91\u7ae0\u8282\u5927\u7eb2", "{\"num\": 1, \"title\": \"\", \"outline\": \"\"}",
            (args, ctx) -> {
                int num = argInt(args, "num", 0);
                String title = argStr(args, "title");
                String outline = argStr(args, "outline");
                outlineService.editChapterOutline(num, title, outline);
                logger.successKey("log.chapter_outline_updated", num);
                return agentMsg(ctx, "agent.chapter_outline_updated", num);
            }));

        tools.add(new Tool("generate_chapter", "\u751f\u6210\u5f53\u524d\u7ae0\u8282\uff08\u5f02\u6b65\uff09", "{}",
            (args, ctx) -> {
                if (!"writing".equals(ctx.getState().getPhase())) throw agentErr(ctx, "phase_not_writing");
                int chIdx = ctx.getState().getCurrentChapterIndex();
                CompletableFuture.runAsync(() -> {
                    try { chapterService.generateChapter(taskManager.getTaskContext()); }
                    catch (Exception e) { logger.error("\u7ae0\u8282\u521b\u4f5c\u5931\u8d25: " + e.getMessage()); }
                });
                return agentMsg(ctx, "agent.chapter_task_started", chIdx + 1);
            }));

        tools.add(new Tool("confirm_chapter", "\u786e\u8ba4\u5f53\u524d\u7ae0\u8282", "{}",
            (args, ctx) -> {
                if (!"writing".equals(ctx.getState().getPhase())) throw agentErr(ctx, "phase_not_writing");
                chapterService.confirmChapter();
                int idx = ctx.getState().getCurrentChapterIndex();
                ChapterState ch = ctx.getState().getChapters().get(idx > 0 ? idx - 1 : 0);
                logger.successKey("log.chapter_confirmed", ch.getNum());
                return agentMsg(ctx, "agent.chapter_confirmed", ch.getNum(), ch.getTitle());
            }));

        tools.add(new Tool("edit_chapter_content", "\u5c40\u90e8\u7f16\u8f91\u7ae0\u8282\u6b63\u6587", "{\"num\": 1, \"operation\": \"replace_lines|replace_text|insert_after_line|append\", \"new_text\": \"\"}",
            (args, ctx) -> {
                EditChapterContentRequest req = OM.treeToValue(args, EditChapterContentRequest.class);
                if (nvl(req.getOperation()).isEmpty()) throw agentErr(ctx, "chapter_edit_op_required");
                int totalLines = editingService.editChapterContent(ctx.getState(), req);
                stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                int ci = findChapterIdx(ctx.getState(), req.getChapterNum());
                if (ci >= 0) saveChapterMarkdown(ctx.getProjectDir(), ctx.getState().getChapters().get(ci), ctx.getState().getTitle());
                return agentMsg(ctx, "agent.chapter_content_edited", req.getChapterNum(), req.getOperation(), totalLines);
            }));

        tools.add(new Tool("revise_chapter", "\u4fee\u8ba2\u7ae0\u8282\u6b63\u6587\uff08\u5f02\u6b65\uff09", "{\"num\": 6, \"feedback\": \"\u4fee\u6539\u610f\u89c1\"}",
            (args, ctx) -> {
                String feedback = argStr(args, "feedback");
                if (feedback.trim().isEmpty()) throw agentErr(ctx, "missing_feedback");
                int num = argInt(args, "num", 0);
                if (num <= 0) {
                    if (!"writing".equals(ctx.getState().getPhase()) || ctx.getState().getCurrentChapterIndex() >= listSize(ctx.getState().getChapters()))
                        throw agentErr(ctx, "chapter_not_found");
                    num = ctx.getState().getChapters().get(ctx.getState().getCurrentChapterIndex()).getNum();
                }
                int ci = findChapterIdx(ctx.getState(), num);
                if (ci < 0) throw agentErr(ctx, "chapter_n_not_found", num);
                ChapterState target = ctx.getState().getChapters().get(ci);
                if (nvl(target.getContent()).isEmpty()) throw agentErr(ctx, "chapter_content_empty");
                boolean isCurrent = "writing".equals(ctx.getState().getPhase()) &&
                        ctx.getState().getCurrentChapterIndex() < listSize(ctx.getState().getChapters()) &&
                        ctx.getState().getChapters().get(ctx.getState().getCurrentChapterIndex()).getNum() == num &&
                        ("review".equals(target.getStatus()) || "writing".equals(target.getStatus()));
                int fNum = num;
                CompletableFuture.runAsync(() -> {
                    try {
                        if (isCurrent) chapterService.reviseChapter(taskManager.getTaskContext(), feedback);
                        else chapterService.reviseSpecificChapter(taskManager.getTaskContext(), fNum, feedback);
                    } catch (Exception e) { logger.error("\u7ae0\u8282\u4fee\u8ba2\u5931\u8d25: " + e.getMessage()); }
                });
                return agentMsg(ctx, "agent.chapter_revise_started", num);
            }));

        tools.add(new Tool("delete_chapter", "\u3010\u5371\u9669\u3011\u6e05\u9664\u6700\u540e\u4e00\u7ae0\u6b63\u6587", "{\"confirm\": true}",
            (args, ctx) -> {
                String msg = requireConfirm(ctx, args, "\u6e05\u9664\u6700\u540e\u4e00\u4e2a\u7ae0\u8282\u7684\u6b63\u6587");
                if (!msg.isEmpty()) return msg;
                if (listSize(ctx.getState().getChapters()) == 0) throw agentErr(ctx, "no_chapters_to_delete");
                int lastIdx = ctx.getState().getChapters().size() - 1;
                ChapterState ch = ctx.getState().getChapters().get(lastIdx);
                if ("writing".equals(ch.getStatus())) throw agentErr(ctx, "writing_chapter_cannot_delete");
                try { Files.deleteIfExists(Paths.get(projectService.chapterMarkdownPath(ctx.getProjectDir(), ch.getNum()))); } catch (Exception e) {}
                ch.setContent(""); ch.setSummary(""); ch.setStatus("pending");
                if (ctx.getState().getCurrentChapterIndex() > lastIdx) ctx.getState().setCurrentChapterIndex(lastIdx);
                stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                logger.successKey("log.chapter_deleted", ch.getNum());
                return agentMsg(ctx, "agent.chapter_deleted", ch.getNum());
            }));

        tools.add(new Tool("delete_chapters_from", "\u3010\u5371\u9669\u3011\u4ece\u6307\u5b9a\u7ae0\u8282\u5230\u672b\u5c3e\u6e05\u9664\u6b63\u6587", "{\"num\": 6, \"confirm\": true}",
            (args, ctx) -> {
                int num = argInt(args, "num", 0);
                if (!argBool(args, "confirm")) {
                    int affected = 0;
                    if (ctx.getState().getChapters() != null)
                        for (ChapterState ch : ctx.getState().getChapters()) if (ch.getNum() >= num) affected++;
                    return agentMsg(ctx, "agent.chapters_bulk_delete_confirm", num, affected);
                }
                int startIdx = -1;
                for (int i = 0; i < ctx.getState().getChapters().size(); i++)
                    if (ctx.getState().getChapters().get(i).getNum() == num) { startIdx = i; break; }
                if (startIdx == -1) throw agentErr(ctx, "chapter_n_not_found", num);
                for (int i = startIdx; i < ctx.getState().getChapters().size(); i++)
                    if ("writing".equals(ctx.getState().getChapters().get(i).getStatus())) throw agentErr(ctx, "writing_range_has_writing");
                int deletedCount = ctx.getState().getChapters().size() - startIdx;
                for (int i = startIdx; i < ctx.getState().getChapters().size(); i++) {
                    ChapterState ch = ctx.getState().getChapters().get(i);
                    try { Files.deleteIfExists(Paths.get(projectService.chapterMarkdownPath(ctx.getProjectDir(), ch.getNum()))); } catch (Exception e) {}
                    ch.setContent(""); ch.setSummary(""); ch.setStatus("pending");
                }
                if (ctx.getState().getCurrentChapterIndex() >= startIdx) ctx.getState().setCurrentChapterIndex(startIdx);
                stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                logger.successKey("log.chapters_deleted_from", num, deletedCount);
                return agentMsg(ctx, "agent.chapters_deleted_from", num, deletedCount);
            }));

        tools.add(new Tool("suggest_foreshadows", "AI\u5efa\u8bae\u4f0f\u7b14\uff08\u5f02\u6b65\uff09", "{}",
            (args, ctx) -> {
                if (listSize(ctx.getState().getChapters()) == 0) throw agentErr(ctx, "need_generate_outline_first");
                CompletableFuture.runAsync(() -> {
                    try { foreshadowService.suggestForeshadows(taskManager.getTaskContext()); }
                    catch (Exception e) { logger.error("\u4f0f\u7b14\u5efa\u8bae\u5931\u8d25: " + e.getMessage()); }
                });
                return agentMsg(ctx, "agent.foreshadow_suggest_started");
            }));

        tools.add(new Tool("create_foreshadow", "\u521b\u5efa\u4f0f\u7b14", "{\"name\": \"\", \"description\": \"\", \"plant_chapter\": 1, \"target_chapter\": 5}",
            (args, ctx) -> {
                String name = argStr(args, "name"), desc = argStr(args, "description");
                if (name.isEmpty() || desc.isEmpty()) throw agentErr(ctx, "worldview_field_empty");
                Foreshadow fs = new Foreshadow();
                fs.setId(foreshadowService.nextForeshadowID(ctx.getState().getForeshadows()));
                fs.setName(name); fs.setDescription(desc);
                fs.setPlantChapter(argInt(args, "plant_chapter", 1));
                fs.setTargetChapter(argInt(args, "target_chapter", 0));
                fs.setStatus(Foreshadow.STATUS_PLANTED); fs.setEvents(new ArrayList<>());
                if (ctx.getState().getForeshadows() == null) ctx.getState().setForeshadows(new ArrayList<>());
                ctx.getState().getForeshadows().add(fs);
                stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                foreshadowService.saveForeshadowRoadmap(ctx.getProjectDir(), ctx.getState());
                return agentMsg(ctx, "agent.foreshadow_created", fs.getName(), fs.getId());
            }));

        tools.add(new Tool("update_foreshadow", "\u66f4\u65b0\u4f0f\u7b14", "{\"id\": 1, \"name\": \"\", \"description\": \"\", \"status\": \"\", \"resolution\": \"\"}",
            (args, ctx) -> {
                int id = argInt(args, "id", -1);
                if (ctx.getState().getForeshadows() == null) throw agentErr(ctx, "foreshadow_not_found");
                for (Foreshadow fs : ctx.getState().getForeshadows())
                    if (fs.getId() == id) {
                        String v;
                        if (!(v=argStr(args,"name")).isEmpty()) fs.setName(v);
                        if (!(v=argStr(args,"description")).isEmpty()) fs.setDescription(v);
                        int pc = argInt(args,"plant_chapter",0); if (pc > 0) fs.setPlantChapter(pc);
                        int tc2 = argInt(args,"target_chapter",0); if (tc2 > 0) fs.setTargetChapter(tc2);
                        if (!(v=argStr(args,"status")).isEmpty()) fs.setStatus(v);
                        if (!(v=argStr(args,"resolution")).isEmpty()) fs.setResolution(v);
                        stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                        foreshadowService.saveForeshadowRoadmap(ctx.getProjectDir(), ctx.getState());
                        return agentMsg(ctx, "agent.foreshadow_updated", fs.getName());
                    }
                throw agentErr(ctx, "foreshadow_not_found");
            }));

        tools.add(new Tool("delete_foreshadow", "\u5220\u9664\u4f0f\u7b14", "{\"id\": 1}",
            (args, ctx) -> {
                int id = argInt(args, "id", -1);
                if (ctx.getState().getForeshadows() != null) {
                    List<Foreshadow> fsl = ctx.getState().getForeshadows();
                    for (int i = 0; i < fsl.size(); i++)
                        if (fsl.get(i).getId() == id) {
                            String name = fsl.get(i).getName(); fsl.remove(i);
                            stateService.saveProgress(ctx.getProgressPath(), ctx.getState());
                            foreshadowService.saveForeshadowRoadmap(ctx.getProjectDir(), ctx.getState());
                            return agentMsg(ctx, "agent.foreshadow_deleted", name);
                        }
                }
                return agentMsg(ctx, "agent.foreshadow_not_found", id);
            }));

        tools.add(new Tool("read_skills", "\u83b7\u53d6\u6280\u80fd\u5217\u8868", "{}",
            (args, ctx) -> {
                StringBuilder sb = new StringBuilder();
                SkillConfig sc = ctx.getConfig().getSkillConfig();
                for (Skill s : ctx.getSkills()) {
                    boolean enabled = sc != null && sc.getEnabledSkills() != null && Boolean.TRUE.equals(sc.getEnabledSkills().get(s.getId()));
                    sb.append(String.format("%s [%s] %s (%s)\n  %s\n\n", enabled ? "\u2705" : "\u274c", s.getCategory(), s.getName(), s.getId(), s.getDescription()));
                }
                return sb.toString();
            }));

        tools.add(new Tool("toggle_skill", "\u542f\u7528/\u7981\u7528\u6280\u80fd", "{\"id\": \"\u6280\u80fdID\", \"enabled\": true}",
            (args, ctx) -> {
                String id = argStr(args, "id"); boolean enabled = argBool(args, "enabled");
                boolean found = false;
                for (Skill s : ctx.getSkills()) if (id.equals(s.getId())) { found = true; break; }
                if (!found) throw agentErr(ctx, "skill_not_found");
                if (ctx.getConfig().getSkillConfig() == null) ctx.getConfig().setSkillConfig(new SkillConfig());
                if (ctx.getConfig().getSkillConfig().getEnabledSkills() == null)
                    ctx.getConfig().getSkillConfig().setEnabledSkills(new HashMap<>());
                ctx.getConfig().getSkillConfig().getEnabledSkills().put(id, enabled);
                stateService.saveConfig(ctx.getCfgPath(), ctx.getConfig());
                return agentMsg(ctx, "agent.skill_toggled", id, enabled ? "\u542f\u7528" : "\u7981\u7528");
            }));

        tools.add(new Tool("reset_progress", "\u3010\u5371\u9669\u3011\u91cd\u7f6e\u6240\u6709\u8fdb\u5ea6", "{\"confirm\": true}",
            (args, ctx) -> {
                String msg = requireConfirm(ctx, args, String.format("\u91cd\u7f6e\u5168\u90e8\u8fdb\u5ea6\uff08\u5171 %d \u7ae0\uff09", listSize(ctx.getState().getChapters())));
                if (!msg.isEmpty()) return msg;
                try { fileSystemService.deleteFile(ctx.getProgressPath()); } catch (Exception e) { /* ok */ }
                Progress empty = new Progress(); empty.setPhase("outline");
                ctx.setState(empty);
                projectService.setConfig(ctx.getConfig());
                logger.success("\u8fdb\u5ea6\u5df2\u91cd\u7f6e\u3002");
                return agentMsg(ctx, "agent.progress_reset");
            }));

        return tools;
    }

} // end class AgentService
