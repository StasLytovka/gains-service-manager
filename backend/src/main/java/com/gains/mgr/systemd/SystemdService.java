package com.gains.mgr.systemd;

import com.gains.mgr.systemd.Models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Core service for managing systemd user services.
 *
 * <p>Each Gains tenant runs as a separate Linux user.
 * Their GUI server is a systemd --user service owned by that user.
 *
 * <p>To control another user's service we must run systemctl as that user:
 * sudo -u <user> env XDG_RUNTIME_DIR=/run/user/<uid> \
 * DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/<uid>/bus \
 * systemctl --user <action> <service>
 *
 * <p>This mirrors the svc_cmd() function in the original shell scripts.
 */
@Service
public class SystemdService {

    private static final Logger log = LoggerFactory.getLogger(SystemdService.class);

    /**
     * Static fallback map used when filesystem discovery fails.
     * Maps Linux username → service filename.
     * Matches the SERVICES array in the original shell scripts.
     */
    private static final Map<String, String> STATIC_SERVICE_MAP;

    static {
        // LinkedHashMap preserves insertion order (sorted by port)
        STATIC_SERVICE_MAP = new LinkedHashMap<>();
        STATIC_SERVICE_MAP.put("archpoint", "Gains8GuiServer20200.service");
        STATIC_SERVICE_MAP.put("batteriesplus", "Gains8GuiServer20400.service");
        STATIC_SERVICE_MAP.put("cbcgroup", "Gains8GuiServer20600.service");
        STATIC_SERVICE_MAP.put("decksdirect", "Gains8GuiServer20800.service");
        STATIC_SERVICE_MAP.put("khov", "Gains8GuiServer21000.service");
        STATIC_SERVICE_MAP.put("ncs", "Gains8GuiServer21200.service");
        STATIC_SERVICE_MAP.put("paladin", "Gains8GuiServer21400.service");
        STATIC_SERVICE_MAP.put("sps", "Gains8GuiServer21600.service");
        STATIC_SERVICE_MAP.put("terumobct", "Gains8GuiServer21800.service");
        STATIC_SERVICE_MAP.put("vallen", "Gains8GuiServer22000.service");
        STATIC_SERVICE_MAP.put("vallenusa", "Gains8GuiServer22200.service");
        STATIC_SERVICE_MAP.put("zoll", "Gains8GuiServer22400.service");
    }

