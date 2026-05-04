package com.gains.mgr.systemd;

import com.gains.mgr.systemd.ServiceModels.ActionResult;
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
                "SELECT sum(numbackends), " +
                "(SELECT setting::int FROM pg_settings WHERE name='max_connections') " +
                "FROM pg_stat_database"
            );
            String[] connParts = connInfo.trim().split("\\|");
            if (connParts.length == 2) {
                int active = Integer.parseInt(connParts[0].trim());
                int max = Integer.parseInt(connParts[1].trim());
                double pct = (double) active / max * 100;
                String[] eval = evaluate(pct, 70, 90);
                metrics.add(new PgHealthMetric("Connections", active + " / " + max, eval[0]));
                totalScore += Integer.parseInt(eval[1]);
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
                String[] eval = evaluateReverse(ratio, 95, 90);
                metrics.add(new PgHealthMetric("Cache Hit Ratio", hitRatio + "%", eval[0]));
                totalScore += Integer.parseInt(eval[1]);
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
            String[] eval = evaluate(aq, 20, 50);
            metrics.add(new PgHealthMetric("Active Queries", String.valueOf(aq), eval[0]));
            totalScore += Integer.parseInt(eval[1]);
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
            String[] eval = evaluate(lq, 1, 3);
            metrics.add(new PgHealthMetric("Long Queries (>30s)", String.valueOf(lq), eval[0]));
            totalScore += Integer.parseInt(eval[1]);
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Long Queries (>30s)", "N/A", "Unknown"));
        }

        // Locks
        try {
            String locks = psql("SELECT count(*) FROM pg_locks WHERE granted = false").trim();
            int lk = Integer.parseInt(locks);
            String[] eval = evaluate(lk, 1, 5);
            metrics.add(new PgHealthMetric("Waiting Locks", String.valueOf(lk), eval[0]));
            totalScore += Integer.parseInt(eval[1]);
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Waiting Locks", "N/A", "Unknown"));
        }

        // Oldest transaction
        try {
            String oldest = psql(
                "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(xact_start)))::int, 0) " +
                "FROM pg_stat_activity WHERE xact_start IS NOT NULL AND pid <> pg_backend_pid()"
            ).trim();
            int sec = Integer.parseInt(oldest);
            String[] eval = evaluate(sec, 300, 900);
            String display = sec < 60 ? sec + "s" : (sec / 60) + "m " + (sec % 60) + "s";
            metrics.add(new PgHealthMetric("Oldest Transaction", display, eval[0]));
            totalScore += Integer.parseInt(eval[1]);
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Oldest Transaction", "N/A", "Unknown"));
        }

        // Temp files (work_mem pressure)
        try {
            String tempInfo = psql(
              "SELECT COALESCE(sum(temp_files), 0), pg_size_pretty(COALESCE(sum(temp_bytes), 0)) FROM pg_stat_database")
                    .trim();
            String[] parts = tempInfo.split("\\|");
            if (parts.length == 2) {
                String display = parts[0].trim() + " files / " + parts[1].trim();
                metrics.add(new PgHealthMetric("Temp Files", display, "Info"));
            }
        } catch (Exception ignored) {
            // non-critical metric
        }

        // Dead tuples ratio (across all tenant DBs)
        try {
            String deadInfo = shell.exec(List.of(
                "sudo", "-u", "postgres", "psql", "-t", "-A", "-c",
                "SELECT datname FROM pg_database WHERE datname NOT IN ('postgres','template0','template1')"
            ), 5);
            long totalDead = 0;
            long totalLive = 0;
            for (String db : deadInfo.split("\n")) {
                if (db.isBlank()) continue;
                String row = shell.exec(List.of(
                    "sudo", "-u", "postgres", "psql", "-t", "-A", "-d", db.trim(), "-c",
                    "SELECT COALESCE(sum(n_dead_tup),0), COALESCE(sum(n_live_tup),0) FROM pg_stat_user_tables"
                ), 5).trim();
                String[] parts = row.split("\\|");
                if (parts.length == 2) {
                    totalDead += Long.parseLong(parts[0].trim());
                    totalLive += Long.parseLong(parts[1].trim());
                }
            }
            double dp = (totalDead + totalLive) > 0
                    ? totalDead * 100.0 / (totalDead + totalLive) : 0;
            String deadPct = String.valueOf(Math.round(dp * 10) / 10.0);
            String[] eval = evaluate(dp, 10, 25);
            metrics.add(new PgHealthMetric("Dead Tuples", deadPct + "%", eval[0]));
            totalScore += Integer.parseInt(eval[1]);
            checks++;
        } catch (Exception e) {
            metrics.add(new PgHealthMetric("Dead Tuples", "N/A", "Unknown"));
        }

        // Autovacuum workers
        try {
            String av = psql(
                "SELECT count(*) FROM pg_stat_activity WHERE query LIKE 'autovacuum%'"
            ).trim();
            metrics.add(new PgHealthMetric("Autovacuum Workers", av, "Info"));
        } catch (Exception ignored) {
            // non-critical metric
        }

        // Info metrics
        try {
            String size = psql("SELECT pg_size_pretty(sum(pg_database_size(datname))) FROM pg_database WHERE datistemplate = false").trim();
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

        // Cache hit per database (multi-tenant insight)
        try {
            String perDb = psql(
                "SELECT datname || '|' || ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 1) " +
                "FROM pg_stat_database " +
                "WHERE datname NOT IN ('postgres','template0','template1') " +
                "ORDER BY blks_hit / NULLIF(blks_hit + blks_read, 0.0001) ASC"
            );
            for (String line : perDb.split("\n")) {
                String[] parts = line.trim().split("\\|");
                if (parts.length == 2 && !parts[1].isEmpty()) {
                    double hit = Double.parseDouble(parts[1]);
                    String[] eval = evaluateReverse(hit, 95, 90);
                    metrics.add(new PgHealthMetric("Cache: " + parts[0], parts[1] + "%", eval[0]));
                }
            }
        } catch (Exception ignored) {
            // non-critical metric
        }

        int score = checks > 0 ? totalScore / checks : 0;
        String level;
        if (score >= 80) {
            level = "Good";
        } else if (score >= 50) {
            level = "Fair";
        } else {
            level = "Poor";
        }

        return new PgHealthResult(score, level, metrics);
    }

    private String[] evaluate(double value, double warnThreshold, double critThreshold) {
        if (value < warnThreshold) {
            return new String[]{"OK", "100"};
        } else if (value < critThreshold) {
            return new String[]{"Warning", "60"};
        }
        return new String[]{"Critical", "20"};
    }

    private String[] evaluateReverse(double value, double okThreshold, double warnThreshold) {
        if (value >= okThreshold) {
            return new String[]{"OK", "100"};
        } else if (value >= warnThreshold) {
            return new String[]{"Warning", "60"};
        }
        return new String[]{"Critical", "20"};
    }

    private String psql(String sql) {
        return shell.exec(List.of("sudo", "-u", "postgres", "psql", "-t", "-A", "-c", sql), 5);
    }
}
