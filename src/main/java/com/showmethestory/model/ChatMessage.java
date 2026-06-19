package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A chat message within a chat session.
 */
public class ChatMessage {

    private String role;
    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("tool_result")
    private String toolResult;

    @JsonProperty("tool_result_key")
    private String toolResultKey;

    @JsonProperty("tool_result_args")
    private List<String> toolResultArgs;

    private String timestamp;

    public ChatMessage() {}

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    public String getToolResultKey() { return toolResultKey; }
    public void setToolResultKey(String toolResultKey) { this.toolResultKey = toolResultKey; }

    public List<String> getToolResultArgs() { return toolResultArgs; }
    public void setToolResultArgs(List<String> toolResultArgs) { this.toolResultArgs = toolResultArgs; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
