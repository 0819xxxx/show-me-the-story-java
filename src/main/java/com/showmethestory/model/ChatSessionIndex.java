package com.showmethestory.model;

import java.util.List;

/**
 * Index of all chat sessions, persisted to sessions/index.json.
 */
public class ChatSessionIndex {

    private List<ChatSessionMeta> sessions;

    public ChatSessionIndex() {}

    public List<ChatSessionMeta> getSessions() { return sessions; }
    public void setSessions(List<ChatSessionMeta> sessions) { this.sessions = sessions; }
}
