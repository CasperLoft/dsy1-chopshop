package dk.dsy1.chopshop.repository;

import dk.dsy1.chopshop.config.ConnectionProvider;
import dk.dsy1.chopshop.domain.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executes the JDBC queries that traverse product, tray, part, and animal data. */
public final class TraceabilityRepository {
    private static final String PRODUCT_EXISTS_SQL = "SELECT 1 FROM products WHERE id = ?";
    private static final String ANIMAL_EXISTS_SQL = "SELECT 1 FROM animals WHERE ear_tag = ?";

    private static final String ANIMALS_FOR_PRODUCT_SQL = """
            SELECT DISTINCT a.ear_tag
            FROM product_trays pt
            JOIN tray_parts tp ON tp.tray_id = pt.tray_id
            JOIN animal_parts ap ON ap.id = tp.animal_part_id
            JOIN animals a ON a.id = ap.animal_id
            WHERE pt.product_id = ?
            ORDER BY a.ear_tag
            """;

    private static final String PRODUCTS_FOR_ANIMAL_SQL = """
            SELECT DISTINCT p.id, p.product_type
            FROM animals a
            JOIN animal_parts ap ON ap.animal_id = a.id
            JOIN tray_parts tp ON tp.animal_part_id = ap.id
            JOIN product_trays pt ON pt.tray_id = tp.tray_id
            JOIN products p ON p.id = pt.product_id
            WHERE a.ear_tag = ?
            ORDER BY p.id
            """;

    private final ConnectionProvider connectionProvider;

    public TraceabilityRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public Optional<List<String>> findAnimalEarTagsForProduct(UUID productId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            if (!exists(connection, PRODUCT_EXISTS_SQL, productId)) {
                return Optional.empty();
            }

            List<String> earTags = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ANIMALS_FOR_PRODUCT_SQL)) {
                statement.setObject(1, productId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        earTags.add(resultSet.getString("ear_tag"));
                    }
                }
            }
            return Optional.of(earTags);
        }
    }

    public Optional<List<Product>> findProductsForAnimal(String earTag) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            if (!exists(connection, ANIMAL_EXISTS_SQL, earTag)) {
                return Optional.empty();
            }

            List<Product> products = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(PRODUCTS_FOR_ANIMAL_SQL)) {
                statement.setString(1, earTag);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        products.add(new Product(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("product_type")
                        ));
                    }
                }
            }
            return Optional.of(products);
        }
    }

    private boolean exists(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
