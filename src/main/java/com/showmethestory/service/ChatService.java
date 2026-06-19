package com.showmethestory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.showmethestory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chat session persistence and agent loop orchestration.
 * Maps to Go: chat.go -- LoadChatSessions, LoadChatSession, SaveChatSession,
 * DeleteChatSession, updateChatIndex, generateSessionID, generateChatTitle.
 *
 * Also bridges to AgentService for the async message-handling loop.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.systemDefault());

    private final FileSystemService fileSystemService;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;

    /** Base directory for chat sessions (set by ProjectService on project switch). */
    private volatile String sessionsDir;

    public ChatService(FileSystemService fileSystemService, AgentService agentService) {
        this.fileSystemService = fileSystemService;
        this.agentService = agentService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void setSessionsDir(String dir) {
        this.sessionsDir = dir;
    }

    public String getSessionsDir() {
        return sessionsDir;
    }

    // ---------------------------------------------------------------
    // Path helpers
    // ---------------------------------------------------------------

    private String chatIndexPath() {
        return Path.of(sessionsDir, "index.json").toString();
    }

    private boolean isValidSessionID(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) return false;
        for (char c : id.toCharArray()) {
            if (c == '/' || c == '\\' || c == '.' || c == ':') return false;
        }
        return true;
    }

    // ---------------------------------------------------------------
    // CRUD operations (used by ChatController)
    // ---------------------------------------------------------------

    /**
     * Get all sessions (index). Cleans up empty sessions.
     */
    public ChatSessionIndex getSessions() {
        try {
            ChatSessionIndex idx = loadChatSessions();
            // Clean up empty sessions
            if (idx.getSessions() != null) {
                idx.getSessions().removeIf(m -> m.getMsgCount() == 0);
            }
            return idx;
        } catch (IOException e) {
            log.warn("Failed to load chat sessions: {}", e.getMessage());
            ChatSessionIndex empty = new ChatSessionIndex();
            empty.setSessions(new ArrayList<>());
            return empty;
        }
    }

    /**
     * Create a new empty chat session.
     */
    public ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setId(generateSessionID());
        session.setTitle("新会话");
        session.setMessages(new ArrayList<>());
        String now = nowTimestamp();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        try {
            saveChatSession(session);
        } catch (IOException e) {
            log.error("Failed to create chat session: {}", e.getMessage());
        }
        return session;
    }

    /**
     * Get a session by ID. Returns null if not found.
     */
    public ChatSession getSession(String id) {
        try {
            return loadChatSession(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Delete a session by ID.
     */
    public ResponseEntity<?> deleteSession(String id) {
        try {
            deleteChatSession(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Prepare user message: append to session, generate title if first message, save.
     */
    public void prepareUserMessage(ChatSession session, String content, String contextPage) {
        if (session.getMessages() == null) {
            session.setMessages(new ArrayList<>());
        }

        // Generate title from first message
        if (session.getMessages().isEmpty()) {
            session.setTitle(generateChatTitle(content));
        }

        // Add user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(content);
        userMsg.setTimestamp(nowTimestamp());
        session.getMessages().add(userMsg);

        session.setUpdatedAt(nowTimestamp());

        try {
            saveChatSession(session);
        } catch (IOException e) {
            log.error("Failed to save user message: {}", e.getMessage());
        }
    }

    /**
     * Run the agent loop for a chat message (called async).
     */
    public void runAgentLoop(OpenAIClient.CancellationToken ct, ChatSession session,
                              String content, String contextPage) {
        agentService.runChatAgentLoop(ct, session, content, contextPage);
    }

    // ---------------------------------------------------------------
    // Internal persistence
    // ---------------------------------------------------------------

    private ChatSessionIndex loadChatSessions() throws IOException {
        byte[] data = fileSystemService.readFile(chatIndexPath());
        if (data == null) {
            ChatSessionIndex idx = new ChatSessionIndex();
            idx.setSessions(new ArrayList<>());
            return idx;
        }
        ChatSessionIndex idx = objectMapper.readValue(data, ChatSessionIndex.class);
        if (idx.getSessions() == null) {
            idx.setSessions(new ArrayList<>());
        }
        return idx;
    }

    private ChatSession loadChatSession(String id) throws IOException {
        if (!isValidSessionID(id)) {
            throw new IllegalArgumentException("无效的会话ID");
        }
        String path = Path.of(sessionsDir, id + ".json").toString();
        byte[] data = fileSystemService.readFile(path);
        if (data == null) {
            throw new IllegalArgumentException("会话不存在: " + id);
        }
        return objectMapper.readValue(data, ChatSession.class);
    }

    public void saveChatSession(ChatSession session) throws IOException {
        if (sessionsDir == null) return;
        Files.createDirectories(Path.of(sessionsDir));

        String path = Path.of(sessionsDir, session.getId() + ".json").toString();
        byte[] data = objectMapper.writeValueAsBytes(session);
        fileSystemService.writeFileAtomic(path, data);

        updateChatIndex(session);
    }

    private void deleteChatSession(String id) throws IOException {
        if (!isValidSessionID(id)) {
            throw new IllegalArgumentException("无效的会话ID");
        }
        String path = Path.of(sessionsDir, id + ".json").toString();
        fileSystemService.deleteFile(path);

        ChatSessionIndex idx = loadChatSessions();
        if (idx.getSessions() != null) {
            idx.getSessions().removeIf(m -> id.equals(m.getId()));
        }
        saveChatSessions(idx);
    }

    private void updateChatIndex(ChatSession session) throws IOException {
        ChatSessionIndex idx = loadChatSessions();

        boolean found = false;
        if (idx.getSessions() != null) {
            for (ChatSessionMeta m : idx.getSessions()) {
                if (m.getId().equals(session.getId())) {
                    m.setTitle(session.getTitle());
                    m.setUpdatedAt(session.getUpdatedAt());
                    m.setMsgCount(session.getMessages() != null ? session.getMessages().size() : 0);
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            if (idx.getSessions() == null) {
                idx.setSessions(new ArrayList<>());
            }
            ChatSessionMeta meta = new ChatSessionMeta();
            meta.setId(session.getId());
            meta.setTitle(session.getTitle());
            meta.setCreatedAt(session.getCreatedAt());
            meta.setUpdatedAt(session.getUpdatedAt());
            meta.setMsgCount(session.getMessages() != null ? session.getMessages().size() : 0);
            idx.getSessions().add(meta);
        }

        saveChatSessions(idx);
    }

    private void saveChatSessions(ChatSessionIndex idx) throws IOException {
        byte[] data = objectMapper.writeValueAsBytes(idx);
        fileSystemService.writeFileAtomic(chatIndexPath(), data);
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------

    public String generateSessionID() {
        return "s_" + Instant.now().toEpochMilli() + "_" +
                (int) (Math.random() * 1000);
    }

    public String generateChatTitle(String userMessage) {
        if (userMessage == null) return "";
        int[] codePoints = userMessage.codePoints().toArray();
        if (codePoints.length > 20) {
            return new String(codePoints, 0, 20) + "...";
        }
        return userMessage;
    }

    public String nowTimestamp() {
        return TS_FMT.format(Instant.now());
    }
}
