package dk.dsy1.chopshop.processing;

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
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "chopshop.outbox.delay=3600000")
@AutoConfigureMockMvc
@Testcontainers
class ProcessingServiceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("processing").withUsername("processing").withPassword("processing");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final UUID animal = UUID.fromString("377805d4-df37-4b24-aa1f-cf1e39927fc5");

    @BeforeEach void clear() { jdbc.update("DELETE FROM event_outbox"); jdbc.update("DELETE FROM animal_parts"); jdbc.update("DELETE FROM trays"); }
    private String part(String type, String weight, String tray) {
        return "{\"animalId\":\"" + animal + "\",\"partType\":\"" + type + "\",\"weight\":" + weight + ",\"trayRfid\":\"" + tray + "\"}";
    }
    private void tray(String rfid, int capacity) throws Exception {
        mvc.perform(post("/trays").contentType(APPLICATION_JSON).content("{\"rfid\":\"" + rfid + "\",\"maxWeight\":" + capacity + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty());
    }
    private int add(String content) throws Exception {
        return mvc.perform(post("/parts").contentType(APPLICATION_JSON).content(content)).andReturn().getResponse().getStatus();
    }
    @Test
    void dashboardOriginIsAllowedButOtherOriginsAreRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/parts")
                .header("Origin", "http://localhost:8000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "http://localhost:8000"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/parts")
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void createsAndReadsPersistedTrayAndParts() throws Exception {
        tray("tray-1", 10);
        mvc.perform(post("/parts").contentType(APPLICATION_JSON).content(part("leg", "3.25", "tray-1")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.animalId").value(animal.toString())).andExpect(jsonPath("$.trayRfid").value("tray-1"));
        mvc.perform(get("/trays/tray-1")).andExpect(status().isOk()).andExpect(jsonPath("$.partType").value("leg"))
                .andExpect(jsonPath("$.maxWeight").value(10)).andExpect(jsonPath("$.currentWeight").value(3.25))
                .andExpect(jsonPath("$.parts.length()").value(1)).andExpect(jsonPath("$.parts[0].animalId").value(animal.toString()));
        assertEquals(animal, jdbc.queryForObject("SELECT animal_id FROM animal_parts", UUID.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE event_type = ? AND payload::jsonb ->> 'trayRfid' = ?", Integer.class, "part.registered", "tray-1"));
    }
    @Test void enforcesTypeCapacityAndAllowsExactCapacity() throws Exception {
        tray("tray-2", 10);
        assertEquals(201, add(part("leg", "6.5", "tray-2")));
        assertEquals(409, add(part("wing", "1", "tray-2")));
        assertEquals(409, add(part("leg", "3.51", "tray-2")));
        assertEquals(201, add(part("leg", "3.5", "tray-2")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class));
        mvc.perform(get("/trays/tray-2")).andExpect(jsonPath("$.currentWeight").value(10)).andExpect(jsonPath("$.parts.length()").value(2));
    }
    @Test void unknownAndDuplicateTrays() throws Exception {
        mvc.perform(get("/trays/missing")).andExpect(status().isNotFound());
        assertEquals(404, add(part("leg", "1", "missing")));
        tray("same", 10);
        mvc.perform(post("/trays").contentType(APPLICATION_JSON).content("{\"rfid\":\"same\",\"maxWeight\":10}"))
                .andExpect(status().isConflict());
    }
    @Test void rejectsInvalidRequests() throws Exception {
        for (String body : new String[]{"{}", "{\"rfid\":\" \",\"maxWeight\":5}", "{\"rfid\":\"x\",\"maxWeight\":0}", "{\"rfid\":\"x\",\"maxWeight\":-1}"})
            mvc.perform(post("/trays").contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        for (String body : new String[]{"{}", part("leg", "0", "x"), part(" ", "1", "x"), part("leg", "-1", "x"), "{\"animalId\":\"invalid\",\"partType\":\"leg\",\"weight\":1,\"trayRfid\":\"x\"}"})
            assertEquals(400, add(body));
    }
    @Test void concurrentPartsCannotOverfillTray() throws Exception {
        tray("concurrent", 10);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> { ready.countDown(); start.await(); return add(part("leg", "6", "concurrent")); };
            Future<Integer> first = executor.submit(task), second = executor.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            mvc.perform(get("/trays/concurrent")).andExpect(jsonPath("$.currentWeight").value(6)).andExpect(jsonPath("$.parts.length()").value(1));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
}
