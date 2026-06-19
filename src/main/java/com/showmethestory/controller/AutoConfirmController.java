package com.showmethestory.controller;

import com.showmethestory.service.TaskManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auto-confirm toggle and task stop endpoints.
 * Maps to Go handlers: GetAutoConfirm, PutAutoConfirm, PostTaskStop
 */
@RestController
@RequestMapping("/api")
public class AutoConfirmController {

    private final TaskManager taskManager;

    public AutoConfirmController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    /**
     * Go: GetAutoConfirm - returns whether auto-confirm mode is enabled.
     * Response: {enabled: bool}
     * Auto-confirm mode: when on, chapters are automatically confirmed after generation
     * and the next chapter begins generating automatically.
     */
    @GetMapping("/autoconfirm")
    public ResponseEntity<Map<String, Boolean>> getAutoConfirm() {
        return ResponseEntity.ok(Map.of("enabled", taskManager.isAutoConfirm()));
    }

    /**
     * Go: PutAutoConfirm - toggle auto-confirm mode on/off.
     * Body: {enabled: bool}
     * Can be toggled even while tasks are running.
     * Logs the toggle action.
     */
    @PutMapping("/autoconfirm")
    public ResponseEntity<Map<String, Boolean>> putAutoConfirm(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", false));
        }

        taskManager.setAutoConfirm(enabled);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    /**
     * Go: PostTaskStop - cancel the currently running task.
     * Sends cancellation signal to the task's context.
     * Returns {status: "stopping"} on success.
     * Returns 400 if no task is running.
     */
    @PostMapping("/task/stop")
    public ResponseEntity<?> stopTask() {
        if (!taskManager.isTaskRunning()) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_task_running"));
        }

        taskManager.cancelTask();
        return ResponseEntity.ok(Map.of("status", "stopping"));
    }
}
