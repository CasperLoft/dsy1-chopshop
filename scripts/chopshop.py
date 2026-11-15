#!/usr/bin/env python3
"""Run a local ChopShop demo; control traceability with trace-stop/trace-start."""
import fcntl
import hashlib
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parent.parent
KEY = hashlib.sha256(str(ROOT).encode()).hexdigest()[:12]
STATE = Path(tempfile.gettempdir()) / f"chopshop-{os.getuid()}-{KEY}"
SOCKET = STATE / "control.sock"
EXPECTED = {"animals", "trays", "animal_parts", "tray_parts", "products", "product_trays", "event_animals", "event_parts", "event_products", "event_product_trays"}
COLUMNS = {
    "animals": {"id", "ear_tag"}, "trays": {"id", "rfid"},
    "animal_parts": {"id", "animal_id"}, "tray_parts": {"tray_id", "animal_part_id"},
    "products": {"id", "product_type"}, "product_trays": {"product_id", "tray_id"},
    "event_animals": {"id", "ear_tag"}, "event_parts": {"id", "animal_id", "tray_rfid"},
    "event_products": {"id", "product_type"}, "event_product_trays": {"product_id", "tray_rfid"},
}
PORTS = {8000: "dashboard", 8081: "Registration", 8082: "Processing", 8083: "Packaging", 8084: "Traceability HTTP", 9090: "Traceability gRPC"}
DBS = ["registration-postgres", "processing-postgres", "packaging-postgres", "traceability-postgres"]
processes = {}
reported = set()


def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def prepare_state():
    STATE.mkdir(mode=0o700, exist_ok=True)
    if STATE.is_symlink() or STATE.stat().st_uid != os.getuid() or STATE.stat().st_mode & 0o077:
        raise RuntimeError(f"Unsafe launcher directory {STATE}; check its ownership and permissions.")


def check_ports(ports=PORTS):
    for port, name in ports.items():
        with socket.socket() as probe:
            try:
                probe.bind(("127.0.0.1", port))
            except OSError as e:
                raise RuntimeError(f"Port {port} ({name}) is already in use. Stop the existing application and retry.") from e


