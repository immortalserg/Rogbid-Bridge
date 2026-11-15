"""Experimental Bluetooth SIG Heart Rate Service driver.

Generic BLE heart-rate sensors only. Does not identify a watch manufacturer,
support history, or claim compatibility with any specific device.
"""
from __future__ import annotations

from datetime import datetime, timezone

from .base import BleDriver, BleIdentity, Measurement
from .heart_rate import HEART_RATE_SERVICE_UUID, decode_heart_rate_measurement


class StandardHeartRateDriver(BleDriver):
    driver_id = "bluetooth_sig_heart_rate"
    display_name = "Bluetooth SIG Heart Rate (experimental)"

    def __init__(self):
        self._sequence = 0

    def matches(self, device: BleIdentity) -> bool:
        return (
            device.connectable
            and HEART_RATE_SERVICE_UUID in (uuid.lower() for uuid in device.service_uuids)
        )

    def parse(self, payload: bytes) -> list[Measurement]:
        values = decode_heart_rate_measurement(payload)
        self._sequence += 1
        timestamp = datetime.now(timezone.utc).isoformat()
        return [
            Measurement(
                kind="heart",
                measured_at=timestamp,
                record_id=f"sig-heart:{timestamp}:{self._sequence}",
                values=values,
            )
        ]
