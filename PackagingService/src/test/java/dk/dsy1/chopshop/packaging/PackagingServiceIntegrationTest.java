package dk.dsy1.chopshop.packaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "chopshop.outbox.delay=3600000")
@AutoConfigureMockMvc
@Testcontainers
class PackagingServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("packaging").withUsername("packaging").withPassword("packaging");

    @Container
    static final org.testcontainers.containers.GenericContainer<?> RABBIT =
            new org.testcontainers.containers.GenericContainer<>("rabbitmq:4-management").withExposedPorts(5672)
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM product_trays");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void createsAndRetrievesProductWithMultipleTrays() throws Exception {
        String response = mvc.perform(post("/products").contentType(APPLICATION_JSON)
                        .content("{\"productType\":\"half animal\",\"trayRfids\":[\"TRAY-B\",\"TRAY-A\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.productType").value("half animal"))
                .andExpect(jsonPath("$.trayRfids[0]").value("TRAY-A"))
                .andExpect(jsonPath("$.trayRfids[1]").value("TRAY-B"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(get("/products/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.productType").value("half animal"))
                .andExpect(jsonPath("$.trayRfids[0]").value("TRAY-A"))
                .andExpect(jsonPath("$.trayRfids[1]").value("TRAY-B"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM product_trays WHERE product_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_type = ? AND payload::jsonb ->> 'productId' = ?", Integer.class, "product.created", id.toString()));
    }

    @Test
    void outboxRetriesAfterBrokerOutageAndPublishesPersistentMessage() throws Exception {
        mvc.perform(post("/products").contentType(APPLICATION_JSON)
                .content("{\"productType\":\"legs\",\"trayRfids\":[\"retry-tray\"]}"))
                .andExpect(status().isCreated());
        var unavailable = new dk.dsy1.chopshop.packaging.messaging.Outbox(jdbc, "127.0.0.1", 1, "guest", "guest");
        unavailable.publishPending();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class));
        var available = new dk.dsy1.chopshop.packaging.messaging.Outbox(jdbc, RABBIT.getHost(), RABBIT.getMappedPort(5672), "guest", "guest");
        available.publishPending();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class));
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(RABBIT.getHost()); factory.setPort(RABBIT.getMappedPort(5672));
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            var message = channel.basicGet("chopshop.traceability", false);
            assertNotNull(message);
            assertEquals("product.created", message.getProps().getType());
            assertEquals(2, message.getProps().getDeliveryMode());
            assertTrue(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("retry-tray"));
            channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
        }
    }

    @Test
    void permitsTrayInMultipleProducts() throws Exception {
        String body = "{\"productType\":\"legs\",\"trayRfids\":[\"TRAY-A\"]}";
        mvc.perform(post("/products").contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/products").contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM product_trays WHERE tray_rfid = ?", Integer.class, "TRAY-A"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM product_trays", Integer.class));
    }

    @Test
    void returns404ForUnknownProductAnd400ForMalformedId() throws Exception {
        mvc.perform(get("/products/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/products/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidProductWithoutPersistingAnything() throws Exception {
        String[] invalid = {
                "{}", "{\"productType\":\" \",\"trayRfids\":[\"A\"]}",
                "{\"productType\":\"x\"}", "{\"productType\":\"x\",\"trayRfids\":[]}",
                "{\"productType\":\"x\",\"trayRfids\":[\" \"]}",
                "{\"productType\":\"x\",\"trayRfids\":[null]}",
                "{\"productType\":\"x\",\"trayRfids\":[\"A\",\"A\"]}",
                "{\"productType\":\"" + "x".repeat(101) + "\",\"trayRfids\":[\"A\"]}",
                "{\"productType\":\"x\",\"trayRfids\":[\"" + "x".repeat(101) + "\"]}"
        };
        for (String body : invalid) {
            mvc.perform(post("/products").contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM product_trays", Integer.class));
    }
}
