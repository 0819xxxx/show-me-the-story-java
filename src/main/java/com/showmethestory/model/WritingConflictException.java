package com.showmethestory.model;

/**
 * Exception thrown when a writing conflict requires user intervention.
 * Wraps a {@link WritingConflict} describing the issue and suggested actions.
 */
public class WritingConflictException extends Exception {

    private final WritingConflict conflict;

    public WritingConflictException(WritingConflict conflict) {
        super(conflict != null ? conflict.getSummary() : "Writing conflict requires user intervention");
        this.conflict = conflict;
    }

    public WritingConflict getConflict() {
        return conflict;
    }
}
