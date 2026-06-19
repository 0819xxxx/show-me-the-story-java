package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single step in the agent's reasoning loop.
 */
public class AgentStep {

    private String role;
    private String content;

    @JsonProperty("tool_call")
    private ToolCall toolCall;

    @JsonProperty("tool_result")
    private String toolResult;

    @JsonProperty("tool_result_key")
    private String toolResultKey;

    @JsonProperty("tool_result_args")
    private List<String> toolResultArgs;

    public AgentStep() {}

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ToolCall getToolCall() { return toolCall; }
    public void setToolCall(ToolCall toolCall) { this.toolCall = toolCall; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    public String getToolResultKey() { return toolResultKey; }
    public void setToolResultKey(String toolResultKey) { this.toolResultKey = toolResultKey; }

    public List<String> getToolResultArgs() { return toolResultArgs; }
    public void setToolResultArgs(List<String> toolResultArgs) { this.toolResultArgs = toolResultArgs; }
}
