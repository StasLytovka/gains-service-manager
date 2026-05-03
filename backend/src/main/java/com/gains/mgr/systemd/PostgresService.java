package com.gains.mgr.systemd;

import com.gains.mgr.systemd.Models.ActionResult;
import com.gains.mgr.systemd.PostgresModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PostgresService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresService.class);

    private final ShellExecutor shell;

    public PostgresService(ShellExecutor shell) {
        this.shell = shell;
    }

    public PostgresStatus getStatus() {
        try {
            String status = shell.exec(List.of("systemctl", "is-active", "postgresql"), 3).trim();
            return new PostgresStatus(status);
        } catch (Exception e) {
            return new PostgresStatus("unknown", e.getMessage());
        }
    }

    public ActionResult start() {
        try {
            LOGGER.info("ACTION [START] PostgreSQL (system service)");
            String output = shell.exec(List.of("sudo", "systemctl", "start", "postgresql"), 15);
            LOGGER.info("ACTION [START] PostgreSQL COMPLETED");
            return new ActionResult(true, "PostgreSQL start OK", output);
        } catch (Exception e) {
            LOGGER.error("ACTION [START] PostgreSQL FAILED: {}", e.getMessage());
            return new ActionResult(false, "Failed to start PostgreSQL: " + e.getMessage());
        }
    }

    public ActionResult stop() {
        try {
            LOGGER.info("ACTION [STOP] PostgreSQL (system service)");
            String output = shell.exec(List.of("sudo", "systemctl", "stop", "postgresql"), 15);
            LOGGER.info("ACTION [STOP] PostgreSQL COMPLETED");
            return new ActionResult(true, "PostgreSQL stop OK", output);
        } catch (Exception e) {
            LOGGER.error("ACTION [STOP] PostgreSQL FAILED: {}", e.getMessage());
            return new ActionResult(false, "Failed to stop PostgreSQL: " + e.getMessage());
        }
    }

    public PgHealthResult getHealth() {
        List<PgHealthMetric> metrics = new ArrayList<>();
        int totalScore = 0;
        int checks = 0;

        try {
            String connInfo = psql(
                "SELECT numbackends, " +
                "(SELECT setting::int FROM pg_settings WHERE name='max_connections') " +
                "FROM pg_stat_database WHERE datname = current_database()"
            );
            String[] connParts = connInfo.trim().split("\\|");
            if (connParts.length == 2) {
                int active = Integer.parseInt(connParts[0].trim());
                int max = Integer.parseInt(connParts[1].trim());
                double pct = (double) active / max * 100;
                String status = pct < 70 ? "OK" : pct < 90 ? "Warning" : "Critical";
                int s = pct < 70 ? 100 : pct < 90 ? 60 : 20;
                metrics.add(new PgHealthMetric("Connections", active + " / " + max, status));
                totalScore += s;
                checks++;
            }
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Connections", "N/A", "Unknown"));
        }

        try {
            String hitRatio = psql(
                "SELECT ROUND(100.0 * sum(blks_hit) / NULLIF(sum(blks_hit) + sum(blks_read), 0), 1) " +
                "FROM pg_stat_database"
            ).trim();
            if (!hitRatio.isEmpty() && !hitRatio.contains("null")) {
                double ratio = Double.parseDouble(hitRatio);
                String status = ratio >= 99 ? "OK" : ratio >= 95 ? "Warning" : "Critical";
                int s = ratio >= 99 ? 100 : ratio >= 95 ? 60 : 20;
                metrics.add(new PgHealthMetric("Cache Hit Ratio", hitRatio + "%", status));
                totalScore += s;
                checks++;
            }
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Cache Hit Ratio", "N/A", "Unknown"));
        }

        try {
            String activeQ = psql(
                "SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND pid <> pg_backend_pid()"
            ).trim();
            int aq = Integer.parseInt(activeQ);
            String status = aq < 20 ? "OK" : aq < 50 ? "Warning" : "Critical";
            int s = aq < 20 ? 100 : aq < 50 ? 60 : 20;
            metrics.add(new PgHealthMetric("Active Queries", String.valueOf(aq), status));
            totalScore += s;
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Active Queries", "N/A", "Unknown"));
        }

        try {
            String longQ = psql(
                "SELECT count(*) FROM pg_stat_activity " +
                "WHERE state = 'active' AND now() - query_start > interval '30 seconds' AND pid <> pg_backend_pid()"
            ).trim();
            int lq = Integer.parseInt(longQ);
            String status = lq == 0 ? "OK" : lq <= 2 ? "Warning" : "Critical";
            int s = lq == 0 ? 100 : lq <= 2 ? 60 : 20;
            metrics.add(new PgHealthMetric("Long Queries (>30s)", String.valueOf(lq), status));
            totalScore += s;
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Long Queries (>30s)", "N/A", "Unknown"));
        }

        try {
            String size = psql("SELECT pg_size_pretty(pg_database_size(current_database()))").trim();
            metrics.add(new PgHealthMetric("DB Size", size, "Info"));
        } catch (Exception ignored) {
            // non-critical metric
        }

        try {
            String uptime = psql("SELECT now() - pg_postmaster_start_time()").trim();
            String formatted = uptime.contains(".") ? uptime.substring(0, uptime.indexOf('.')) : uptime;
            metrics.add(new PgHealthMetric("Uptime", formatted, "Info"));
        } catch (Exception ignored) {
            // non-critical metric
        }

        int score = checks > 0 ? totalScore / checks : 0;
        String level = score >= 80 ? "Good" : score >= 50 ? "Fair" : "Poor";

        return new PgHealthResult(score, level, metrics);
    }

    private String psql(String sql) {
        return shell.exec(List.of("sudo", "-u", "postgres", "psql", "-t", "-A", "-c", sql), 5);
    }
}
