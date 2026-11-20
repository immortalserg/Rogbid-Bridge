"""Hardware-free end-to-end BLE notification -> SQLite journal test."""
import asyncio
import importlib
from pathlib import Path
import sys
import tempfile
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge"
PACKAGE = "_watch_ble_end_to_end_test"
pkg = types.ModuleType(PACKAGE)
pkg.__path__ = [str(ROOT)]
sys.modules[PACKAGE] = pkg
drivers = types.ModuleType(PACKAGE + ".ble_drivers")
drivers.__path__ = [str(ROOT / "ble_drivers")]
sys.modules[drivers.__name__] = drivers

transport = importlib.import_module(PACKAGE + ".ble_drivers.heart_rate_transport")
persistence = importlib.import_module(PACKAGE + ".ble_drivers.persistence")
storage = importlib.import_module(PACKAGE + ".storage")


class FakeHass:
    async def async_add_executor_job(self, fn, *args):
        return await asyncio.to_thread(fn, *args)


class FakeCharacteristic:
    properties = ["notify"]


class FakeService:
    def get_characteristic(self, uuid):
        return FakeCharacteristic()


class FakeServices:
    def get_service(self, uuid):
        return FakeService()


class FakeClient:
    def __init__(self, device, timeout, disconnected_callback=None):
        self.is_connected = False
        self.services = FakeServices()
        self.callback = None
        self.disconnected_callback = disconnected_callback

    async def connect(self):
        self.is_connected = True

    async def start_notify(self, characteristic, callback):
        self.callback = callback

    async def stop_notify(self, characteristic):
        pass

    async def disconnect(self):
        self.is_connected = False
        if self.disconnected_callback:
            self.disconnected_callback(self)


class EndToEndTests(unittest.IsolatedAsyncioTestCase):
    async def test_notification_saved_to_sqlite_and_disconnect(self):
        with tempfile.TemporaryDirectory() as directory:
            journal = storage.Journal(Path(directory) / "heart.sqlite3")
            journal.initialize()
            writer = persistence.BleJournalWriter(FakeHass(), journal, "ble_fixture")
            readings = []
            client = FakeClient("fixture", 20)
            async with transport.HeartRateSession(
                "fixture", readings.append, client_factory=lambda *a, **k: client
            ) as session:
                client.callback(None, bytearray([0, 75]))
                await asyncio.sleep(0)
                self.assertEqual(len(readings), 1)
                self.assertTrue(await writer.save(readings[0]))
                self.assertFalse(await writer.save(readings[0]))
                client.disconnected_callback(client)
                await asyncio.wait_for(session.wait_disconnected(), 1)
            records = journal.recent("ble_fixture", "heart", 10)
            self.assertEqual(len(records), 1)
            self.assertEqual(records[0]["heart_rate"], 75)


if __name__ == "__main__":
    unittest.main()
