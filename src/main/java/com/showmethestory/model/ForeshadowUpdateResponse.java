package com.showmethestory.model;

import java.util.List;

/**
 * AI response wrapping a list of foreshadow updates after chapter writing.
 */
public class ForeshadowUpdateResponse {

    private List<ForeshadowUpdateItem> updates;

    public ForeshadowUpdateResponse() {}

    public List<ForeshadowUpdateItem> getUpdates() { return updates; }
    public void setUpdates(List<ForeshadowUpdateItem> updates) { this.updates = updates; }
}
