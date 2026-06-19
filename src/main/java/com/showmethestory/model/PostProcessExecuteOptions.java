package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Options controlling post-process execution behaviour.
 */
public class PostProcessExecuteOptions {

    @JsonProperty("run_smooth_transitions_first")
    private boolean runSmoothTransitionsFirst;

    @JsonProperty("include_polish")
    private boolean includePolish;

    public PostProcessExecuteOptions() {}

    public PostProcessExecuteOptions(boolean runSmoothTransitionsFirst, boolean includePolish) {
        this.runSmoothTransitionsFirst = runSmoothTransitionsFirst;
        this.includePolish = includePolish;
    }

    public boolean isRunSmoothTransitionsFirst() { return runSmoothTransitionsFirst; }
    public void setRunSmoothTransitionsFirst(boolean runSmoothTransitionsFirst) { this.runSmoothTransitionsFirst = runSmoothTransitionsFirst; }

    public boolean isIncludePolish() { return includePolish; }
    public void setIncludePolish(boolean includePolish) { this.includePolish = includePolish; }
}
