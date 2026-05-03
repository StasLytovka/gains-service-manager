package com.gains.mgr.systemd;

import java.util.List;

public class PostgresModels {

    public record PostgresStatus(
            String status,
            String message
    ) {
        public PostgresStatus(String status) {
            this(status, "");
        }
    }

    public record PgHealthMetric(
            String name,
            String value,
            String status
    ) {
    }

    public record PgHealthResult(
            int score,
            String level,
            List<PgHealthMetric> metrics
    ) {
    }
}
