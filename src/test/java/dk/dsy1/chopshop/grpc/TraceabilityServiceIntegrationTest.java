package dk.dsy1.chopshop.grpc;

import dk.dsy1.chopshop.config.ConnectionProvider;
import dk.dsy1.chopshop.repository.TraceabilityRepository;
import dk.dsy1.chopshop.service.TraceabilityQueryService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class TraceabilityServiceIntegrationTest {
    private static final String PRODUCT_ONE = "00000000-0000-0000-0000-000000000031";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("chopshop")
            .withUsername("chopshop")
            .withPassword("chopshop");

    private static Server server;
    private static ManagedChannel channel;
    private static TraceabilityServiceGrpc.TraceabilityServiceBlockingStub client;

    @BeforeAll
    static void startService() throws Exception {
        runSqlScript("schema.sql");
        runSqlScript("test-data.sql");

        ConnectionProvider connectionProvider = () -> POSTGRES.createConnection("");
        TraceabilityRepository repository = new TraceabilityRepository(connectionProvider);
        TraceabilityQueryService queryService = new TraceabilityQueryService(repository);
        server = ServerBuilder.forPort(0)
                .addService(new TraceabilityServiceImpl(queryService))
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
        client = TraceabilityServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stopService() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void productReturnsAllInvolvedAnimalEarTags() {
        AnimalsForProductResponse response = client.getAnimalsForProduct(ProductRequest.newBuilder()
                .setProductId(PRODUCT_ONE)
                .build());

        assertIterableEquals(
                java.util.List.of("DK-A1001", "DK-B2002"),
                response.getAnimalsList().stream().map(AnimalResponse::getEarTag).toList()
        );
    }

    @Test
    void animalReturnsAllInvolvedProducts() {
        ProductsForAnimalResponse response = client.getProductsForAnimal(AnimalRequest.newBuilder()
                .setEarTag("DK-A1001")
                .build());

        assertIterableEquals(
                java.util.List.of(
                        "00000000-0000-0000-0000-000000000031",
                        "00000000-0000-0000-0000-000000000032"
                ),
                response.getProductsList().stream().map(ProductResponse::getProductId).toList()
        );
    }

    @Test
    void unknownProductReturnsNotFound() {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                client.getAnimalsForProduct(ProductRequest.newBuilder()
                        .setProductId("00000000-0000-0000-0000-000000000099")
                        .build())
        );

        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void unknownAnimalReturnsNotFound() {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                client.getProductsForAnimal(AnimalRequest.newBuilder().setEarTag("DK-UNKNOWN").build())
        );

        assertEquals(Status.Code.NOT_FOUND, exception.getStatus().getCode());
    }

    @Test
    void malformedProductIdReturnsInvalidArgument() {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                client.getAnimalsForProduct(ProductRequest.newBuilder().setProductId("not-a-uuid").build())
        );

        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }

    @Test
    void blankEarTagReturnsInvalidArgument() {
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                client.getProductsForAnimal(AnimalRequest.newBuilder().setEarTag("   ").build())
        );

        assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
    }

    @Test
    void existingUnlinkedAnimalReturnsAnEmptyResponse() throws Exception {
        UUID animalId = UUID.randomUUID();
        try (Connection connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement("INSERT INTO animals (id, ear_tag) VALUES (?, ?)")) {
            statement.setObject(1, animalId);
            statement.setString(2, "DK-UNLINKED");
            statement.executeUpdate();
        }

        ProductsForAnimalResponse response = client.getProductsForAnimal(AnimalRequest.newBuilder()
                .setEarTag("DK-UNLINKED")
                .build());

        assertEquals(0, response.getProductsCount());
    }

    private static void runSqlScript(String resourceName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                TraceabilityServiceIntegrationTest.class.getClassLoader().getResourceAsStream(resourceName),
                StandardCharsets.UTF_8
        ))) {
            String script = reader.lines().reduce("", (left, right) -> left + "\n" + right);
            try (Connection connection = POSTGRES.createConnection(""); Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
        }
    }
}
