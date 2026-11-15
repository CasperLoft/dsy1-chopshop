package dk.dsy1.chopshop.registration.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** The caller writes the event in the same database transaction as its business data. */
@Component
public class Outbox {
    private final JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper();
    private final String host, username, password;
    private final int port;
    public Outbox(JdbcTemplate db,
            @org.springframework.beans.factory.annotation.Value("${RABBITMQ_HOST:localhost}") String host,
            @org.springframework.beans.factory.annotation.Value("${RABBITMQ_PORT:5672}") int port,
            @org.springframework.beans.factory.annotation.Value("${RABBITMQ_USER:guest}") String username,
            @org.springframework.beans.factory.annotation.Value("${RABBITMQ_PASSWORD:guest}") String password) {
        this.db = db; this.host = host; this.port = port; this.username = username; this.password = password;
    }

    public void add(String type, Map<String, ?> data) {
        try {
            db.update("INSERT INTO event_outbox(id, event_type, payload) VALUES (?, ?, ?)",
                    UUID.randomUUID(), type, json.writeValueAsString(data));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    @Scheduled(fixedDelayString = "${chopshop.outbox.delay:5000}", initialDelayString = "${chopshop.outbox.delay:5000}")
    public void publishPending() {
        var events = db.query("SELECT id, event_type, payload FROM event_outbox ORDER BY created_at LIMIT 100",
                (rs, n) -> new Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
        if (events.isEmpty()) return;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare("chopshop.events", "direct", true);
            channel.queueDeclare("chopshop.traceability", true, false, false, null);
            channel.queueBind("chopshop.traceability", "chopshop.events", "traceability");
            channel.confirmSelect();
            for (Event event : events) {
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().contentType("application/json")
                        .deliveryMode(2).type(event.type()).messageId(event.id().toString()).build();
                channel.basicPublish("chopshop.events", "traceability", props, event.payload().getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(5000);
                db.update("DELETE FROM event_outbox WHERE id = ?", event.id());
            }
        } catch (Exception e) {
            System.err.println("Outbox delivery will retry: " + e.getMessage());
        }
    }
    private record Event(UUID id, String type, String payload) {}
}
