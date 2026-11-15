# ChopShop Distributed Services

ChopShop is organised as independent services in one repository. Each service has its own Maven project, runs as a separate application, and owns its own PostgreSQL database.

| Service | Protocol | Application port | Database port |
| --- | --- | --- | --- |
| `TraceabilityService/` | gRPC | `9090` | `5433` |
| `RegistrationService/` | REST | `8081` | `5434` |
| `ProcessingService/` | REST | `8082` | `5435` |
| `PackagingService/` | REST | `8083` | `5436` |

## Local databases

Docker Compose starts the PostgreSQL databases:

```bash
docker compose up -d
docker compose ps
```

| Database service | Database | User | Host port | Purpose |
| --- | --- | --- | --- | --- |
| `traceability-postgres` | `chopshop` | `chopshop` | `5433` | Traceability data: animals, trays, parts, and products |
| `registration-postgres` | `registration` | `registration` | `5434` | Animal registrations |
| `processing-postgres` | `processing` | `processing` | `5435` | Trays and animal parts |
| `packaging-postgres` | `packaging` | `packaging` | `5436` | Products and source tray references |

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


## ProcessingService (station 2)

Start the databases with `docker compose up -d`, then start `ProcessingServiceApplication` from IntelliJ or run `./mvnw spring-boot:run` from `ProcessingService/`. The service initializes its own schema and listens on `http://localhost:8082`.

| Method | Endpoint | Result |
| --- | --- | --- |
| `POST` | `/trays` | Creates a tray and returns `201 Created`. |
| `GET` | `/trays/{rfid}` | Returns the tray, its current weight, type and parts, or `404` if absent. |
| `POST` | `/parts` | Registers a part in a tray and returns `201 Created`. |

Create a tray:

```json
{"rfid":"TRAY-001","maxWeight":10.0}
```

Then register a part with the UUID returned by animal registration:

```json
{"animalId":"377805d4-df37-4b24-aa1f-cf1e39927fc5","partType":"leg","weight":3.25,"trayRfid":"TRAY-001"}
```

The first part sets the tray's type. A different type, an over-capacity addition, or a duplicate RFID returns `409 Conflict`; missing values and nonpositive weights return `400 Bad Request`. The service uses decimal weights and serializes concurrent additions to a tray. Animal IDs are external references: the service does not validate them against registration or synchronize parts to traceability yet, so station 2 can keep working independently of those services.

Run `./mvnw test` in `ProcessingService/` with Docker running. The PostgreSQL integration tests cover registration, lookup, validation, tray rules and concurrent additions. See the [station 2 component diagram](docs/assignments/assignment-03_5/C3_component_diagram_processingService_V1.svg) for the current architecture.


## PackagingService (station 3)

Start the databases with `docker compose up -d`, then start `PackagingServiceApplication` from IntelliJ or run `./mvnw spring-boot:run` from `PackagingService/`. The service initializes its own schema and listens on `http://localhost:8083`.

| Method | Endpoint | Result |
| --- | --- | --- |
| `POST` | `/products` | Creates a product and returns `201 Created` with its UUID and source trays. |
| `GET` | `/products/{id}` | Returns the product and source trays, or `404` if absent. |

Register a product with `POST http://localhost:8083/products`:

```json
{"productType":"half animal","trayRfids":["TRAY-001","TRAY-002"]}
```

A nonblank product type and at least one distinct, nonblank tray RFID are required; values may contain at most 100 characters. Invalid requests return `400 Bad Request`. The response returns tray RFIDs in sorted order. Multiple products can reference the same tray.

Tray RFIDs are external references supplied by the operator. Packaging does not check them against ProcessingService, track quantities, or synchronize products to TraceabilityService yet. After synchronization is implemented, tray-level recall may include animals whose parts were in a source tray but were not actually used in a specific product.

Run `./mvnw test` in `PackagingService/` with Docker running. The PostgreSQL integration tests cover creation, persistence, lookup, validation, and reuse of tray references. See the [station 3 component diagram](docs/assignments/assignment-03_5/C3_component_diagram_packagingService_V1.svg).
