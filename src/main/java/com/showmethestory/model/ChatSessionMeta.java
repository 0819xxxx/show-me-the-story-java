package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight metadata for a chat session (used in the session index).
 */
public class ChatSessionMeta {

    private String id;
    private String title;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("msg_count")
    private int msgCount;

    public ChatSessionMeta() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public int getMsgCount() { return msgCount; }
    public void setMsgCount(int msgCount) { this.msgCount = msgCount; }
}
