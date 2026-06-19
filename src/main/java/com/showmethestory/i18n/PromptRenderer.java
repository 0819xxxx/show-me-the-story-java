package com.showmethestory.i18n;

import java.util.Map;

/**
 * Simple template renderer that replaces {{.Key}} placeholders with values.
 * Mirrors Go's RenderPrompt function.
 */
public final class PromptRenderer {

    private PromptRenderer() {}

    /**
     * Render a prompt template by replacing all {{.Key}} placeholders
     * with the corresponding values from the data map.
     *
     * @param template the template string containing {{.Key}} placeholders
     * @param data     key-value pairs for substitution
     * @return the rendered string
     */
    public static String renderPrompt(String template, Map<String, String> data) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String placeholder = "{{." + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
