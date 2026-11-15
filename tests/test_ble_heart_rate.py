"""Bluetooth SIG 0x2A37 packet fixtures, no physical device needed."""
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
PACKAGE = "_watch_ble_sig_test"
if PACKAGE not in sys.modules:
    pkg = types.ModuleType(PACKAGE)
    pkg.__path__ = [str(ROOT)]
    sys.modules[PACKAGE] = pkg

decoder = importlib.import_module(PACKAGE + ".heart_rate")
base = importlib.import_module(PACKAGE + ".base")


class HeartRateTests(unittest.TestCase):
    def test_8_bit_value(self):
        self.assertEqual(decoder.decode_heart_rate_measurement(bytes([0, 72]))["heart_rate"], 72)

    def test_16_bit_value(self):
        self.assertEqual(decoder.decode_heart_rate_measurement(bytes([1, 44, 1]))["heart_rate"], 300)

    def test_contact_supported_and_detected(self):
        result = decoder.decode_heart_rate_measurement(bytes([6, 80]))
        self.assertTrue(result["sensor_contact_supported"])
        self.assertTrue(result["sensor_contact_detected"])

    def test_energy_and_rr_intervals(self):
        result = decoder.decode_heart_rate_measurement(bytes([0x18, 70, 5, 0, 0, 4, 0, 2]))
        self.assertEqual(result["energy_expended_kj"], 5)
        self.assertEqual(result["rr_intervals_seconds"], [1.0, 0.5])

    def test_truncated_packets(self):
        for payload in (b"", b"\x01\x40", b"\x08\x40\x01", b"\x10\x40\x01"):
            with self.subTest(payload=payload):
                with self.assertRaises(base.DriverError):
                    decoder.decode_heart_rate_measurement(payload)

    def test_unexpected_trailing_bytes(self):
        with self.assertRaises(base.DriverError):
            decoder.decode_heart_rate_measurement(bytes([0, 70, 1]))


if __name__ == "__main__":
    unittest.main()
