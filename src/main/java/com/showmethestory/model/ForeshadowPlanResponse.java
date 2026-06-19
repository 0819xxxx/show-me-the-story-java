package com.showmethestory.model;

import java.util.List;

/**
 * AI response wrapping a list of foreshadow suggestions.
 */
public class ForeshadowPlanResponse {

    private List<ForeshadowSuggestion> foreshadows;

    public ForeshadowPlanResponse() {}

    public List<ForeshadowSuggestion> getForeshadows() { return foreshadows; }
    public void setForeshadows(List<ForeshadowSuggestion> foreshadows) { this.foreshadows = foreshadows; }
}
