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

    @Container
    private static final org.testcontainers.containers.GenericContainer<?> RABBIT =
            new org.testcontainers.containers.GenericContainer<>("rabbitmq:4-management").withExposedPorts(5672)
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Server startup complete.*\\n", 1));

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
    void consumesRabbitEventsAndAnswersRecallQueries() throws Exception {
        String animal = UUID.randomUUID().toString(), part = UUID.randomUUID().toString(), product = UUID.randomUUID().toString();
        String tray = "rabbit-" + UUID.randomUUID(), earTag = "rabbit-" + UUID.randomUUID();
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(RABBIT.getHost()); factory.setPort(RABBIT.getMappedPort(5672));
        try (var connection = factory.newConnection(); var channel = connection.createChannel();
             var consumer = new dk.dsy1.chopshop.messaging.TraceabilityConsumer(() -> POSTGRES.createConnection(""), RABBIT.getHost(), RABBIT.getMappedPort(5672), "guest", "guest")) {
            channel.exchangeDeclare("chopshop.events", "direct", true);
            channel.queueDeclare("chopshop.traceability", true, false, false, null);
            channel.queueBind("chopshop.traceability", "chopshop.events", "traceability");
            consumer.start();
            String[] types = {"product.created", "part.registered", "animal.registered"};
            String[] bodies = {
                "{\"productId\":\"" + product + "\",\"productType\":\"cuts\",\"trayRfids\":[\"" + tray + "\"]}",
                "{\"partId\":\"" + part + "\",\"animalId\":\"" + animal + "\",\"trayRfid\":\"" + tray + "\"}",
                "{\"animalId\":\"" + animal + "\",\"earTag\":\"" + earTag + "\"}"};
            for (int i = 0; i < types.length; i++) {
                var props = new com.rabbitmq.client.AMQP.BasicProperties.Builder().type(types[i]).deliveryMode(2).build();
                channel.basicPublish("chopshop.events", "traceability", props, bodies[i].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            java.util.List<String> animals = java.util.List.of();
            while (System.nanoTime() < deadline) {
                try { animals = client.getAnimalsForProduct(ProductRequest.newBuilder().setProductId(product).build())
                        .getAnimalsList().stream().map(AnimalResponse::getEarTag).toList(); }
                catch (StatusRuntimeException ignored) { }
                if (animals.contains(earTag)) break;
                Thread.sleep(100);
            }
            assertIterableEquals(java.util.List.of(earTag), animals);
            assertIterableEquals(java.util.List.of(product), client.getProductsForAnimal(AnimalRequest.newBuilder().setEarTag(earTag).build())
                    .getProductsList().stream().map(ProductResponse::getProductId).toList());
        }
    }

    @Test
    void eventsAreIdempotentAndCanArriveOutOfOrder() throws Exception {
        var consumer = new dk.dsy1.chopshop.messaging.TraceabilityConsumer(() -> POSTGRES.createConnection(""));
        String animal = UUID.randomUUID().toString();
        String part = UUID.randomUUID().toString();
        String product = UUID.randomUUID().toString();
        String productEvent = "{\"productId\":\"" + product + "\",\"productType\":\"cuts\",\"trayRfids\":[\"event-tray\"]}";
        String partEvent = "{\"partId\":\"" + part + "\",\"animalId\":\"" + animal + "\",\"trayRfid\":\"event-tray\"}";
        String animalEvent = "{\"animalId\":\"" + animal + "\",\"earTag\":\"event-ear-tag\"}";
        consumer.apply("product.created", productEvent);
        consumer.apply("part.registered", partEvent);
        consumer.apply("animal.registered", animalEvent);
        consumer.apply("product.created", productEvent);
        consumer.apply("part.registered", partEvent);
        consumer.apply("animal.registered", animalEvent);
        assertIterableEquals(java.util.List.of("event-ear-tag"), client.getAnimalsForProduct(
                ProductRequest.newBuilder().setProductId(product).build()).getAnimalsList().stream().map(AnimalResponse::getEarTag).toList());
        assertIterableEquals(java.util.List.of(product), client.getProductsForAnimal(
                AnimalRequest.newBuilder().setEarTag("event-ear-tag").build()).getProductsList().stream().map(ProductResponse::getProductId).toList());
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
