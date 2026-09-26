package dk.dsy1.chopshop.config;

/**
 * Database settings for the local traceability service.
 * Environment variables override the Docker Compose development defaults.
 */
public record DatabaseConfig(String url, String user, String password) {
    private static final String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:5433/chopshop";
    private static final String DEFAULT_USER = "chopshop";
    private static final String DEFAULT_PASSWORD = "chopshop";

    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(
                valueOrDefault("CHOPSHOP_DB_URL", DEFAULT_URL),
                valueOrDefault("CHOPSHOP_DB_USER", DEFAULT_USER),
                valueOrDefault("CHOPSHOP_DB_PASSWORD", DEFAULT_PASSWORD)
        );
    }

    private static String valueOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
