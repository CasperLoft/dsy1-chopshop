package dk.dsy1.chopshop.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import dk.dsy1.chopshop.config.ConnectionProvider;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

/** Receives facts independently of their order, acknowledging only after commit. */
public final class TraceabilityConsumer implements AutoCloseable {
    private final ConnectionProvider database;
    private final ObjectMapper json = new ObjectMapper();
    private volatile boolean running = true;
    private volatile com.rabbitmq.client.Connection rabbit;
    private final Thread worker;
    private final String host, username, password;
    private final int port;

    public TraceabilityConsumer(ConnectionProvider database) {
        this(database, System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")),
                System.getenv().getOrDefault("RABBITMQ_USER", "guest"),
                System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest"));
    }
    public TraceabilityConsumer(ConnectionProvider database, String host, int port, String username, String password) {
        this.database = database;
        this.host = host; this.port = port; this.username = username; this.password = password;
        worker = new Thread(this::connectLoop, "traceability-rabbitmq");
        worker.setDaemon(true);
    }
    public void start() { worker.start(); }

    private void connectLoop() {
        while (running) {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(host);
                factory.setPort(port);
                factory.setUsername(username);
                factory.setPassword(password);
                factory.setAutomaticRecoveryEnabled(false);
                try (com.rabbitmq.client.Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
                    rabbit = connection;
                    channel.exchangeDeclare("chopshop.events", "direct", true);
                    channel.queueDeclare("chopshop.traceability", true, false, false, null);
                    channel.queueBind("chopshop.traceability", "chopshop.events", "traceability");
                    channel.basicQos(1);
                    channel.basicConsume("chopshop.traceability", false, (tag, delivery) -> {
                        try {
                            apply(delivery.getProperties().getType(), new String(delivery.getBody(), StandardCharsets.UTF_8));
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                        } catch (Exception e) {
                            System.err.println("Traceability event failed; retrying: " + e.getMessage());
                            // Closing the channel returns the unacknowledged message to the queue.
                            try { channel.close(); } catch (java.util.concurrent.TimeoutException ignored) { }
                        }
                    }, tag -> {});
                    while (running && connection.isOpen() && channel.isOpen()) Thread.sleep(1000);
                } finally { rabbit = null; }
            } catch (Exception e) {
                if (running) System.err.println("Traceability broker connection will retry: " + e.getMessage());
            }
            if (running) try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    public void apply(String type, String payload) throws Exception {
        JsonNode event = json.readTree(payload);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                switch (type) {
                    case "animal.registered" -> execute(connection,
                            "INSERT INTO event_animals(id, ear_tag) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                            uuid(event, "animalId"), text(event, "earTag"));
                    case "part.registered" -> execute(connection,
                            "INSERT INTO event_parts(id, animal_id, tray_rfid) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                            uuid(event, "partId"), uuid(event, "animalId"), text(event, "trayRfid"));
                    case "product.created" -> {
                        UUID id = uuid(event, "productId");
                        execute(connection, "INSERT INTO event_products(id, product_type) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", id, text(event, "productType"));
                        JsonNode trays = event.required("trayRfids");
                        if (!trays.isArray() || trays.isEmpty()) throw new IllegalArgumentException("trayRfids must be a nonempty array");
                        for (JsonNode tray : trays) {
                            if (!tray.isTextual() || tray.asText().isBlank()) throw new IllegalArgumentException("Invalid tray RFID");
                            execute(connection, "INSERT INTO event_product_trays(product_id, tray_rfid) VALUES (?, ?) ON CONFLICT DO NOTHING", id, tray.asText());
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown event type: " + type);
                }
                connection.commit();
            } catch (Exception e) { connection.rollback(); throw e; }
        }
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Invalid " + field);
        return value.asText();
    }
    private static UUID uuid(JsonNode node, String field) { return UUID.fromString(text(node, field)); }
    private static void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }
    @Override public void close() {
        running = false;
        worker.interrupt();
        try { if (rabbit != null) rabbit.close(); } catch (Exception ignored) { }
    }
}
