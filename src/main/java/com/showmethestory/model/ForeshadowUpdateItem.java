package com.showmethestory.model;

/**
 * A single foreshadow update returned by the AI tracking pipeline.
 */
public class ForeshadowUpdateItem {

    private int id;
    private String status;
    private String event;
    private String resolution;

    public ForeshadowUpdateItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
