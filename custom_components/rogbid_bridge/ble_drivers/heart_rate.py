"""Bluetooth SIG Heart Rate Measurement (0x2A37) protocol decoder.

This is a standards-based decoder, NOT a claim of watch model compatibility.
The standard Heart Rate Service UUID is 0x180D. Actual measurements arrive
through characteristic notifications after a GATT connection and subscription.
"""
from __future__ import annotations

from .base import DriverError


HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
HEART_RATE_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb"


def decode_heart_rate_measurement(payload: bytes) -> dict:
    """Decode a SIG Heart Rate Measurement characteristic notification.

    Flags: bit0 value width, bits1-2 sensor contact, bit3 energy expended,
    bit4 RR intervals. Reserved flag bits are ignored.
    """
    if not payload:
        raise DriverError("Empty heart rate measurement")
    flags = payload[0]
    offset = 1
    value_size = 2 if flags & 0x01 else 1
    if len(payload) < offset + value_size:
        raise DriverError("Truncated heart rate value")
    bpm = int.from_bytes(payload[offset:offset + value_size], "little")
    offset += value_size

    contact_supported = bool(flags & 0x04)
    contact_detected = bool(flags & 0x02) if contact_supported else None
    energy = None
    if flags & 0x08:
        if len(payload) < offset + 2:
            raise DriverError("Truncated energy expended")
        energy = int.from_bytes(payload[offset:offset + 2], "little")
        offset += 2

    rr_intervals = []
    if flags & 0x10:
        if (len(payload) - offset) % 2:
            raise DriverError("Truncated RR interval")
        while offset < len(payload):
            rr = int.from_bytes(payload[offset:offset + 2], "little")
            rr_intervals.append(rr / 1024.0)
            offset += 2
    elif len(payload) != offset:
        raise DriverError("Unexpected trailing bytes")

    if not 1 <= bpm <= 65535:
        raise DriverError("Invalid heart rate value")
    return {
        "heart_rate": bpm,
        "sensor_contact_supported": contact_supported,
        "sensor_contact_detected": contact_detected,
        "energy_expended_kj": energy,
        "rr_intervals_seconds": rr_intervals,
    }
