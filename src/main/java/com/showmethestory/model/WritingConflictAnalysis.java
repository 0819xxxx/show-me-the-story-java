package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Internal AI analysis of a writing conflict (fact-check failures).
 */
public class WritingConflictAnalysis {

    private boolean reconcilable;
    private String summary;

    @JsonProperty("root_cause")
    private String rootCause;

    @JsonProperty("extra_constraints")
    private String extraConstraints;

    @JsonProperty("suggested_actions")
    private List<ConflictActionOption> suggestedActions;

    public WritingConflictAnalysis() {}

    public boolean isReconcilable() { return reconcilable; }
    public void setReconcilable(boolean reconcilable) { this.reconcilable = reconcilable; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getExtraConstraints() { return extraConstraints; }
    public void setExtraConstraints(String extraConstraints) { this.extraConstraints = extraConstraints; }

    public List<ConflictActionOption> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<ConflictActionOption> suggestedActions) { this.suggestedActions = suggestedActions; }
}
