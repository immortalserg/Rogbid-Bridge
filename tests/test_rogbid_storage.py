"""Tests for independent Rogbid journal (no HA runtime required)."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("rogbid_storage", ROOT / "custom_components" / "rogbid_bridge" / "storage.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
Journal = module.Journal


class JournalTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.journal = Journal(str(Path(self.tmp.name) / "journal.sqlite3"))
        self.journal.initialize()

    def test_device_isolation(self):
        a = {"kind": "heart", "id": "heart:1", "measured_at": "2026-11-14T12:00:00Z", "bpm": 70}
        b = {**a, "bpm": 90}
        self.assertTrue(self.journal.insert("watch_a", "heart", a["id"], a["measured_at"], a))
        self.assertTrue(self.journal.insert("watch_b", "heart", b["id"], b["measured_at"], b))
        self.assertEqual(self.journal.recent("watch_a", "heart")[0]["bpm"], 70)
        self.assertEqual(self.journal.recent("watch_b", "heart")[0]["bpm"], 90)

    def test_duplicate_measurements_ignored(self):
        row = {"kind": "oxygen", "id": "oxygen:1", "measured_at": "2026-11-14T12:00:00Z", "spo2": 98}
        self.assertTrue(self.journal.insert("watch_a", "oxygen", row["id"], row["measured_at"], row))
        self.assertFalse(self.journal.insert("watch_a", "oxygen", row["id"], row["measured_at"], row))
        self.assertEqual(len(self.journal.recent("watch_a", "oxygen")), 1)

    def test_daily_steps_updated(self):
        row = {"kind": "steps", "id": "steps:2026-11-14", "measured_at": "2026-11-14T00:00:00Z", "steps": 100}
        self.assertTrue(self.journal.insert("watch_a", "steps", row["id"], row["measured_at"], row))
        self.assertFalse(self.journal.insert("watch_a", "steps", row["id"], row["measured_at"], row))
        updated = {**row, "steps": 200}
        self.assertTrue(self.journal.insert("watch_a", "steps", row["id"], row["measured_at"], updated))
        self.assertEqual(self.journal.recent("watch_a", "steps")[0]["steps"], 200)
        self.assertEqual(len(self.journal.recent("watch_a", "steps")), 1)


if __name__ == "__main__":
    unittest.main()
