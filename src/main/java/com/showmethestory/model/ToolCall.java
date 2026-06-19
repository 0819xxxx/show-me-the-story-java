package com.showmethestory.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A tool call made by the agent (name + JSON arguments).
 */
public class ToolCall {

    private String name;
    private JsonNode arguments;

    public ToolCall() {}

    public ToolCall(String name, JsonNode arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public JsonNode getArguments() { return arguments; }
    public void setArguments(JsonNode arguments) { this.arguments = arguments; }
}
