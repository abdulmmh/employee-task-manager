package io.github.abdulmmh.etm.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {
    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement statement;
    @Mock private ResultSet result;
    @InjectMocks private HealthService service;

    @Test
    void successfulProbeUsesTimeoutAndReleasesPoolConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);

        assertThat(service.databaseAvailable()).isTrue();
        verify(statement).setQueryTimeout(3);
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void unavailableConnectionIsDownWithoutExposingFailure() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("private connection details"));
        assertThat(service.databaseAvailable()).isFalse();
    }

    @Test
    void queryFailureIsDownAndReleasesResources() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("private query details"));

        assertThat(service.databaseAvailable()).isFalse();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void missingProbeResultIsDown() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        assertThat(service.databaseAvailable()).isFalse();
        verify(result).close();
        verify(connection).close();
    }
}
