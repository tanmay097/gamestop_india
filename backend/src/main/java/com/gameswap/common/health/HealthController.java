package com.gameswap.common.health;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcClient jdbcClient;

    public HealthController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @GetMapping
    public Map<String, Object> health() {
        String postgisVersion = "unknown";
        boolean dbUp = false;
        try {
            postgisVersion = jdbcClient.sql("SELECT PostGIS_Version()").query(String.class).single();
            dbUp = true;
        } catch (Exception ignored) {
        }
        return Map.of(
            "status", dbUp ? "UP" : "DEGRADED",
            "service", "gameswap-backend",
            "database", dbUp ? "UP" : "DOWN",
            "postgis", postgisVersion,
            "timestamp", Instant.now().toString()
        );
    }
}
