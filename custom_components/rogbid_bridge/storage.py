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
            db.execute("""CREATE TABLE IF NOT EXISTS deleted_records (
                device_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                record_id TEXT NOT NULL,
                deleted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (device_id, kind, record_id)
            )""")

    def get_sync_cursor(self, device_id: str, kind: str) -> int:
        """Last durably processed source record ID for a BLE history stream."""
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS sync_cursors (
                device_id TEXT NOT NULL, kind TEXT NOT NULL, last_id INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (device_id, kind)
            )""")
            row = db.execute(
                "SELECT last_id FROM sync_cursors WHERE device_id=? AND kind=?",
                (device_id, kind),
            ).fetchone()
            return int(row[0]) if row else 0

    def save_sync_cursor(self, device_id: str, kind: str, last_id: int) -> None:
        """Advance cursor monotonically only after the record is persisted."""
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS sync_cursors (
                device_id TEXT NOT NULL, kind TEXT NOT NULL, last_id INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (device_id, kind)
            )""")
            db.execute(
                """INSERT INTO sync_cursors (device_id, kind, last_id) VALUES (?, ?, ?)
                ON CONFLICT(device_id, kind) DO UPDATE SET last_id=MAX(last_id, excluded.last_id)""",
                (device_id, kind, last_id),
            )

    def get_steps_cursor(self, device_id: str) -> str:
        """Return previous completed date; replay latest day for changing totals."""
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS steps_sync (
                device_id TEXT PRIMARY KEY, last_date TEXT NOT NULL
            )""")
            row = db.execute("SELECT last_date FROM steps_sync WHERE device_id=?", (device_id,)).fetchone()
            return row[0] if row else ""

    def save_steps_cursor(self, device_id: str, date: str) -> None:
        with sqlite3.connect(self.path) as db:
            db.execute("""CREATE TABLE IF NOT EXISTS steps_sync (
                device_id TEXT PRIMARY KEY, last_date TEXT NOT NULL
            )""")
            db.execute("""INSERT INTO steps_sync (device_id, last_date) VALUES (?, ?)
                ON CONFLICT(device_id) DO UPDATE SET last_date=MAX(last_date, excluded.last_date)""",
                (device_id, date))

    def insert(self, device_id: str, kind: str, record_id: str, measured_at: str, payload: dict) -> bool:
        with sqlite3.connect(self.path) as db:
            if db.execute(
                "SELECT 1 FROM deleted_records WHERE device_id=? AND kind=? AND record_id=?",
                (device_id, kind, record_id),
            ).fetchone():
                return False
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
                "SELECT record_id, payload FROM records WHERE device_id = ? AND kind = ? ORDER BY measured_at DESC LIMIT ?",
                (device_id, kind, max(1, min(limit, 1000))),
            ).fetchall()
        return [dict(json.loads(payload), _record_id=record_id) for record_id, payload in rows]

    def delete(self, device_id: str, kind: str, record_id: str) -> bool:
        """Delete a single record, keeping a tombstone against future MQTT replay."""
        with sqlite3.connect(self.path) as db:
            result = db.execute(
                "DELETE FROM records WHERE device_id=? AND kind=? AND record_id=?",
                (device_id, kind, record_id),
            )
            if result.rowcount:
                db.execute(
                    "INSERT OR IGNORE INTO deleted_records (device_id, kind, record_id) VALUES (?, ?, ?)",
                    (device_id, kind, record_id),
                )
            return result.rowcount == 1

    def step_totals(self, device_id: str, start_date: str, end_date: str) -> dict:
        """Sum one daily steps record per date over an inclusive local-date window."""
        totals = {"steps": 0.0, "kcal": 0.0, "diatance": 0.0}
        with sqlite3.connect(self.path) as db:
            rows = db.execute(
                """SELECT measured_at, payload FROM records
                   WHERE device_id=? AND kind='steps'
                   AND substr(measured_at, 1, 10) BETWEEN ? AND ?
                   ORDER BY measured_at DESC, received_at DESC""",
                (device_id, start_date, end_date),
            ).fetchall()
        seen = set()
        for measured_at, raw in rows:
            day = measured_at[:10]
            if day in seen:
                continue
            seen.add(day)
            payload = json.loads(raw)
            for key in totals:
                value = payload.get(key)
                if type(value) in (int, float) and value >= 0:
                    totals[key] += value
        return totals
