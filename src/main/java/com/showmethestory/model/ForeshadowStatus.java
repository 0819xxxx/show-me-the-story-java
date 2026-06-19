package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Foreshadow lifecycle status.
 */
public enum ForeshadowStatus {

    PLANTED("planted"),
    PROGRESSING("progressing"),
    RESOLVED("resolved"),
    ABANDONED("abandoned");

    private final String value;

    ForeshadowStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ForeshadowStatus fromValue(String value) {
        for (ForeshadowStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown ForeshadowStatus: " + value);
    }
}
