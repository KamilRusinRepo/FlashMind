package com.example.backend.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusControllerTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final StatusController controller = new StatusController(jdbcTemplate);

    @Test
    void returnsUpWhenDatabaseResponds() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        var response = controller.status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().status());
        assertEquals("UP", response.getBody().database());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void returnsServiceUnavailableWithoutConnectionDetailsWhenDatabaseFails() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("private connection details"));

        var response = controller.status();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().status());
        assertEquals("DOWN", response.getBody().database());
    }

    @Test
    void rejectsUnexpectedDatabaseResult() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.status().getStatusCode());
    }
}
