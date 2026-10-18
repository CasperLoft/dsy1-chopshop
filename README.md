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

RegistrationService manages its own `animals` table. When the Spring Boot application starts, it connects to PostgreSQL on port `5434`, executes `RegistrationService/src/main/resources/schema.sql`, and loads the sample registrations in `RegistrationService/src/main/resources/data.sql`.

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

RegistrationService is a Spring Boot REST service for registering animals. Start `RegistrationServiceApplication` from IntelliJ; it listens on `http://localhost:8081` and creates its database schema and seed data on startup.

### REST API

| Method | Endpoint | Result |
| --- | --- | --- |
| `POST` | `/registrations` | Registers an animal and returns `201 Created`. |
| `GET` | `/registrations/{earTag}` | Returns one registration, or `404 Not Found` when the ear tag is unknown. |
| `GET` | `/registrations?arrivalDate=YYYY-MM-DD` | Returns all registrations arriving on that date. |
| `GET` | `/registrations?origin=value` | Returns all registrations from that origin. |

Register an animal with `POST http://localhost:8081/registrations`:

```json
{
  "earTag": "DK-A1001",
  "arrivalDate": "2026-10-18",
  "weight": 525.5,
  "origin": "Example Farm"
}
```

RegistrationService generates a UUID and stores it with the unique ear tag, arrival date, weight, and origin. Submitting an existing ear tag returns `409 Conflict`.

Example lookup requests:

```text
GET http://localhost:8081/registrations/DK-SEED-1001
GET http://localhost:8081/registrations?arrivalDate=2026-10-18
GET http://localhost:8081/registrations?origin=Peperridge%20Farm
```

The committed seed registrations use `DK-SEED-...` ear tags and provide data for these lookups.

### Tests

Run the RegistrationService test suite from its Maven project directory:

```bash
cd RegistrationService
mvn test
```

The six integration tests start a temporary PostgreSQL container with Testcontainers. They verify registration, all three lookup routes, unknown ear tags, and duplicate ear tags without changing the local database on port `5434`.
