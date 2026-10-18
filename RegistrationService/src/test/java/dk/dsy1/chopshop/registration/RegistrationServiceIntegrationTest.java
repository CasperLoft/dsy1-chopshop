package dk.dsy1.chopshop.registration;

import dk.dsy1.chopshop.registration.dto.RegistrationRequest;
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

import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegistrationServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("registration")
            .withUsername("registration")
            .withPassword("registration");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearRegistrations() {
        jdbcTemplate.update("DELETE FROM animals");
    }

    @Test
    void postRegistrationCreatesAndPersistsAnAnimal() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "DK-POST-001", LocalDate.of(2026, 10, 18), 525.5, "Peperridge Farm"
        );

        mockMvc.perform(post("/registrations")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.earTag").value("DK-POST-001"))
                .andExpect(jsonPath("$.arrivalDate").value("2026-10-18"))
                .andExpect(jsonPath("$.weight").value(525.5))
                .andExpect(jsonPath("$.origin").value("Peperridge Farm"));

        var savedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM animals WHERE ear_tag = ?", Integer.class, "DK-POST-001"
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, savedCount);
    }

    @Test
    void getByEarTagReturnsTheRegistration() throws Exception {
        register("DK-GET-001", LocalDate.of(2026, 10, 18), 510.0, "Oak Meadow Farm");

        mockMvc.perform(get("/registrations/DK-GET-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earTag").value("DK-GET-001"))
                .andExpect(jsonPath("$.arrivalDate").value("2026-10-18"))
                .andExpect(jsonPath("$.weight").value(510.0))
                .andExpect(jsonPath("$.origin").value("Oak Meadow Farm"));
    }

    @Test
    void getByArrivalDateReturnsOnlyMatchingRegistrationsInEarTagOrder() throws Exception {
        register("DK-DATE-002", LocalDate.of(2026, 10, 18), 510.0, "Oak Meadow Farm");
        register("DK-DATE-001", LocalDate.of(2026, 10, 18), 520.0, "Riverbend Farm");
        register("DK-DATE-003", LocalDate.of(2026, 10, 19), 530.0, "Oak Meadow Farm");

        mockMvc.perform(get("/registrations").param("arrivalDate", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].earTag").value("DK-DATE-001"))
                .andExpect(jsonPath("$[1].earTag").value("DK-DATE-002"));
    }

    @Test
    void getByOriginReturnsOnlyMatchingRegistrationsInEarTagOrder() throws Exception {
        register("DK-ORIGIN-002", LocalDate.of(2026, 10, 18), 510.0, "Green Valley Farm");
        register("DK-ORIGIN-001", LocalDate.of(2026, 10, 19), 520.0, "Green Valley Farm");
        register("DK-ORIGIN-003", LocalDate.of(2026, 10, 19), 530.0, "Oak Meadow Farm");

        mockMvc.perform(get("/registrations").param("origin", "Green Valley Farm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].earTag").value("DK-ORIGIN-001"))
                .andExpect(jsonPath("$[1].earTag").value("DK-ORIGIN-002"));
    }

    @Test
    void unknownEarTagReturnsNotFound() throws Exception {
        mockMvc.perform(get("/registrations/DK-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateEarTagReturnsConflict() throws Exception {
        register("DK-DUPLICATE-001", LocalDate.of(2026, 10, 18), 525.5, "Peperridge Farm");

        mockMvc.perform(post("/registrations")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest(
                                "DK-DUPLICATE-001", LocalDate.of(2026, 10, 19), 530.0, "Oak Meadow Farm"
                        ))))
                .andExpect(status().isConflict());
    }

    private void register(String earTag, LocalDate arrivalDate, double weight, String origin) throws Exception {
        mockMvc.perform(post("/registrations")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest(
                                earTag, arrivalDate, weight, origin
                        ))))
                .andExpect(status().isCreated());
    }
}
