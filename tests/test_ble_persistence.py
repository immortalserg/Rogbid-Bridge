"""Check the BLE-to-SQLite adapter with a real temporary Journal."""
import asyncio
import importlib
from pathlib import Path
import sys
import tempfile
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge"
PACKAGE = "_watch_persistence_test"
if PACKAGE not in sys.modules:
    pkg = types.ModuleType(PACKAGE)
    pkg.__path__ = [str(ROOT)]
    sys.modules[PACKAGE] = pkg
storage = importlib.import_module(PACKAGE + ".storage")
base = importlib.import_module(PACKAGE + ".ble_drivers.base")
adapter = importlib.import_module(PACKAGE + ".ble_drivers.persistence")


class FakeHass:
    async def async_add_executor_job(self, func, *args):
        return await asyncio.to_thread(func, *args)


class PersistenceTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.journal = storage.Journal(str(Path(self.tmp.name) / "history.sqlite3"))
        self.journal.initialize()

    async def asyncTearDown(self):
        self.tmp.cleanup()

    async def test_save_and_deduplicate(self):
        saved = []
        writer = adapter.BleJournalWriter(FakeHass(), self.journal, "ble_test", lambda *args: saved.append(args))
        m = base.Measurement("heart", "2026-11-15T12:00:00Z", "heart:1", {"heart_rate": 72})
        self.assertTrue(await writer.save(m))
        self.assertFalse(await writer.save(m))
        self.assertEqual(len(saved), 1)
        self.assertEqual(self.journal.recent("ble_test", "heart", 1)[0]["heart_rate"], 72)

    async def test_rejects_rogbid_device_id(self):
        with self.assertRaises(ValueError):
            adapter.BleJournalWriter(FakeHass(), self.journal, "z10_existing")

    async def test_invalid_measurement_not_saved(self):
        writer = adapter.BleJournalWriter(FakeHass(), self.journal, "ble_test")
        m = base.Measurement("heart", "2026-11-15T12:00:00Z", "heart:bad", {"heart_rate": -1})
        with self.assertRaises(ValueError):
            await writer.save(m)
        self.assertEqual(self.journal.recent("ble_test", "heart"), [])


if __name__ == "__main__":
    unittest.main()
