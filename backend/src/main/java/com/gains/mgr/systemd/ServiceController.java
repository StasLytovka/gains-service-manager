package com.gains.mgr.systemd;

import com.gains.mgr.systemd.Models.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing systemd service management endpoints.
 *
 * All endpoints require a valid JWT token (enforced by JwtFilter + SecurityConfig).
 * The Angular frontend calls these endpoints from the dashboard.
 *
 * Base path: /api/services
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final SystemdService systemdService;

    // Spring injects the SystemdService bean via constructor injection
    public ServiceController(SystemdService systemdService) {
        this.systemdService = systemdService;
    }

    /**
     * GET /api/services
     * Returns the status of all discovered Gains services.
     * Called on dashboard load and every 30 seconds for auto-refresh.
     */
    @GetMapping
    public ResponseEntity<List<ServiceStatusResult>> getAllStatuses() {
        return ResponseEntity.ok(systemdService.getAllStatuses());
    }

    /**
     * GET /api/services/{username}/{serviceName}/status
     * Returns the status of one specific service.
     * Called after start/stop/restart to refresh a single row.
     *
     * @PathVariable - extracts {username} and {serviceName} from the URL path
     */
    @GetMapping("/{username}/{serviceName}/status")
    public ResponseEntity<ServiceStatusResult> getStatus(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.getStatus(username, serviceName));
    }

    /**
     * POST /api/services/{username}/{serviceName}/start
     * Starts a service.  Body is empty — all info is in the URL.
     */
    @PostMapping("/{username}/{serviceName}/start")
    public ResponseEntity<ActionResult> start(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.startService(username, serviceName));
    }

    /**
     * POST /api/services/{username}/{serviceName}/stop
     * Stops a service.
     */
    @PostMapping("/{username}/{serviceName}/stop")
    public ResponseEntity<ActionResult> stop(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.stopService(username, serviceName));
    }

    /**
     * POST /api/services/{username}/{serviceName}/restart
     * Restarts a service (stop + start in one systemd call).
     */
    @PostMapping("/{username}/{serviceName}/restart")
    public ResponseEntity<ActionResult> restart(
            @PathVariable String username,
            @PathVariable String serviceName) {
        return ResponseEntity.ok(systemdService.restartService(username, serviceName));
    }

    /**
     * GET /api/services/{username}/{serviceName}/logs?lines=50
     * Returns the last N lines from journalctl for a service.
     *
     * @RequestParam - reads the "lines" query parameter from the URL.
     *   defaultValue = "50" means ?lines=50 is assumed if not provided.
     */
    @GetMapping("/{username}/{serviceName}/logs")
    public ResponseEntity<LogResult> getLogs(
            @PathVariable String username,
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "50") int lines) {
        return ResponseEntity.ok(systemdService.getLogs(username, serviceName, lines));
    }

    /**
     * POST /api/services/start-all
     * Starts all discovered services in sequence.
     * Returns a summary: how many succeeded and how many failed.
     */
    @PostMapping("/start-all")
    public ResponseEntity<BulkResult> startAll() {
        return ResponseEntity.ok(systemdService.startAll());
    }

    /**
     * POST /api/services/stop-all
     * Stops all discovered services.
     */
    @PostMapping("/stop-all")
    public ResponseEntity<BulkResult> stopAll() {
        return ResponseEntity.ok(systemdService.stopAll());
    }

    /**
     * GET /api/services/postgres
     * Returns the status of the system-wide PostgreSQL service.
     * Displayed in the toolbar badge on the dashboard.
     */
    @GetMapping("/postgres")
    public ResponseEntity<PostgresStatus> getPostgresStatus() {
        return ResponseEntity.ok(systemdService.getPostgresStatus());
    }
}
