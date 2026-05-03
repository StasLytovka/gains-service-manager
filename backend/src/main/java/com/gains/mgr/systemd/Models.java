package com.gains.mgr.systemd;

import java.util.List;
import java.util.Map;

public class Models {

    public record ServiceInfo(
            String username,
            String serviceName,
            int port
    ) {}

    public record ServiceStatusResult(
            String username,
            String serviceName,
            int port,
            String status,
            String subState,
            String pid,
            String since
    ) {}

    public record ActionResult(
            boolean success,
            String message,
            String output
    ) {
        public ActionResult(boolean success, String message) {
            this(success, message, "");
        }
    }

    public record LogResult(
            String username,
            String serviceName,
            List<String> lines
    ) {}

    public record BulkResult(
            Map<String, ActionResult> results,
            int successCount,
            int failCount
    ) {}
}
