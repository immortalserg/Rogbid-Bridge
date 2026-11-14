"""Transport-neutral BLE journal protocol for a future Wear OS GATT service.

This module does not establish a BLE connection. It validates reassembled
application frames received via a Home Assistant Bluetooth proxy.
"""
from __future__ import annotations

import json
from typing import Any

# Custom UUIDs reserved for Watch Bridge; the watch GATT server must implement
# the same values before BLE transfer can work.
SERVICE_UUID = "d312a001-7a7b-4e72-9e5b-a21b3471a001"
REQUEST_UUID = "d312a002-7a7b-4e72-9e5b-a21b3471a001"
STREAM_UUID = "d312a003-7a7b-4e72-9e5b-a21b3471a001"
MAX_FRAME_BYTES = 8192
ALLOWED_KINDS = frozenset(("heart", "oxygen", "steps", "sleep"))


class FrameError(ValueError):
    """Malformed or unsupported Watch Bridge BLE frame."""


def decode_frame(raw: bytes) -> dict[str, Any]:
    """Validate one complete UTF-8 JSON frame.

    GATT notifications may split frames; callers must reassemble them first.
    No fake values are synthesized for unsupported metrics.
    """
    if not raw or len(raw) > MAX_FRAME_BYTES:
        raise FrameError("Invalid frame size")
    try:
        frame = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise FrameError("Invalid UTF-8 JSON") from exc
    if not isinstance(frame, dict):
        raise FrameError("Expected JSON object")
    if frame.get("type") not in ("record", "done"):
        raise FrameError("Unknown frame type")
    if frame["type"] == "done":
        if not isinstance(frame.get("cursor"), str) or len(frame["cursor"]) > 200:
            raise FrameError("Invalid completion cursor")
        return frame
    record = frame.get("payload")
    if not isinstance(record, dict):
        raise FrameError("Missing record payload")
    kind, record_id, measured_at = (
        record.get("kind"), record.get("id"), record.get("measured_at")
    )
    if kind not in ALLOWED_KINDS:
        raise FrameError("Unsupported kind")
    if not isinstance(record_id, str) or not 1 <= len(record_id) <= 200:
        raise FrameError("Invalid record ID")
    if not isinstance(measured_at, str) or not 1 <= len(measured_at) <= 64:
        raise FrameError("Invalid measurement timestamp")
    return frame


def encode_request(cursor: str = "") -> bytes:
    """Create a request for records after the last acknowledged cursor."""
    if not isinstance(cursor, str) or len(cursor) > 200:
        raise FrameError("Invalid cursor")
    return json.dumps({"type": "sync", "cursor": cursor}, separators=(",", ":")).encode()
