package dk.dsy1.chopshop.registration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test void unavailableDatabaseIsReportedAsDegraded() {
        var database = mock(JdbcTemplate.class);
        when(database.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("unavailable"));
        var response = new HealthController(database).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("degraded", response.getBody().get("status"));
    }
}
