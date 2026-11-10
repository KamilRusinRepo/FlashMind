dpackage com.example.backend.status;

import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final JdbcTemplate jdbcTemplate;

    public StatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/status")
    public ResponseEntity<StatusResponse> status() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return ResponseEntity.ok(new StatusResponse("UP", "UP", Instant.now()));
            }
        } catch (DataAccessException exception) {
            // Do not expose database connection details in the public response.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new StatusResponse("DOWN", "DOWN", Instant.now()));
    }

    public record StatusResponse(String status, String database, Instant timestamp) {
    }
}

