package com.showmethestory.model;

/**
 * A Server-Sent Event (SSE) message wrapper.
 */
public class SSEMessage {

    private String event;
    private Object data;

    public SSEMessage() {}

    public SSEMessage(String event, Object data) {
        this.event = event;
        this.data = data;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
