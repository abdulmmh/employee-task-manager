package io.github.abdulmmh.etm.health;

import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {
    private final DataSource dataSource;

    public boolean databaseAvailable() {
        // Reuse the application pool and its verified-TLS connection settings.
        // Connection acquisition uses the pool timeout; query timeout is separate.
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(3);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException exception) {
            // The public response must not reveal hostnames, credentials or SQL errors.
            return false;
        }
    }
}
