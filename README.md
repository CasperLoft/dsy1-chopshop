# ChopShop Traceability gRPC Service

Part 2 implements the traceability-query slice of the ChopShop system. It returns the animals involved in a product and the products involving an animal. Station applications, asynchronous synchronization, and recall publication are documented future architecture and are not implemented here.

## Start PostgreSQL

```bash
docker compose up -d
docker compose exec -T postgres psql -U chopshop -d chopshop < database/schema.sql
docker compose exec -T postgres psql -U chopshop -d chopshop < database/test-data.sql
```

The local database is available on `127.0.0.1:5433`.

## Run and test

```bash
mvn test
mvn compile
java -cp "target/classes:$(cat cp.txt)" dk.dsy1.chopshop.server.TraceabilityServer
```

`mvn test` uses Testcontainers and therefore requires Docker Desktop to be running.

To create the runtime classpath file before the final command:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
```

The server listens on port `9090`. In Kreya, call `TraceabilityService/GetAnimalsForProduct` with a `product_id` UUID, or `TraceabilityService/GetProductsForAnimal` with an `ear_tag`.

## Configuration

The server uses these development defaults, which match `compose.yaml`:

```text
CHOPSHOP_DB_URL=jdbc:postgresql://127.0.0.1:5433/chopshop
CHOPSHOP_DB_USER=chopshop
CHOPSHOP_DB_PASSWORD=chopshop
```

Set any of these environment variables to override the local defaults.
