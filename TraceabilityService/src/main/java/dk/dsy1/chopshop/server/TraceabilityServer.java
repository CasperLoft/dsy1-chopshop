package dk.dsy1.chopshop.server;

import dk.dsy1.chopshop.config.DatabaseConfig;
import dk.dsy1.chopshop.config.PostgresConnectionProvider;
import dk.dsy1.chopshop.grpc.TraceabilityServiceImpl;
import dk.dsy1.chopshop.repository.TraceabilityRepository;
import dk.dsy1.chopshop.service.TraceabilityQueryService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

/** Starts the local Traceability gRPC Service on port 9090. */
public final class TraceabilityServer {
    private static final int PORT = 9090;

    private TraceabilityServer() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        PostgresConnectionProvider connectionProvider = new PostgresConnectionProvider(config);
        TraceabilityRepository repository = new TraceabilityRepository(connectionProvider);
        TraceabilityQueryService queryService = new TraceabilityQueryService(repository);

        var http = new dk.dsy1.chopshop.http.TraceabilityHttpServer(queryService, 8084);
        var consumer = new dk.dsy1.chopshop.messaging.TraceabilityConsumer(connectionProvider);
        consumer.start();

        Server server = ServerBuilder.forPort(PORT)
                .addService(new TraceabilityServiceImpl(queryService))
                .build()
                .start();

        http.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { http.close(); consumer.close(); server.shutdown(); }));
        System.out.printf("Traceability gRPC Service started on port %d; dashboard HTTP on 127.0.0.1:8084%n", PORT);
        server.awaitTermination();
    }
}
