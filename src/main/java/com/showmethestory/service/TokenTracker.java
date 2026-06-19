package com.showmethestory.service;

import com.showmethestory.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe token usage accumulator for a single async task.
 * Tracks committed (finalised) and pending (in-flight) prompt/completion tokens,
 * and emits throttled SSE updates via LogBroadcaster (2-second interval).
 */
public class TokenTracker {

    /** Throttle interval between SSE token-usage emissions (milliseconds). */
    private static final long TOKEN_EMIT_INTERVAL_MS = 2_000;

    /** Multiplier for estimating tokens from character count (1.5x for CJK). */
    private static final double TOKEN_ESTIMATE_MULTIPLIER = 1.5;

    private final ReentrantLock lock = new ReentrantLock();

    private int committedPrompt;
    private int committedCompletion;
    private int pendingPrompt;
    private int pendingCompletion;

    private final LogBroadcaster logger;
    private Instant lastEmit = Instant.EPOCH;

    public TokenTracker(LogBroadcaster logger) {
        this.logger = logger;
    }

    // ---------------------------------------------------------------
    // Lifecycle hooks called around each API call
    // ---------------------------------------------------------------

    /**
     * Called before an API call begins. Estimates prompt tokens from message content.
     */
    public void beginCall(List<Message> messages) {
        lock.lock();
        try {
            pendingPrompt = estimateTokensFromRunes(countMessageChars(messages));
            pendingCompletion = 0;
        } finally {
            lock.unlock();
        }
        maybeEmit(true);
    }

    /**
     * Called during streaming to update the completion token estimate from
     * accumulated content so far.
     */
    public void updateStreamContent(String content) {
        lock.lock();
        try {
            pendingCompletion = estimateTokensFromRunes(charCount(content));
        } finally {
            lock.unlock();
        }
        maybeEmit(false);
    }

    /**
     * Called after an API call completes. Commits the final token counts.
     *
     * @param promptTokens     actual prompt tokens from the API (if hasUsage is true)
     * @param completionTokens actual completion tokens from the API (if hasUsage is true)
     * @param hasUsage         whether the API returned real usage numbers
     * @param messages         the messages sent (used for estimation when hasUsage is false)
     * @param output           the output text (used for estimation when hasUsage is false)
     */
    public void finishCall(int promptTokens, int completionTokens, boolean hasUsage,
                           List<Message> messages, String output) {
        lock.lock();
        try {
            if (hasUsage) {
                committedPrompt += promptTokens;
                committedCompletion += completionTokens;
            } else {
                committedPrompt += estimateTokensFromRunes(countMessageChars(messages));
                committedCompletion += estimateTokensFromRunes(charCount(output));
            }
            pendingPrompt = 0;
            pendingCompletion = 0;
        } finally {
            lock.unlock();
        }
        maybeEmit(true);
    }

    /**
     * Take a snapshot of current total token usage (committed + pending).
     *
     * @return int[2] where [0] = prompt tokens, [1] = completion tokens
     */
    public int[] snapshot() {
        lock.lock();
        try {
            return new int[]{
                    committedPrompt + pendingPrompt,
                    committedCompletion + pendingCompletion
            };
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------
    // Throttled SSE emission
    // ---------------------------------------------------------------

    private void maybeEmit(boolean force) {
        if (logger == null) return;
        lock.lock();
        int prompt;
        int completion;
        try {
            Instant now = Instant.now();
            if (!force && lastEmit != Instant.EPOCH
                    && now.toEpochMilli() - lastEmit.toEpochMilli() < TOKEN_EMIT_INTERVAL_MS) {
                return;
            }
            prompt = committedPrompt + pendingPrompt;
            completion = committedCompletion + pendingCompletion;
            lastEmit = now;
        } finally {
            lock.unlock();
        }
        logger.tokenUsage(prompt, completion);
    }

    // ---------------------------------------------------------------
    // Token estimation
    // ---------------------------------------------------------------

    /**
     * Estimate token count from character (rune) count.
     * Uses 1.5x multiplier to account for CJK characters that typically
     * tokenise to more tokens than Latin characters.
     */
    public static int estimateTokensFromRunes(int runes) {
        return (int) (runes * TOKEN_ESTIMATE_MULTIPLIER);
    }

    private static int countMessageChars(List<Message> messages) {
        int n = 0;
        if (messages != null) {
            for (Message m : messages) {
                if (m.getContent() != null) {
                    n += charCount(m.getContent());
                }
            }
        }
        return n;
    }

    /**
     * Count Unicode code points (equivalent to Go's utf8.RuneCountInString).
     */
    private static int charCount(String s) {
        if (s == null) return 0;
        return s.codePointCount(0, s.length());
    }
}
