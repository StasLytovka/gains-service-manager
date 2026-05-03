package com.gains.mgr.systemd;

import com.gains.mgr.systemd.Models.ActionResult;
import com.gains.mgr.systemd.PostgresModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/postgres")
public class PostgresController {

    private final PostgresService postgresService;

    public PostgresController(PostgresService postgresService) {
        this.postgresService = postgresService;
    }

    @GetMapping
    public ResponseEntity<PostgresStatus> getStatus() {
        return ResponseEntity.ok(postgresService.getStatus());
    }

    @PostMapping("/start")
    public ResponseEntity<ActionResult> start() {
        return ResponseEntity.ok(postgresService.start());
    }

    @PostMapping("/stop")
    public ResponseEntity<ActionResult> stop() {
        return ResponseEntity.ok(postgresService.stop());
    }

    @GetMapping("/health")
    public ResponseEntity<PgHealthResult> getHealth() {
        return ResponseEntity.ok(postgresService.getHealth());
    }
}
