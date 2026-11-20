"""Hardware-free tests for Home Assistant BLE advertisement normalization."""
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
PACKAGE = "_watch_ble_test"
if PACKAGE not in sys.modules:
    pkg = types.ModuleType(PACKAGE)
    pkg.__path__ = [str(ROOT)]
    sys.modules[PACKAGE] = pkg

base = importlib.import_module(PACKAGE + ".base")
adapter = importlib.import_module(PACKAGE + ".ha_adapter")


class ExampleDriver(base.BleDriver):
    driver_id = "example"
    display_name = "Example fixture only"

    def matches(self, device):
        return "0000180d-0000-1000-8000-00805f9b34fb" in device.service_uuids

    def parse(self, payload):
        return []


class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.registry = base.DriverRegistry()
        self.registry.register(ExampleDriver())

    def test_normalize_advertisement(self):
        info = types.SimpleNamespace(
            address="AA:BB", name="Fixture", connectable=False,
            service_uuids=["0000180D-0000-1000-8000-00805F9B34FB"],
            manufacturer_data={123: b"data"},
        )
        identity = adapter.identity_from_service_info(info)
        self.assertEqual(identity.manufacturer_ids, (123,))
        self.assertEqual(identity.service_uuids[0], "0000180d-0000-1000-8000-00805f9b34fb")
        self.assertFalse(identity.connectable)

    def test_matches_and_deduplicates(self):
        info = types.SimpleNamespace(
            address="AA:BB", name="Fixture", connectable=True,
            service_uuids=["0000180D-0000-1000-8000-00805F9B34FB"],
            manufacturer_data={},
        )
        matches = adapter.find_candidates([info, info], self.registry)
        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0].driver_id, "example")

    def test_unknown_device_is_not_matched(self):
        info = types.SimpleNamespace(
            address="AA:BB", name="Unknown", connectable=True,
            service_uuids=[], manufacturer_data={},
        )
        self.assertEqual(adapter.find_candidates([info], self.registry), [])

    def test_no_drivers_means_no_matches(self):
        info = types.SimpleNamespace(
            address="AA:BB", name="Unknown", connectable=True,
            service_uuids=["0000180d-0000-1000-8000-00805f9b34fb"],
            manufacturer_data={},
        )
        self.assertEqual(adapter.find_candidates([info], base.DriverRegistry()), [])


if __name__ == "__main__":
    unittest.main()
