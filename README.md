# ChopShop

ChopShop simulates three slaughterhouse stations: registration, processing, and packaging. The local dashboard lets you operate all three and observe their events in RabbitMQ. Each station has its own PostgreSQL database; a separate TraceabilityService answers recall queries over gRPC.

## Start the dashboard

**Requirements:** Docker Desktop, Java 21 or later, Maven, and Python 3. Start Docker Desktop, then run this command from the repository root:

```bash
python3 scripts/chopshop.py
```

The launcher starts the databases and RabbitMQ, checks the Traceability schema, and starts the four application services and dashboard. Open [http://localhost:8000](http://localhost:8000) and keep the launcher terminal open. The launcher prints the location of each service's log. The status panel shows whether the applications and their databases are available; RabbitMQ shows port reachability only. It may take a short while for all services to become available.

In **Station Flow**, register an animal, create a tray and add parts, then select trays to create a product. In **Traceability**, find products by ear tag or animals by product UUID. You can enter an identifier or select one created during the current session. Results may take a few seconds to appear while events are delivered.

Refreshing the page or choosing **New demo** clears the dashboard's session, not the database records. Suggested ear tags and tray RFIDs are unique for repeat demonstrations. Use `localhost`, rather than `127.0.0.1`, because the station APIs allow requests from that dashboard origin.

### Stop and restart Traceability alone

In a separate terminal from the repository root, run:

```bash
python3 scripts/chopshop.py trace-stop
python3 scripts/chopshop.py trace-start
```

These commands affect only TraceabilityService started by the launcher. While it is stopped, station events wait in RabbitMQ.

### Manual startup and troubleshooting

The launcher preserves existing databases. It initializes a completely empty Traceability database once. If existing Traceability tables or required columns are missing, it stops and names them; review `database/schema.sql` and migrate the existing database manually. Existing station records and seed registrations are not automatically sent to Traceability.

If a required application port is already in use, stop that application before running the launcher. It will not adopt or stop processes started elsewhere. To start services manually, run `docker compose up -d`, then start each of these in a separate terminal from the repository root:

```bash
mvn -f RegistrationService/pom.xml spring-boot:run
mvn -f ProcessingService/pom.xml spring-boot:run
mvn -f PackagingService/pom.xml spring-boot:run
```

For a **fresh** Traceability database, initialize its schema once before starting Traceability:

```bash
docker compose exec -T traceability-postgres psql -U chopshop -d chopshop < database/schema.sql
```

In another terminal, start Traceability:

```bash
cd TraceabilityService
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" dk.dsy1.chopshop.server.TraceabilityServer
```

Start the dashboard in another terminal from the repository root:

```bash
python3 scripts/dashboard_server.py
```

## Demonstrate RabbitMQ and recovery

Open [RabbitMQ management](http://localhost:15672) and sign in with `guest` / `guest`. The dashboard also links to it. Events use the `chopshop.events` exchange and `chopshop.traceability` queue. Traceability normally consumes messages quickly; use the Traceability stop command above to show messages waiting in the queue, then restart it with the corresponding start command.

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

In Kreya, connect to `localhost:9090` and call `GetAnimalsForProduct` with a product UUID or `GetProductsForAnimal` with an ear tag. See the [Kreya instructions](docs/Kreya/readme.md) and [container diagram](docs/assignments/assignment-04/C2_container_diagram_V4.svg).

Station operations and their outgoing events are saved together in an outbox. RabbitMQ delivers persistent messages, and Traceability handles duplicate or out-of-order deliveries. Results are **eventually consistent**: a new record may briefly be missing or incomplete while events are in transit. Recall is based on source trays, so it can include animals whose parts were in a tray but were not used in a particular product. Stations accept animal IDs and tray RFIDs without checking other services synchronously. Packaging records a product type and source tray references; it does not track quantities or verify that a “half animal” includes all expected parts.

## Tests and shutdown

With Docker running, run the integration tests from the repository root:

```bash
mvn -f RegistrationService/pom.xml test
mvn -f ProcessingService/pom.xml test
mvn -f PackagingService/pom.xml test
mvn -f TraceabilityService/pom.xml test
python3 -m unittest discover -s scripts/tests -v
```

The tests use temporary PostgreSQL and RabbitMQ containers. To finish a demonstration, press `Ctrl+C` in the launcher terminal. It stops its application and dashboard processes but leaves Docker containers running. To stop those containers as well, run:

```bash
docker compose down
```

This preserves the database and RabbitMQ volumes for the next run.