    // ─────────────────────────────────────────────────────────────
    // Public API methods (called by ServiceController)
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns status of all discovered services.
     */
    public List<ServiceStatusResult> getAllStatuses() {
        return discoverServices().stream()
                .map(svc -> {
                    try {
                        return getStatus(svc.username(), svc.serviceName());
                    } catch (Exception e) {
                        // If one service fails, return an error row instead of crashing
                        return new ServiceStatusResult(
                                svc.username(), svc.serviceName(), svc.port(),
                                "error", "", "0", ""
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the status of a single service.
     */
    public ServiceStatusResult getStatus(String username, String serviceName) {
        long uid = getUserId(username);
        int port = portFromServiceName(serviceName);

        // systemctl show returns key=value pairs for the requested properties
        String output = runUserSystemctl(
                username, uid, List.of(
                        "show", serviceName,
                        "--property=ActiveState,SubState,MainPID,ActiveEnterTimestamp"
                )
        );

        // Parse "Key=Value\nKey=Value\n..." into a Map
        Map<String, String> props = Arrays.stream(output.split("\n"))
                .filter(line -> line.contains("="))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')),
                        line -> line.substring(line.indexOf('=') + 1),
                        (a, b) -> a  // keep first value on duplicate keys
                ));

        return new ServiceStatusResult(
                username,
                serviceName,
                port,
                props.getOrDefault("ActiveState", "unknown"),
                props.getOrDefault("SubState", ""),
                props.getOrDefault("MainPID", "0"),
                props.getOrDefault("ActiveEnterTimestamp", "")
        );
    }

    public ActionResult startService(String username, String serviceName) {
        return controlService(username, serviceName, "start");
    }

    public ActionResult stopService(String username, String serviceName) {
        return controlService(username, serviceName, "stop");
    }

    public ActionResult restartService(String username, String serviceName) {
        return controlService(username, serviceName, "restart");
    }

    /**
     * Returns the last N lines from journalctl for a service.
     */
    public LogResult getLogs(String username, String serviceName, int lines) {
        long uid = getUserId(username);
        String output = runJournalctl(username, uid, serviceName, lines);
        List<String> logLines = Arrays.stream(output.split("\n"))
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
        return new LogResult(username, serviceName, logLines);
    }

    /**
     * Starts all discovered services.
     */
    public BulkResult startAll() {
        return bulkAction("start");
    }

    /**
     * Stops all discovered services.
     */
    public BulkResult stopAll() {
        return bulkAction("stop");
    }

    /**
     * Returns the status of the PostgreSQL system service.
     */
    public PostgresStatus getPostgresStatus() {
        try {
            String status = exec(List.of("systemctl", "is-active", "postgresql"), 3).trim();
            return new PostgresStatus(status);
        } catch (Exception e) {
            return new PostgresStatus("unknown", e.getMessage());
        }
    }

    /**
     * Discovers all Gains services by scanning the filesystem.
     * Falls back to the static map if the scan fails or finds nothing.
     *
     * <p>Path pattern: /home/<user>/.config/systemd/user/Gains8GuiServer*.service
     * We skip files inside default.target.wants/ (those are symlinks to the same files).
     */
    private List<ServiceInfo> discoverServices() {
        List<ServiceInfo> discovered = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try {
            // Use sudo find so we can read all users' home directories
            String output = exec(
                    List.of(
                            "sudo", "find", "/home",
                            "-path", "*/.config/systemd/user/Gains8GuiServer*.service",
                            "-not", "-path", "*/default.target.wants/*"
                    ), 5
            );

            for (String path : output.split("\n")) {
                if (path.isBlank()) {
                    continue;
                }

                File svcFile = new File(path.trim());
                String svcName = svcFile.getName();

                // Skip port 8080 — explicitly excluded per requirements
                if (svcName.contains("8080")) {
                    continue;
                }

                // Extract the username from the path:
                // /home/batteriesplus/.config/systemd/user/Gains8GuiServer20400.service
                //       ^^^^^^^^^^^^^ this is parentFile x4 from the .service file
                String username = svcFile
                        .getParentFile()          // user/
                        .getParentFile()          // systemd/
                        .getParentFile()          // .config/
                        .getParentFile()          // batteriesplus/
                        .getName();

                int port = portFromServiceName(svcName);
                String key = username + "/" + svcName;

                if (!seen.contains(key)) {
                    seen.add(key);
                    discovered.add(new ServiceInfo(username, svcName, port));
                }
            }
        } catch (Exception e) {
            log.warn("Service auto-discovery failed, using static map: {}", e.getMessage());
        }

        // Fall back to static map if discovery found nothing
        if (discovered.isEmpty()) {
            log.info("Using static service map ({} services)", STATIC_SERVICE_MAP.size());
            STATIC_SERVICE_MAP.forEach((user, svc) ->
                                               discovered.add(new ServiceInfo(user, svc, portFromServiceName(svc))));
        }

        // Sort by port number for consistent display order
        discovered.sort(Comparator.comparingInt(ServiceInfo::port));
        return discovered;
    }

    /**
     * Runs start / stop / restart for a single service.
     */
    private ActionResult controlService(String username, String serviceName, String action) {
        long uid = getUserId(username);
        if (uid < 0) {
            return new ActionResult(false, "User '" + username + "' not found on this system");
        }
        try {
            String output = runUserSystemctl(username, uid, List.of(action, serviceName));
            return new ActionResult(true, action + " OK for " + serviceName, output);
        } catch (Exception e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    /**
     * Runs start/stop/restart/show for all services and aggregates results.
     */
    private BulkResult bulkAction(String action) {
        Map<String, ActionResult> results = new LinkedHashMap<>();
        for (ServiceInfo svc : discoverServices()) {
            String key = svc.username() + "/" + svc.serviceName();
            results.put(key, controlService(svc.username(), svc.serviceName(), action));
        }
        long successCount = results.values().stream().filter(ActionResult::success).count();
        long failCount = results.size() - successCount;
        return new BulkResult(results, (int) successCount, (int) failCount);
    }

    /**
     * Runs a 'systemctl --user' command as the target Linux user.
     *
     * <p>Equivalent to the shell svc_cmd() function:
     * sudo -u $USER env XDG_RUNTIME_DIR=/run/user/$UID
     * DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$UID/bus
     * systemctl --user <args>
     *
     * <p>XDG_RUNTIME_DIR and DBUS_SESSION_BUS_ADDRESS are required so that
     * systemctl can connect to the user's D-Bus session even without a GUI login.
     */
    private String runUserSystemctl(String username, long uid, List<String> args) {
        List<String> cmd = new ArrayList<>(List.of(
                "sudo", "-u", username,
                "env",
                "XDG_RUNTIME_DIR=/run/user/" + uid,
                "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" + uid + "/bus",
                "systemctl", "--user"
        ));
        cmd.addAll(args);
        return exec(cmd, 15);
    }

    /**
     * Runs journalctl to retrieve service logs.
     *
     * <p>Equivalent to:
     * sudo -u $USER env ... journalctl --user -u <service> -n <lines> --no-pager
     */
    private String runJournalctl(String username, long uid, String serviceName, int lines) {
        List<String> cmd = List.of(
                "sudo", "-u", username,
                "env",
                "XDG_RUNTIME_DIR=/run/user/" + uid,
                "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" + uid + "/bus",
                "journalctl", "--user",
                "-u", serviceName,
                "-n", String.valueOf(lines),
                "--no-pager",
                "--output=short-iso"
        );
        return exec(cmd, 15);
    }

    /**
     * Looks up the numeric UID of a Linux user via the 'id -u' command.
     */
    private long getUserId(String username) {
        try {
            return Long.parseLong(exec(List.of("id", "-u", username), 3).trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Extracts the port number from a service name.
     * "Gains8GuiServer20400.service" → 20400
     */
    private int portFromServiceName(String serviceName) {
        try {
            return Integer.parseInt(
                    serviceName.replace("Gains8GuiServer", "").replace(".service", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Executes an OS command and returns its combined stdout+stderr output.
     *
     * <p>ProcessBuilder is the standard Java API for running external processes.
     * redirectErrorStream(true) merges stderr into stdout so we get everything.
     *
     * @param cmd     command and its arguments as a list
     * @param timeout maximum seconds to wait for the command to finish
     * @return output string (may be empty)
     * @throws RuntimeException if the process times out
     */
    private String exec(List<String> cmd, long timeout) {
        log.debug("exec: {}", String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);   // merge stderr into stdout
            Process process = pb.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Command timed out: " + cmd.get(0));
            }

            return new String(process.getInputStream().readAllBytes());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute: " + cmd.get(0) + " — " + e.getMessage());
        }
    }
}
