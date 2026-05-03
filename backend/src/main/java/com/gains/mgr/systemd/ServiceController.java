package com.gains.mgr.systemd;

import com.gains.mgr.systemd.ServiceModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final SystemdService systemdService;

    public ServiceController(SystemdService systemdService) {
        this.systemdService = systemdService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceStatusResult>> getAllStatuses() {
        return ResponseEntity.ok(systemdService.getAllStatuses());
    }

    @GetMapping("/{username}/{serviceName}/status")
    public ResponseEntity<ServiceStatusResult> getStatus(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.getStatus(username, serviceName));
    }

    @PostMapping("/{username}/{serviceName}/start")
    public ResponseEntity<ActionResult> start(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.startService(username, serviceName));
    }

    @PostMapping("/{username}/{serviceName}/stop")
    public ResponseEntity<ActionResult> stop(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.stopService(username, serviceName));
    }

    @PostMapping("/{username}/{serviceName}/restart")
    public ResponseEntity<ActionResult> restart(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.restartService(username, serviceName));
    }

    @GetMapping("/{username}/{serviceName}/logs")
    public ResponseEntity<LogResult> getLogs(
            @PathVariable String username,
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "50") int lines) {
        return ResponseEntity.ok(systemdService.getLogs(username, serviceName, lines));
    }

    @PostMapping("/start-all")
    public ResponseEntity<BulkResult> startAll() {
        return ResponseEntity.ok(systemdService.startAll());
    }

    @PostMapping("/stop-all")
    public ResponseEntity<BulkResult> stopAll() {
        return ResponseEntity.ok(systemdService.stopAll());
    }
}
