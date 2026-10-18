# ChopShop Distributed Services

ChopShop is organised as independent services in one repository. Each service has its own Maven project, runs as a separate application, and owns its own PostgreSQL database.

| Service | Protocol | Application port | Database port |
| --- | --- | --- | --- |
| `TraceabilityService/` | gRPC | `9090` | `5433` |
| `RegistrationService/` | REST | `8081` | `5434` |

## Local databases

Docker Compose starts both PostgreSQL databases:

```bash
docker compose up -d
docker compose ps
```

| Database service | Database | User | Host port | Purpose |
| --- | --- | --- | --- | --- |
| `traceability-postgres` | `chopshop` | `chopshop` | `5433` | Traceability data: animals, trays, parts, and products |
| `registration-postgres` | `registration` | `registration` | `5434` | Animal registrations |

Initialise Traceability's schema and development data from the repository root:

```bash
docker compose exec -T traceability-postgres psql -U chopshop -d chopshop < database/schema.sql
docker compose exec -T traceability-postgres psql -U chopshop -d chopshop < database/test-data.sql
```

RegistrationService manages its own `animals` table. When the Spring Boot application starts, it connects to PostgreSQL on port `5434` and executes `RegistrationService/src/main/resources/schema.sql`.

Confirm that RegistrationService's schema exists:

```bash
docker compose exec registration-postgres \
  psql -U registration -d registration -c '\d animals'
```

## TraceabilityService

TraceabilityService implements the traceability-query slice of ChopShop. It returns the animals involved in a product and the products involving an animal.

Run its tests from its Maven project directory:

```bash
cd TraceabilityService
mvn test
```

`mvn test` uses Testcontainers, so Docker Desktop must be running.

To compile and start the gRPC server from the command line:

```bash
cd TraceabilityService
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" dk.dsy1.chopshop.server.TraceabilityServer
```

The gRPC server listens on port `9090`. In Kreya, call:

- `TraceabilityService/GetAnimalsForProduct` with a `product_id` UUID.
- `TraceabilityService/GetProductsForAnimal` with an `ear_tag`.

### Traceability configuration

TraceabilityService uses these local development defaults, which match `compose.yaml`:

```text
CHOPSHOP_DB_URL=jdbc:postgresql://127.0.0.1:5433/chopshop
CHOPSHOP_DB_USER=chopshop
CHOPSHOP_DB_PASSWORD=chopshop
```

Set any of these environment variables to override the defaults.

## RegistrationService

RegistrationService is a Spring Boot REST service for registering animals. Start `RegistrationServiceApplication` from IntelliJ; it listens on `http://localhost:8081` and creates its database schema on startup.

The first REST endpoint is planned and currently being implemented:

```text
POST /registrations
```

It will accept a JSON request body such as:

```json
{
  "earTag": "DK-A1001",
  "arrivalDate": "2026-10-18",
  "weight": 525.5,
  "origin": "Example Farm"
}
```

RegistrationService will generate a UUID for the registration and store it with the unique ear tag, arrival date, weight, and origin in its own database.
