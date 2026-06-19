package com.showmethestory.controller;

import com.showmethestory.model.ChatSession;
import com.showmethestory.model.ChatSessionIndex;
import com.showmethestory.service.ChatService;
import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Chat session and message endpoints.
 * Maps to Go handlers: GetChatSessions, PostChatSession, GetChatSession,
 *   DeleteChatSession, PostChatMessage
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final TaskManager taskManager;

    public ChatController(ChatService chatService, TaskManager taskManager) {
        this.chatService = chatService;
        this.taskManager = taskManager;
    }

    /**
     * Go: GetChatSessions - returns the chat session index (list of session metadata).
     * Cleans up empty sessions (msg_count == 0) before returning.
     */
    @GetMapping("/sessions")
    public ResponseEntity<ChatSessionIndex> getChatSessions() {
        return ResponseEntity.ok(chatService.getSessions());
    }

    /**
     * Go: PostChatSession - create a new empty chat session.
     * Creates the session file but doesn't add to index until first message.
     * Response: ChatSession with id, title="新会话", empty messages, timestamps.
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createChatSession() {
        return ResponseEntity.ok(chatService.createSession());
    }

    /**
     * Go: GetChatSession - load and return a specific chat session by ID.
     * Path variable: {id}
     * Returns 404 if session not found.
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getChatSession(@PathVariable String id) {
        ChatSession session = chatService.getSession(id);
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("error", "chat_session_not_found"));
        }
        return ResponseEntity.ok(session);
    }

    /**
     * Go: DeleteChatSession - delete a chat session by ID.
     * Path variable: {id}
     * Rejects if task is running.
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteChatSession(@PathVariable String id) {
        if (taskManager.isTaskRunning()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_locked"));
        }
        return chatService.deleteSession(id);
    }

    /**
     * Go: PostChatMessage - send a message to a chat session and get AI response (async).
     * Path variable: {id} - session ID
     * Body: {content: "...", context_page: "..."}
     *
     * Process:
     * 1. Load session, append user message
     * 2. If first message, generate title from content
     * 3. Save session
     * 4. Launch async agent loop that:
     *    - Builds history from session messages
     *    - Runs RunAgentLoop with up to 30 iterations
     *    - Saves agent steps (tool calls, tool results) back to session
     *    - Appends final assistant reply
     *    - Broadcasts chat chunk via SSE
     *
     * Returns 202 Accepted immediately.
     */
    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<?> postChatMessage(@PathVariable String id, @RequestBody Map<String, String> body) {
        if (!taskManager.tryStartTask()) {
            return ResponseEntity.status(409).body(Map.of("error", "task_running_wait"));
        }

        String content = body.get("content");
        if (content == null || content.isEmpty()) {
            taskManager.endTask();
            return ResponseEntity.badRequest().body(Map.of("error", "missing_content"));
        }

        String contextPage = body.getOrDefault("context_page", "");

        // Load session and validate
        ChatSession session = chatService.getSession(id);
        if (session == null) {
            taskManager.endTask();
            return ResponseEntity.status(404).body(Map.of("error", "chat_session_not_found"));
        }

        // Prepare the message (add user message, save, etc.) synchronously
        chatService.prepareUserMessage(session, content, contextPage);

        // Launch async agent loop
        CompletableFuture.runAsync(() -> {
            try {
                chatService.runAgentLoop(taskManager.getTaskContext(), session, content, contextPage);
            } finally {
                taskManager.endTask();
            }
        });

        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
