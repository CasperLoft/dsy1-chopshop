# ChopShop

ChopShop simulates three slaughterhouse stations: registration, processing, and packaging. The local dashboard lets you operate all three and observe their events in RabbitMQ. Each station has its own PostgreSQL database; a separate TraceabilityService answers recall queries over gRPC.

## Start the dashboard

**Requirements:** Docker Desktop, Java 21 or later, Maven, and Python 3. Run the commands below from the repository root. Leave each service command running in its own terminal.

### 1. Start the databases and RabbitMQ

```bash
docker compose up -d
docker compose ps
```

The first time you use a fresh Traceability database, initialize its schema:

```bash
docker compose exec -T traceability-postgres psql -U chopshop -d chopshop < database/schema.sql
```

Run this schema command **only once per fresh database**. The station services create their own tables when they start. If you already initialized Traceability using an older schema, add only the four `event_*` table definitions at the end of `database/schema.sql`; the full schema is not a migration. Existing station records and seed registrations are not automatically sent to Traceability.

### 2. Start the three station services

Open a separate terminal for each command, all from the repository root:

```bash
mvn -f RegistrationService/pom.xml spring-boot:run
```

```bash
mvn -f ProcessingService/pom.xml spring-boot:run
```

```bash
mvn -f PackagingService/pom.xml spring-boot:run
```

Wait for each application to finish starting. Alternatively, run its `*ServiceApplication` class from IntelliJ.

### 3. Start TraceabilityService

In another terminal, from the repository root:

```bash
cd TraceabilityService
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" dk.dsy1.chopshop.server.TraceabilityServer
```

Traceability consumes RabbitMQ events and provides recall queries. The dashboard's station forms can still be used while Traceability is stopped; messages then wait in RabbitMQ.

### 4. Open the dashboard

In another terminal, from the repository root:

```bash
python3 -m http.server 8000 --bind 127.0.0.1 --directory dashboard
```

Open [http://localhost:8000](http://localhost:8000). Use `localhost`, rather than `127.0.0.1`, because the station APIs allow requests from this dashboard origin.

1. **Registration:** register one or more animals.
2. **Processing:** create a tray and add parts, selecting the animals and tray from the dashboard. Each tray accepts one part type up to its capacity.
3. **Packaging:** select one or more trays and create a product.

Open the **Traceability** tab to find products by ear tag or animals by product UUID. Enter an identifier or select one created in the current session. TraceabilityService must be running; its local dashboard interface uses port `8084` alongside gRPC on `9090`. Results can take a few seconds to appear.

The dashboard shows IDs and carries them between stations. Its lists last only for the current page session: refreshing or choosing **New demo** clears the view, not the records in the databases. Suggested ear tags and tray RFIDs are unique for repeat demonstrations.

## Demonstrate RabbitMQ and recovery

Open [RabbitMQ management](http://localhost:15672) and sign in with `guest` / `guest`. The dashboard also links to it. Events use the `chopshop.events` exchange and `chopshop.traceability` queue. Traceability normally consumes messages quickly; stop its terminal process temporarily if you want to show messages waiting in the queue, then restart it using the command above.

To demonstrate a broker outage, run the following from the repository root:

```bash
docker compose stop rabbitmq
```

Continue registering animals, adding parts, or creating products in the dashboard. Each running station saves its work and pending events in its local database. Restart the broker:

```bash
docker compose start rabbitmq
```

The stations retry delivery, normally within five seconds. Traceability consumes the events when it is running. A station needs its own database to remain available, and a stopped station cannot accept its own work.

## Traceability and system behavior

In Kreya, connect to `localhost:9090` and call `GetAnimalsForProduct` with a product UUID or `GetProductsForAnimal` with an ear tag. See the [Kreya instructions](docs/Kreya/readme.md) and [container diagram](docs/assignments/assignment-04/container-diagram.puml).

Station operations and their outgoing events are saved together in an outbox. RabbitMQ delivers persistent messages, and Traceability handles duplicate or out-of-order deliveries. Results are **eventually consistent**: a new record may briefly be missing or incomplete while events are in transit. Recall is based on source trays, so it can include animals whose parts were in a tray but were not used in a particular product. Stations accept animal IDs and tray RFIDs without checking other services synchronously.

## Tests and shutdown

With Docker running, run the integration tests from the repository root:

```bash
mvn -f RegistrationService/pom.xml test
mvn -f ProcessingService/pom.xml test
mvn -f PackagingService/pom.xml test
mvn -f TraceabilityService/pom.xml test
```

The tests use temporary PostgreSQL and RabbitMQ containers. To finish a demonstration, press `Ctrl+C` in the dashboard and service terminals, then stop the Compose containers:

```bash
docker compose down
```

This preserves the database and RabbitMQ volumes for the next run.
