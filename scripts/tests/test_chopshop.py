import importlib.util
from pathlib import Path
import socket
import subprocess
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "chopshop.py"
spec = importlib.util.spec_from_file_location("chopshop_launcher", path)
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


def result(output=""):
    return subprocess.CompletedProcess([], 0, stdout=output)


class SchemaTests(unittest.TestCase):
    @patch.object(launcher, "run")
    def test_empty_database_applies_schema_in_one_transaction(self, run):
        run.side_effect = [result(), result()]
        launcher.initialize_traceability()
        self.assertIn("-1", run.call_args_list[1].args[0])
        self.assertEqual(2, run.call_count)

    @patch.object(launcher, "run")
    def test_complete_database_is_only_read(self, run):
        tables = "\n".join(launcher.EXPECTED)
        columns = "\n".join(f"{table}.{column}" for table, names in launcher.COLUMNS.items() for column in names)
        run.side_effect = [result(tables), result(columns)]
        launcher.initialize_traceability()
        self.assertEqual(2, run.call_count)
        self.assertTrue(all("-Atc" in call.args[0] for call in run.call_args_list))

    @patch.object(launcher, "run")
    def test_incomplete_schema_fails_without_writes(self, run):
        run.return_value = result("animals\n")
        with self.assertRaisesRegex(RuntimeError, "incomplete"):
            launcher.initialize_traceability()
        self.assertEqual(1, run.call_count)

    @patch.object(launcher, "run")
    def test_missing_column_fails_without_writes(self, run):
        run.side_effect = [result("\n".join(launcher.EXPECTED)), result("animals.id\n")]
        with self.assertRaisesRegex(RuntimeError, "missing columns"):
            launcher.initialize_traceability()
        self.assertEqual(2, run.call_count)

    def test_occupied_port_is_rejected(self):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen()
            with self.assertRaisesRegex(RuntimeError, "already in use"):
                launcher.check_ports({listener.getsockname()[1]: "test"})


if __name__ == "__main__":
    unittest.main()
