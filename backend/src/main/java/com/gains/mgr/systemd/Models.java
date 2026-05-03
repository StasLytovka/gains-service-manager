package com.gains.mgr.systemd;

import java.util.List;
import java.util.Map;

/**
 * Data models (DTOs) for the service management layer.
 *
 *<p>All classes use Java 17 'record' syntax.
 * A record is an immutable data carrier — the compiler automatically generates:
 *   - a constructor with all fields
 *   - accessor methods (no "get" prefix): username(), serviceName(), etc.
 *   - equals(), hashCode(), toString()
 *
 *<p>Records are ideal for DTOs because they hold data and nothing else.
 */
public class Models {

    /**
     * Represents a discovered systemd user service.
     * Used internally during service auto-discovery.
     */
    public record ServiceInfo(
            String username,     // Linux user who owns the service (e.g. "batteriesplus")
            String serviceName,  // systemd service file name (e.g. "Gains8GuiServer20400.service")
            int port             // port number extracted from the service name (e.g. 20400)
    ) {}

    /**
     * Full status of a single service — returned by GET /api/services.
     *
     *<p>Field descriptions match systemd 'show' property names.
     */
    public record ServiceStatusResult(
            String username,
            String serviceName,
            int port,
            String status,      // ActiveState:  active | inactive | failed | unknown
            String subState,    // SubState:     running | dead | exited | ...
            String pid,         // MainPID:      process ID, "0" if not running
            String since        // ActiveEnterTimestamp: when the service last became active
    ) {}

    /**
     * Result of a start / stop / restart action on a single service.
     */
    public record ActionResult(
            boolean success,
            String message,
            String output       // raw stdout/stderr from the systemctl command
    ) {
        // Convenience constructor without 'output' — used when there is no relevant output
        public ActionResult(boolean success, String message) {
            this(success, message, "");
        }
    }

    /**
     * journalctl log output for a service.
     */
    public record LogResult(
            String username,
            String serviceName,
            List<String> lines  // individual log lines, newest last
    ) {}

    /**
     * PostgreSQL service status — returned by GET /api/services/postgres.
     */
    public record PostgresStatus(
            String status,   // systemd active state: "active", "inactive", "unknown"
            String message   // additional info or error message
    ) {
        public PostgresStatus(String status) {
            this(status, "");
        }
    }

    /**
     * Aggregated result of a bulk start-all or stop-all operation.
     *
     *<p>'results' maps "<username>/<serviceName>" → ActionResult for each service.
     */
    public record BulkResult(
            Map<String, ActionResult> results,
            int successCount,
            int failCount
    ) {}
}
