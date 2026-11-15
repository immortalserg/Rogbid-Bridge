"""Fake-GATT transport tests; no Home Assistant or BLE radio required."""
import asyncio
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
PACKAGE = "_watch_ble_transport_test"
if PACKAGE not in sys.modules:
    pkg = types.ModuleType(PACKAGE)
    pkg.__path__ = [str(ROOT)]
    sys.modules[PACKAGE] = pkg

transport = importlib.import_module(PACKAGE + ".heart_rate_transport")
base = importlib.import_module(PACKAGE + ".base")


class FakeCharacteristic:
    properties = ["notify"]


class FakeService:
    def get_characteristic(self, uuid):
        return FakeCharacteristic()


class FakeServices:
    def __init__(self, available=True):
        self.available = available

    def get_service(self, uuid):
        return FakeService() if self.available else None


class FakeClient:
    def __init__(self, device, timeout, available=True):
        self.is_connected = False
        self.services = FakeServices(available)
        self.callback = None
        self.stopped = False

    async def connect(self):
        self.is_connected = True

    async def start_notify(self, characteristic, callback):
        self.callback = callback

    async def stop_notify(self, characteristic):
        self.stopped = True

    async def disconnect(self):
        self.is_connected = False


class TransportTests(unittest.IsolatedAsyncioTestCase):
    async def test_notification_and_cleanup(self):
        readings = []
        client = FakeClient("fake", 20)
        async with transport.HeartRateSession("fake", readings.append,
                                              client_factory=lambda *a, **k: client):
            client.callback(None, bytearray([0, 72]))
            client.callback(None, bytearray([1]))  # malformed, ignored
        self.assertEqual(len(readings), 1)
        self.assertEqual(readings[0].values["heart_rate"], 72)
        self.assertTrue(client.stopped)
        self.assertFalse(client.is_connected)

    async def test_disconnect_event(self):
        readings = []
        client = FakeClient("fake", 20)
        async with transport.HeartRateSession("fake", readings.append,
                                              client_factory=lambda *a, **k: client) as session:
            session._on_disconnect(client)
            await asyncio.wait_for(session.wait_disconnected(), timeout=1)

    async def test_missing_service_disconnects(self):
        client = FakeClient("fake", 20, available=False)
        with self.assertRaises(base.DriverError):
            async with transport.HeartRateSession("fake", lambda m: None,
                                                  client_factory=lambda *a, **k: client):
                pass
        self.assertFalse(client.is_connected)


if __name__ == "__main__":
    unittest.main()
