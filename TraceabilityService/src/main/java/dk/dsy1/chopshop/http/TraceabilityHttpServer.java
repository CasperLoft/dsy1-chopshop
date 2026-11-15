package dk.dsy1.chopshop.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dk.dsy1.chopshop.service.TraceabilityDataAccessException;
import dk.dsy1.chopshop.service.TraceabilityNotFoundException;
import dk.dsy1.chopshop.service.TraceabilityQueryService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Local browser interface backed by the same queries as the gRPC service. */
public final class TraceabilityHttpServer implements AutoCloseable {
    private static final String DASHBOARD_ORIGIN = "http://localhost:8000";
    private final HttpServer server;
    private final TraceabilityQueryService queries;
    private final ObjectMapper json = new ObjectMapper();

    public TraceabilityHttpServer(TraceabilityQueryService queries, int port) throws IOException {
        this.queries = queries;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/traceability/", this::handle);
        server.createContext("/health", this::handle);
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            exchange.getResponseHeaders().set("Vary", "Origin");
            if (origin != null && !DASHBOARD_ORIGIN.equals(origin)) {
                send(exchange, 403, Map.of("error", "Origin not allowed"));
                return;
            }
            if (origin != null) exchange.getResponseHeaders().set("Access-Control-Allow-Origin", DASHBOARD_ORIGIN);
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/health")) {
                    queries.checkDatabase();
                    send(exchange, 200, Map.of("status", "available"));
                } else if (path.equals("/traceability/products")) {
                    String earTag = parameter(exchange, "earTag");
                    send(exchange, 200, Map.of("products", queries.findProductsForAnimal(earTag)));
                } else if (path.equals("/traceability/animals")) {
                    String id = parameter(exchange, "productId");
                    UUID productId = UUID.fromString(id);
                    if (!productId.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Product ID must be a UUID");
                    send(exchange, 200, Map.of("animals", queries.findAnimalsForProduct(productId)));
                } else send(exchange, 404, Map.of("error", "Endpoint not found"));
            } catch (IllegalArgumentException e) {
                send(exchange, 400, Map.of("error", "Enter a valid identifier"));
            } catch (TraceabilityNotFoundException e) {
                send(exchange, 404, Map.of("error", e.getMessage()));
            } catch (TraceabilityDataAccessException e) {
                send(exchange, 503, Map.of("status", "degraded", "error", "Traceability data is unavailable"));
            }
        }
    }
    private static String parameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) {
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8).trim();
                if (!value.isEmpty()) return value;
            }
        }
        throw new IllegalArgumentException("Missing " + name);
    }
    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
