package com.college.events.controller;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test void databaseFailureStopsReadinessWithoutDisclosingConnectionDetails() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("private JDBC address and credentials"));
        var controller = new HealthController(source);
        var response = controller.readiness();
        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("status", "DOWN"), response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(Map.of("status", "UP"), controller.healthCheck().getBody());
    }

    @Test void readinessValidatesAndClosesTheDatabaseConnection() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var controller = new HealthController(source);
        when(connection.isValid(2)).thenReturn(false);
        assertEquals(503, controller.readiness().getStatusCode().value());
        when(connection.isValid(2)).thenReturn(true);
        assertEquals(200, controller.readiness().getStatusCode().value());
        verify(connection, times(2)).close();
    }
}
