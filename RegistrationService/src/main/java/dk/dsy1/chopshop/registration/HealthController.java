package dk.dsy1.chopshop.registration;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Checks the service and its own database without changing data. */
@RestController
@CrossOrigin(origins = "http://localhost:8000")
public class HealthController {
    private final JdbcTemplate database;
    public HealthController(JdbcTemplate database) { this.database = database; }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            database.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "available"));
        } catch (org.springframework.dao.DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("status", "degraded"));
        }
    }
}
