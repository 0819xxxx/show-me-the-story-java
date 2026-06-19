package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An API message (role + content) for LLM chat completions.
 */
public class Message {

    private String role;
    private String content;

    public Message() {}

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public static Message system(String content) { return new Message("system", content); }
    public static Message user(String content) { return new Message("user", content); }
}
