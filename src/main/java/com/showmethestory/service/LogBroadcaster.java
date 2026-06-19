package com.showmethestory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.showmethestory.i18n.LocaleHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE pub/sub broadcaster. All connected SseEmitter subscribers receive
 * every broadcast event. Uses CopyOnWriteArrayList for thread-safe
 * subscriber management.
 */
@Service
@Scope("singleton")
public class LogBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LogBroadcaster.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean closed = false;

    // ---------------------------------------------------------------
    // Subscribe / Unsubscribe
    // ---------------------------------------------------------------

    /**
     * Create a new SseEmitter, register it as a subscriber, and return it.
     * The caller should return this emitter from a controller endpoint.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(t -> clients.remove(emitter));
        return emitter;
    }

    /**
     * Explicitly remove a subscriber and complete it.
     */
    public void unsubscribe(SseEmitter emitter) {
        clients.remove(emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------
    // Generic broadcast / emit
    // ---------------------------------------------------------------

    /**
     * Broadcast an SSE event to all connected subscribers.
     */
    public void broadcast(String event, Object data) {
        if (closed) return;
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            jsonData = "{\"error\":\"marshal failed\"}";
        }
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event().name(event).data(jsonData));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        clients.removeAll(dead);
    }

    /**
     * Alias for broadcast – emits a named event with arbitrary data.
     */
    public void emit(String event, Object data) {
        broadcast(event, data);
    }

    // ---------------------------------------------------------------
    // Log entry helpers
    // ---------------------------------------------------------------

    private void logEntry(String level, String msg, String msgEN, String msgKey, String[] msgArgs) {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("level", level);
        entry.put("msg", msg != null ? msg : "");
        if (msgEN != null && !msgEN.isEmpty()) {
            entry.put("msg_en", msgEN);
        }
        if (msgKey != null && !msgKey.isEmpty()) {
            entry.put("msg_key", msgKey);
        }
        if (msgArgs != null && msgArgs.length > 0) {
            entry.put("msg_args", msgArgs);
        }
        entry.put("time", LocalTime.now().format(TIME_FMT));

        broadcast("log", entry);

        // Mirror to stdout
        if (msg != null && !msg.isEmpty()) {
            System.out.printf(" [%s] %s%n", level, msg);
        } else if (msgKey != null && !msgKey.isEmpty()) {
            System.out.printf(" [%s] %s%n", level, msgKey);
        }
    }

    public void log(String level, String msg) {
        logEntry(level, msg, null, null, null);
    }

    private void logBilingual(String level, String zh, String en) {
        logEntry(level, zh, en, null, null);
    }

    /**
     * Log with an i18n message key – both zh and en text are resolved.
     */
    public void logKey(String level, String key, Object... args) {
        String zh = LocaleHelper.t(LocaleHelper.LANG_ZH, key, args);
        String en = LocaleHelper.t(LocaleHelper.LANG_EN, key, args);
        String[] strArgs = msgArgsToStrings(args);
        logEntry(level, zh, en, key, strArgs);
    }

    // ---------------------------------------------------------------
    // Convenience level-specific log methods
    // ---------------------------------------------------------------

    public void info(String msg)    { log("info", msg); }
    public void error(String msg)   { log("error", msg); }
    public void warn(String msg)    { log("warn", msg); }
    public void success(String msg) { log("success", msg); }

    public void infoKey(String key, Object... args)    { logKey("info", key, args); }
    public void errorKey(String key, Object... args)   { logKey("error", key, args); }
    public void warnKey(String key, Object... args)    { logKey("warn", key, args); }
    public void successKey(String key, Object... args) { logKey("success", key, args); }

    // Bilingual variants
    public void infoBilingual(String zh, String en)    { logBilingual("info", zh, en); }
    public void errorBilingual(String zh, String en)   { logBilingual("error", zh, en); }
    public void warnBilingual(String zh, String en)    { logBilingual("warn", zh, en); }
    public void successBilingual(String zh, String en) { logBilingual("success", zh, en); }

    // Step helper
    public void stepInfo(int step, int total, String msg) {
        log("info", String.format("[%d/%d] %s", step, total, msg));
    }

    // ---------------------------------------------------------------
    // SSE domain-specific event emitters
    // ---------------------------------------------------------------

    public void streamStart(int chapterIdx) {
        emit("stream_start", Map.of("chapter_idx", chapterIdx));
    }

    public void tokenUsage(int promptTokens, int completionTokens) {
        emit("token_usage", Map.of(
                "prompt_tokens", promptTokens,
                "completion_tokens", completionTokens));
    }

    public void progressUpdate(Object data) {
        emit("progress_update", data);
    }

    public void taskStart(String task) {
        emit("task_start", Map.of("task", task));
    }

    public void taskEnd(String task, boolean success) {
        emit("task_end", Map.of("task", task, "success", success));
    }

    public void contentChunk(int chapterIdx, String text) {
        emit("content_chunk", Map.of("chapter_idx", chapterIdx, "text", text));
    }

    public void foreshadowSuggestions(Object suggestions) {
        emit("foreshadow_suggestions", suggestions);
    }

    public void foreshadowOutlineConflicts(Object report) {
        emit("foreshadow_outline_conflicts", report);
    }

    public void writingConflict(Object conflict) {
        emit("writing_conflict", conflict);
    }

    public void continueAnalysisResult(Object data) {
        emit("continue_analysis", data);
    }

    public void settingsReconciled(Object data) {
        emit("settings_reconciled", data);
    }

    public void settingsUpdated() {
        emit("settings_updated", Map.of("status", "ok"));
    }

    public void postProcessReport(String reportType, String content) {
        emit("postprocess_report", Map.of("type", reportType, "content", content));
    }

    public void postProcessRoadmap(Object pp) {
        emit("postprocess_roadmap", pp);
    }

    public void postProcessItemDone(Object item) {
        emit("postprocess_item_done", item);
    }

    public void postProcessUpdate(Object pp) {
        emit("postprocess_update", pp);
    }

    public void polishResult(int chapterIdx, String text) {
        emit("polish_result", Map.of("chapter_idx", chapterIdx, "text", text));
    }

    public void chatChunk(String sessionId, String text) {
        emit("chat_chunk", Map.of("session_id", sessionId, "text", text));
    }

    public void toolCallStart(String sessionId, String toolName, String args) {
        emit("tool_call_start", Map.of(
                "session_id", sessionId,
                "tool_name", toolName,
                "args", args));
    }

    public void toolCallEnd(String sessionId, String toolName, String result,
                            String resultKey, String[] resultArgs) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("session_id", sessionId);
        payload.put("tool_name", toolName);
        payload.put("result", result);
        if (resultKey != null && !resultKey.isEmpty()) {
            payload.put("result_key", resultKey);
            payload.put("result_args", resultArgs);
        }
        emit("tool_call_end", payload);
    }

    public void streamEnd(int chapterIdx) {
        emit("stream_end", Map.of("chapter_idx", chapterIdx));
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    public void close() {
        closed = true;
        for (SseEmitter emitter : clients) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private static String[] msgArgsToStrings(Object... args) {
        if (args == null || args.length == 0) return null;
        String[] out = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = String.valueOf(args[i]);
        }
        return out;
    }
}