def wait_for_databases():
    deadline = time.monotonic() + 60
    pending = set(DBS)
    while pending and time.monotonic() < deadline:
        for service in list(pending):
            result = subprocess.run(["docker", "compose", "exec", "-T", service, "pg_isready"], cwd=ROOT,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0:
                pending.remove(service)
        if pending:
            time.sleep(1)
    if pending:
        raise RuntimeError("Databases did not become ready: " + ", ".join(sorted(pending)))


def initialize_traceability():
    command = ["docker", "compose", "exec", "-T", "traceability-postgres", "psql", "-U", "chopshop", "-d", "chopshop", "-v", "ON_ERROR_STOP=1"]
    result = run(command + ["-Atc", "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"], capture_output=True, text=True)
    existing = set(result.stdout.splitlines())
    if not existing:
        print("Initializing the empty Traceability database...", flush=True)
        with (ROOT / "database/schema.sql").open("rb") as schema:
            run(command + ["-1"], stdin=schema, stdout=subprocess.DEVNULL)
    elif not EXPECTED.issubset(existing):
        missing = ", ".join(sorted(EXPECTED - existing))
        raise RuntimeError(f"Traceability schema is incomplete (missing: {missing}). Review database/schema.sql and migrate it manually; no data was changed.")
    else:
        columns = run(command + ["-Atc", "SELECT table_name || '.' || column_name FROM information_schema.columns WHERE table_schema = 'public'"], capture_output=True, text=True)
        actual = set(columns.stdout.splitlines())
        missing_columns = {f"{table}.{column}" for table, names in COLUMNS.items() for column in names} - actual
        if missing_columns:
            raise RuntimeError("Traceability schema is incomplete (missing columns: " + ", ".join(sorted(missing_columns)) + "). Review database/schema.sql and migrate it manually; no data was changed.")
        print("Existing Traceability schema detected; preserving its data.", flush=True)


def start_process(name, args, cwd=ROOT):
    current = processes.get(name)
    if current and current.poll() is None:
        raise RuntimeError(f"{name} is already running under this launcher.")
    log_path = STATE / f"{name}.log"
    with log_path.open("ab") as log:
        log.write(f"\n--- {name} started {time.strftime('%Y-%m-%d %H:%M:%S')} ---\n".encode())
        log.flush()
        process = subprocess.Popen(args, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    processes[name] = process
    reported.discard(name)
    print(f"Started {name} (log: {log_path})", flush=True)


def stop_process(name):
    process = processes.get(name)
    if not process or process.poll() is not None:
        return False
    try:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)
    except ProcessLookupError:
        pass
    return True


def trace_start():
    current = processes.get("traceability")
    if current and current.poll() is None:
        raise RuntimeError("Traceability is already running.")
    check_ports({8084: "Traceability HTTP", 9090: "Traceability gRPC"})
    cp_file = ROOT / "TraceabilityService/cp.txt"
    if not cp_file.is_file():
        raise RuntimeError("Traceability classpath file is missing; restart the launcher.")
    cp = "target/classes:" + cp_file.read_text().strip()
    start_process("traceability", ["java", "-cp", cp, "dk.dsy1.chopshop.server.TraceabilityServer"], ROOT / "TraceabilityService")


def send_control(command):
    prepare_state()
    with socket.socket(socket.AF_UNIX) as client:
        client.settimeout(20)
        try:
            client.connect(str(SOCKET))
            client.sendall((command + "\n").encode())
            response = client.recv(4096).decode().strip()
        except OSError as e:
            raise RuntimeError("No launcher is running for this checkout.") from e
    if response.startswith("ERROR:"):
        raise RuntimeError(response[6:].strip())
    print(response)


def launch():
    for command in ("docker", "mvn", "java", "python3"):
        if not shutil.which(command):
            raise RuntimeError(f"Required command not found: {command}")
    prepare_state()
    lock = (STATE / "launcher.lock").open("w")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as e:
        lock.close()
        raise RuntimeError("A launcher is already running for this checkout.") from e
    listener = None
    try:
        check_ports()
        print("Starting Docker Compose...", flush=True)
        run(["docker", "compose", "up", "-d"])
        wait_for_databases()
        initialize_traceability()
        print("Compiling Traceability and preparing its classpath...", flush=True)
        build_log = STATE / "traceability-build.log"
        try:
            with build_log.open("wb") as log:
                run(["mvn", "-f", "TraceabilityService/pom.xml", "compile", "dependency:build-classpath", "-Dmdep.outputFile=cp.txt"], stdout=log, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Traceability build failed; see {build_log}") from error
        for name, path in (("registration", "RegistrationService"), ("processing", "ProcessingService"), ("packaging", "PackagingService")):
            start_process(name, ["mvn", "-f", f"{path}/pom.xml", "spring-boot:run"])
        trace_start()
        start_process("dashboard", [sys.executable, str(ROOT / "scripts/dashboard_server.py")])
        if SOCKET.exists():
            SOCKET.unlink()
        listener = socket.socket(socket.AF_UNIX)
        listener.bind(str(SOCKET))
        os.chmod(SOCKET, 0o600)
        listener.listen(2)
        listener.settimeout(1)
        print("\nOpen http://localhost:8000. Services may take a moment to start.")
        print("Traceability controls: python3 scripts/chopshop.py trace-stop | trace-start")
        print(f"Logs: {STATE}   |   Press Ctrl+C to stop launcher-owned processes.\n", flush=True)
        while True:
            for name, process in list(processes.items()):
                if process.poll() is not None and name not in reported:
                    print(f"{name} stopped (exit {process.returncode}); see {STATE / (name + '.log')}", flush=True)
                    reported.add(name)
            try:
                connection, _ = listener.accept()
            except socket.timeout:
                continue
            with connection:
                connection.settimeout(5)
                try:
                    command = connection.recv(128).decode().strip()
                    if command == "trace-stop":
                        answer = "Traceability stopped." if stop_process("traceability") else "Traceability is already stopped."
                        reported.add("traceability")
                    elif command == "trace-start":
                        trace_start()
                        answer = "Traceability starting; check the dashboard for readiness."
                    else:
                        raise RuntimeError("Unknown launcher command.")
                except (OSError, RuntimeError, UnicodeError) as e:
                    answer = "ERROR: " + str(e)
                connection.sendall((answer + "\n").encode())
    finally:
        if listener:
            listener.close()
            SOCKET.unlink(missing_ok=True)
        for name in reversed(list(processes)):
            if stop_process(name):
                print(f"Stopped {name}.", flush=True)
        lock.close()
        print("Docker Compose containers were left running.", flush=True)


if __name__ == "__main__":
    try:
        if len(sys.argv) == 1:
            launch()
        elif len(sys.argv) == 2 and sys.argv[1] in ("trace-stop", "trace-start"):
            send_control(sys.argv[1])
        else:
            raise RuntimeError("Usage: python3 scripts/chopshop.py [trace-stop|trace-start]")
    except KeyboardInterrupt:
        print("\nLauncher stopped.")
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"ChopShop: {error}", file=sys.stderr)
        sys.exit(1)
