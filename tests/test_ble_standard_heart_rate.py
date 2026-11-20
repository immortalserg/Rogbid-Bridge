"""Hardware-free tests for the first experimental driver."""
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
PACKAGE = "_watch_ble_sig_driver_test"
pkg = types.ModuleType(PACKAGE)
pkg.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = pkg

base = importlib.import_module(PACKAGE + ".base")
driver_mod = importlib.import_module(PACKAGE + ".standard_heart_rate")


class StandardHeartRateTests(unittest.TestCase):
    def setUp(self):
        self.driver = driver_mod.StandardHeartRateDriver()

    def test_connectable_advertised_service_matches(self):
        device = base.BleIdentity(
            address="AA:BB",
            service_uuids=("0000180D-0000-1000-8000-00805F9B34FB",),
            connectable=True,
        )
        self.assertTrue(self.driver.matches(device))

    def test_passive_and_unrelated_devices_do_not_match(self):
        for device in (
            base.BleIdentity("AA", service_uuids=(driver_mod.HEART_RATE_SERVICE_UUID,)),
            base.BleIdentity("BB", connectable=True),
        ):
            self.assertFalse(self.driver.matches(device))

    def test_decoding_yields_unique_records(self):
        first, = self.driver.parse(bytes([0, 72]))
        second, = self.driver.parse(bytes([0, 73]))
        self.assertEqual(first.values["heart_rate"], 72)
        self.assertEqual(second.values["heart_rate"], 73)
        self.assertNotEqual(first.record_id, second.record_id)

    def test_malformed_notification_rejected(self):
        with self.assertRaises(base.DriverError):
            self.driver.parse(b"")


if __name__ == "__main__":
    unittest.main()
