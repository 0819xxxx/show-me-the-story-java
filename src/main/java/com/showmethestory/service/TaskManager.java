package com.showmethestory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton task mutex that enforces single-task concurrency.
 * Maps to Go: Handlers.taskMu / taskRunning / activeWork / taskCtx / taskCancel.
 *
 * Only one top-level AI task can run at a time. Child work (e.g. agent sub-tasks)
 * increments the active-work counter without starting a new task context.
 */
@Service
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final ReentrantLock taskMu = new ReentrantLock();
    private volatile boolean taskRunning = false;
    private volatile int activeWork = 0;
    private volatile boolean autoConfirm = false;

    /** Cancellation token for the current task; null when no task is running. */
    private volatile OpenAIClient.CancellationToken taskContext = null;

    // ---------------------------------------------------------------
    // Task lifecycle
    // ---------------------------------------------------------------

    /**
     * Try to start a new top-level task.
     * Returns true if the task was started, false if one is already running.
     */
    public boolean tryStartTask() {
        taskMu.lock();
        try {
            if (taskRunning || activeWork > 0) {
                return false;
            }
            taskRunning = true;
            activeWork = 1;
            taskContext = new OpenAIClient.CancellationToken();
            return true;
        } finally {
            taskMu.unlock();
        }
    }

    /**
     * Signal that the current top-level task has finished.
     * Decrements active work; when it reaches zero the task is considered done.
     */
    public void endTask() {
        taskMu.lock();
        try {
            activeWork--;
            if (activeWork <= 0) {
                activeWork = 0;
                taskRunning = false;
                if (taskContext != null) {
                    taskContext.cancel();
                    taskContext = null;
                }
            }
        } finally {
            taskMu.unlock();
        }
    }

    /**
     * Increment the active-work counter for a child sub-task (e.g. agent loop action).
     * Returns true if the child work was registered, false if no parent task is running.
     */
    public boolean startChildWork() {
        taskMu.lock();
        try {
            if (!taskRunning) {
                return false;
            }
            activeWork++;
            return true;
        } finally {
            taskMu.unlock();
        }
    }

    /**
     * Check whether any task (top-level or child) is currently running.
     */
    public boolean isTaskRunning() {
        taskMu.lock();
        try {
            return taskRunning || activeWork > 0;
        } finally {
            taskMu.unlock();
        }
    }

    /**
     * Request cancellation of the currently running task.
     */
    public void cancelTask() {
        OpenAIClient.CancellationToken ctx = taskContext;
        if (ctx != null) {
            ctx.cancel();
        }
    }

    /**
     * Check if the current task has been cancelled.
     */
    public boolean isCancelled() {
        OpenAIClient.CancellationToken ctx = taskContext;
        return ctx != null && ctx.isCancelled();
    }

    /**
     * Get the cancellation token for the current task.
     * Returns null if no task is running.
     */
    public OpenAIClient.CancellationToken getTaskContext() {
        return taskContext;
    }

    // ---------------------------------------------------------------
    // Auto-confirm mode
    // ---------------------------------------------------------------

    public boolean isAutoConfirm() {
        taskMu.lock();
        try {
            return autoConfirm;
        } finally {
            taskMu.unlock();
        }
    }

    public void setAutoConfirm(boolean enabled) {
        taskMu.lock();
        try {
            this.autoConfirm = enabled;
        } finally {
            taskMu.unlock();
        }
    }
}
