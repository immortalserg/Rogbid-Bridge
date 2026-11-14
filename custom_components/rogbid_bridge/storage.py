"""Independent timestamped journal; never write directly to Home Assistant Recorder."""
import json
import sqlite3
from pathlib import Path

class Journal:
    """SQLite journal with deterministic deduplication."""

    def __init__(self, path: str):
        self.path = Path(path)

    def initialize(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS records (
                device_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                record_id TEXT NOT NULL,
                measured_at TEXT NOT NULL,
                payload TEXT NOT NULL,
                received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (device_id, kind, record_id)
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS idx_records_date ON records (device_id, kind, measured_at DESC)")

    def insert(self, device_id: str, kind: str, record_id: str, measured_at: str, payload: dict) -> bool:
        with sqlite3.connect(self.path) as db:
            if kind == "steps":
                # Today's step totals evolve throughout the day.
                existing = db.execute(
                    "SELECT payload FROM records WHERE device_id = ? AND kind = ? AND record_id = ?",
                    (device_id, kind, record_id),
                ).fetchone()
                if existing is not None:
                    if json.loads(existing[0]) == payload:
                        return False
                    db.execute(
                        "UPDATE records SET payload = ?, measured_at = ?, received_at = CURRENT_TIMESTAMP WHERE device_id = ? AND kind = ? AND record_id = ?",
                        (json.dumps(payload, ensure_ascii=False, separators=(',', ':')), measured_at, device_id, kind, record_id),
                    )
                    return True
            result = db.execute(
                "INSERT OR IGNORE INTO records (device_id, kind, record_id, measured_at, payload) VALUES (?, ?, ?, ?, ?)",
                (device_id, kind, record_id, measured_at, json.dumps(payload, ensure_ascii=False, separators=(',', ':'))),
            )
            return result.rowcount == 1

    def recent(self, device_id: str, kind: str, limit: int = 100) -> list[dict]:
        with sqlite3.connect(self.path) as db:
            rows = db.execute(
                "SELECT payload FROM records WHERE device_id = ? AND kind = ? ORDER BY measured_at DESC LIMIT ?",
                (device_id, kind, max(1, min(limit, 1000))),
            ).fetchall()
        return [json.loads(row[0]) for row in rows]
