package dk.dsy1.chopshop.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class PostgresConnectionProvider implements ConnectionProvider {
    private final DatabaseConfig config;

    public PostgresConnectionProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
