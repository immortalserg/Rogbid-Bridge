"""Hardware-free contract tests; run with python -m unittest discover -s tests."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
spec = importlib.util.spec_from_file_location("watch_ble_base", ROOT / "base.py")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

BleIdentity = module.BleIdentity
Measurement = module.Measurement
BleDriver = module.BleDriver
DriverRegistry = module.DriverRegistry


class FakeDriver(BleDriver):
    driver_id = "fake"
    display_name = "Fake watch (tests only)"

    def matches(self, device):
        return "0000180d-0000-1000-8000-00805f9b34fb" in (
            uuid.lower() for uuid in device.service_uuids
        )

    def parse(self, payload):
        if len(payload) != 1:
            raise module.DriverError("Invalid fixture")
        return [Measurement("heart", "2026-11-15T12:00:00+00:00", "fake:1", {"heart_rate": payload[0]})]


class DriverTests(unittest.TestCase):
    def test_registry_matches_only_advertised_uuid(self):
        registry = DriverRegistry()
        registry.register(FakeDriver())
        self.assertEqual(len(registry.match(BleIdentity("AA", service_uuids=("0000180D-0000-1000-8000-00805F9B34FB",)))), 1)
        self.assertEqual(registry.match(BleIdentity("BB", name="Unknown")), [])

    def test_duplicate_rejected(self):
        registry = DriverRegistry()
        registry.register(FakeDriver())
        with self.assertRaises(ValueError):
            registry.register(FakeDriver())

    def test_parse_fixture(self):
        reading, = FakeDriver().parse(bytes([72]))
        self.assertEqual(reading.values["heart_rate"], 72)

    def test_invalid_timestamp_rejected(self):
        with self.assertRaises(ValueError):
            Measurement("heart", "not-a-date", "id", {"heart_rate": 72})

    def test_unsupported_kind_rejected(self):
        with self.assertRaises(ValueError):
            Measurement("temperature", "2026-11-15T12:00:00Z", "id", {})


if __name__ == "__main__":
    unittest.main()
