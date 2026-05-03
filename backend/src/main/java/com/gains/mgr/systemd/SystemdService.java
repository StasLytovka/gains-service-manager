package com.gains.mgr.systemd;

import com.gains.mgr.systemd.Models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemdService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemdService.class);

    private static final Map<String, String> STATIC_SERVICE_MAP;

    static {
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

    private final ShellExecutor shell;

    public SystemdService(ShellExecutor shell) {
        this.shell = shell;
    }

    public List<ServiceStatusResult> getAllStatuses() {
        return discoverServices().stream()
                .map(svc -> {
                    try {
                        return getStatus(svc.username(), svc.serviceName());
                    } catch (Exception e) {
                        return new ServiceStatusResult(
                                svc.username(), svc.serviceName(), svc.port(),
                                "error", "", "0", ""
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    public ServiceStatusResult getStatus(String username, String serviceName) {
        long uid = getUserId(username);
        int port = portFromServiceName(serviceName);

        String output = runUserSystemctl(
                username, uid, List.of(
                        "show", serviceName,
                        "--property=ActiveState,SubState,MainPID,ActiveEnterTimestamp"
                )
        );

        Map<String, String> props = Arrays.stream(output.split("\n"))
                .filter(line -> line.contains("="))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf('=')),
                        line -> line.substring(line.indexOf('=') + 1),
                        (a, b) -> a
                ));

        return new ServiceStatusResult(
                username, serviceName, port,
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

    public LogResult getLogs(String username, String serviceName, int lines) {
        long uid = getUserId(username);
        String output = runJournalctl(username, uid, serviceName, lines);
        List<String> logLines = Arrays.stream(output.split("\n"))
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
        return new LogResult(username, serviceName, logLines);
    }

    public BulkResult startAll() {
        return bulkAction("start");
    }

    public BulkResult stopAll() {
        return bulkAction("stop");
    }

    private List<ServiceInfo> discoverServices() {
        List<ServiceInfo> discovered = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try {
            String output = shell.exec(
                    List.of(
                            "sudo", "find", "/home",
                            "-path", "*/.config/systemd/user/Gains8GuiServer*.service",
                            "-not", "-path", "*/default.target.wants/*"
                    ), 5
            );

            for (String path : output.split("\n")) {
                if (path.isBlank()) continue;

                File svcFile = new File(path.trim());
                String svcName = svcFile.getName();

                if (svcName.contains("8080")) continue;

                String username = svcFile
                        .getParentFile().getParentFile()
                        .getParentFile().getParentFile()
                        .getName();

                int port = portFromServiceName(svcName);
                String key = username + "/" + svcName;

                if (!seen.contains(key)) {
                    seen.add(key);
                    discovered.add(new ServiceInfo(username, svcName, port));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Service auto-discovery failed, using static map: {}", e.getMessage());
        }

        if (discovered.isEmpty()) {
            LOGGER.info("Using static service map ({} services)", STATIC_SERVICE_MAP.size());
            STATIC_SERVICE_MAP.forEach((user, svc) ->
                    discovered.add(new ServiceInfo(user, svc, portFromServiceName(svc))));
        }

        discovered.sort(Comparator.comparingInt(ServiceInfo::port));
        return discovered;
    }

    private ActionResult controlService(String username, String serviceName, String action) {
        long uid = getUserId(username);
        if (uid < 0) {
            return new ActionResult(false, "User '" + username + "' not found on this system");
        }
        try {
            LOGGER.info("ACTION [{}] user={} service={}", action.toUpperCase(), username, serviceName);
            String output = runUserSystemctl(username, uid, List.of(action, serviceName));
            LOGGER.info("ACTION [{}] COMPLETED user={} service={}", action.toUpperCase(), username, serviceName);
            return new ActionResult(true, action + " OK for " + serviceName, output);
        } catch (Exception e) {
            LOGGER.error("ACTION [{}] FAILED user={} service={} error={}",
                    action.toUpperCase(), username, serviceName, e.getMessage());
            return new ActionResult(false, e.getMessage());
        }
    }

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

    private String runUserSystemctl(String username, long uid, List<String> args) {
        List<String> cmd = new ArrayList<>(List.of(
                "sudo", "-u", username,
                "env",
                "XDG_RUNTIME_DIR=/run/user/" + uid,
                "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" + uid + "/bus",
                "systemctl", "--user"
        ));
        cmd.addAll(args);
        return shell.exec(cmd, 15);
    }

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
        return shell.exec(cmd, 15);
    }

    private long getUserId(String username) {
        try {
            return Long.parseLong(shell.exec(List.of("id", "-u", username), 3).trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    private int portFromServiceName(String serviceName) {
        try {
            return Integer.parseInt(
                    serviceName.replace("Gains8GuiServer", "").replace(".service", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
