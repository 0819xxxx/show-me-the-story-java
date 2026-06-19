package com.showmethestory.controller;

import com.showmethestory.service.LogBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint for real-time log streaming.
 * Maps to Go: GET /api/events -> SSEHandler
 */
@RestController
public class SSEController {

    private final LogBroadcaster logBroadcaster;

    public SSEController(LogBroadcaster logBroadcaster) {
        this.logBroadcaster = logBroadcaster;
    }

    /**
     * Go: SSEHandler - subscribes to LogBroadcaster and streams SSE events.
     * Uses SseEmitter for Spring's SSE support.
     * LogBroadcaster.subscribe() creates the emitter, registers it, and
     * sets up onCompletion/onTimeout/onError callbacks internally.
     */
    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return logBroadcaster.subscribe();
    }
}
