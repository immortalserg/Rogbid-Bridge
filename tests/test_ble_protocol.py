"""Pure Python protocol checks; no watch or ESP32 required."""
import importlib.util
import json
from pathlib import Path
import unittest

PATH = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_protocol.py"
SPEC = importlib.util.spec_from_file_location("watch_bridge_ble_protocol", PATH)
protocol = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(protocol)


class TestBleProtocol(unittest.TestCase):
    def test_heart(self):
        payload = {"kind": "heart", "id": "heart:1", "measured_at": "2026-11-14T12:00:00Z", "bpm": 72}
        self.assertEqual(protocol.decode_frame(json.dumps({"type": "record", "payload": payload}).encode())["payload"], payload)

    def test_unknown_metric_is_not_accepted(self):
        with self.assertRaises(protocol.FrameError):
            protocol.decode_frame(b'{"type":"record","payload":{"kind":"fake","id":"x","measured_at":"now"}}')

    def test_request(self):
        self.assertEqual(json.loads(protocol.encode_request("123"))["cursor"], "123")

    def test_completion(self):
        self.assertEqual(protocol.decode_frame(b'{"type":"done","cursor":"123"}')["cursor"], "123")

    def test_invalid_json(self):
        with self.assertRaises(protocol.FrameError):
            protocol.decode_frame(b'{broken')

    def test_frame_size(self):
        with self.assertRaises(protocol.FrameError):
            protocol.decode_frame(b'a' * 8193)


if __name__ == "__main__":
    unittest.main()
