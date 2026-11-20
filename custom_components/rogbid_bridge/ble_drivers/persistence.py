"""Opt-in bridge from normalized BLE measurements to existing SQLite Journal.

This module does not configure devices, create entities, or modify MQTT behavior.
"""
from __future__ import annotations

from collections.abc import Callable
from .base import Measurement


def journal_payload(measurement: Measurement) -> dict:
    """Convert normalized BLE data to the existing Watch Bridge journal schema."""
    values = dict(measurement.values)
    if measurement.kind == "heart":
        bpm = values.get("heart_rate")
        if type(bpm) is not int or not 1 <= bpm <= 65535:
            raise ValueError("Invalid heart rate")
        values["heart_rate"] = bpm
    elif measurement.kind == "oxygen":
        spo2 = values.get("spo2")
        if type(spo2) is not int or not 0 <= spo2 <= 100:
            raise ValueError("Invalid oxygen saturation")
    elif measurement.kind == "steps":
        if type(values.get("steps")) is not int or values["steps"] < 0:
            raise ValueError("Invalid steps")
    return {**values, "kind": measurement.kind, "id": measurement.record_id,
            "measured_at": measurement.measured_at}


class BleJournalWriter:
    """Persist selected device readings using HA's executor, without blocking BLE."""

    def __init__(self, hass, journal, device_id: str,
                 on_saved: Callable[[str, dict], None] | None = None):
        if not device_id or not device_id.startswith("ble_"):
            raise ValueError("BLE devices require a separate ble_ device ID")
        self.hass = hass
        self.journal = journal
        self.device_id = device_id
        self.on_saved = on_saved

    async def save(self, measurement: Measurement) -> bool:
        payload = journal_payload(measurement)
        inserted = await self.hass.async_add_executor_job(
            self.journal.insert, self.device_id, measurement.kind,
            measurement.record_id, measurement.measured_at, payload
        )
        if inserted and self.on_saved is not None:
            self.on_saved(measurement.kind, payload)
        return bool(inserted)
