"""One-shot GATT diagnostic for discovered Watch Bridge BLE advertisements."""
import asyncio
import base64
import json
import logging
import time
from datetime import date, timedelta

from bleak import BleakClient
from homeassistant.components import bluetooth

_LOGGER = logging.getLogger(__name__)

SERVICE_UUID = "d312a001-7a7b-4e72-9e5b-a21b3471a001"
REQUEST_UUID = "d312a002-7a7b-4e72-9e5b-a21b3471a001"
STREAM_UUID = "d312a003-7a7b-4e72-9e5b-a21b3471a001"


class GattDiagnostics:
    """Probe one advertisement at a time without interrupting MQTT transport."""

    def __init__(self, hass, expected_device_id, journal, on_record=None):
        self.hass = hass
        self.expected_device_id = expected_device_id
        self.journal = journal
        self.on_record = on_record
        self.task = None
        self.last_attempt = 0.0

    def on_advertisement(self, service_info):
        if not service_info.connectable:
            return
        if self.task is not None and not self.task.done():
            return
        # Cached advertisements may be replayed after proxy scanner reconnects.
        # They cannot be used to connect to rotating Android BLE addresses.
        seen_at = getattr(service_info, "time", None)
        if isinstance(seen_at, (int, float)):
            age = time.monotonic() - seen_at
            if age > 15:
                _LOGGER.debug(
                    "Watch Bridge GATT: skipping stale advertisement %s age=%.1fs",
                    service_info.address, age,
                )
                return
        # Android may rotate random BLE addresses. Limit probes globally.
        if time.monotonic() - self.last_attempt < 120:
            return
        self.last_attempt = time.monotonic()
        self.task = self.hass.async_create_task(
            self._probe(service_info.address),
        )

    async def _probe(self, address):
        client = None
        try:
            device = bluetooth.async_ble_device_from_address(
                self.hass, address, connectable=True
            )
            if device is None:
                _LOGGER.warning("Watch Bridge GATT: device unavailable: %s", address)
                return
            _LOGGER.warning("Watch Bridge GATT: connecting to %s", address)
            client = BleakClient(device, timeout=20.0)
            await asyncio.wait_for(client.connect(), timeout=25)
            services = client.services
            service = services.get_service(SERVICE_UUID)
            if service is None:
                _LOGGER.warning(
                    "Watch Bridge GATT: connected but service %s missing on %s",
                    SERVICE_UUID, address,
                )
                return
            chars = {str(ch.uuid).lower(): ch for ch in service.characteristics}
            for uuid, label in ((REQUEST_UUID, "request"), (STREAM_UUID, "stream")):
                ch = chars.get(uuid)
                _LOGGER.warning(
                    "Watch Bridge GATT: %s characteristic %s properties=%s",
                    label, "found" if ch else "MISSING",
                    list(ch.properties) if ch else [],
                )
            if REQUEST_UUID in chars and STREAM_UUID in chars:
                await asyncio.wait_for(
                    client.write_gatt_char(
                        REQUEST_UUID,
                        json.dumps({"type": "device_info"}).encode("utf-8"),
                        response=True,
                    ),
                    timeout=10,
                )
                raw = await asyncio.wait_for(
                    client.read_gatt_char(STREAM_UUID), timeout=10
                )
                reply = json.loads(bytes(raw).decode("utf-8"))
                if not isinstance(reply, dict) or reply.get("type") != "device_info":
                    _LOGGER.warning("Watch Bridge GATT: unexpected device_info reply: %s", reply)
                else:
                    reported_id = reply.get("device_id")
                    if reported_id == self.expected_device_id:
                        _LOGGER.warning(
                            "Watch Bridge GATT: device_info verified id=%s protocol=%s",
                            reported_id, reply.get("protocol"),
                        )
                        for kind in ("heart", "oxygen", "steps", "sleep"):
                            try:
                                await self._read_history(client, kind)
                            except Exception as exc:
                                _LOGGER.warning("Watch Bridge GATT: %s sync failed: %s", kind, exc)
                    else:
                        _LOGGER.warning(
                            "Watch Bridge GATT: device_id mismatch expected=%s reported=%s",
                            self.expected_device_id, reported_id,
                        )
            _LOGGER.warning("Watch Bridge GATT: diagnostic complete for %s", address)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            _LOGGER.warning(
                "Watch Bridge GATT: connection failed for %s: %s: %s",
                address, type(exc).__name__, exc,
            )
        finally:
            if client is not None and client.is_connected:
                try:
                    await asyncio.wait_for(client.disconnect(), timeout=10)
                except Exception as exc:
                    _LOGGER.warning("Watch Bridge GATT: disconnect failed: %s", exc)


    async def _read_history(self, client, kind):
        """Synchronize one journal, preserving per-kind cursor on disconnect."""
        if kind == "steps":
            last_date = await self.hass.async_add_executor_job(
                self.journal.get_steps_cursor, self.expected_device_id
            )
            # Revisit the latest day because step totals can change.
            after_date = ((date.fromisoformat(last_date) - timedelta(days=1)).isoformat()
                          if last_date else "")
        else:
            after_id = await self.hass.async_add_executor_job(
                self.journal.get_sync_cursor, self.expected_device_id, kind
            )
        count = 0
        completed = False
        deadline = time.monotonic() + 35
        while count < 100 and time.monotonic() < deadline:
            request = {"type": f"{kind}_history"}
            if kind == "steps":
                request["after_date"] = after_date
            else:
                request["after_id"] = after_id
            if kind == "sleep":
                # Android sends small base64 fragments to avoid GATT MTU truncation.
                fragments = bytearray()
                total = None
                while True:
                    request["offset"] = len(fragments)
                    await asyncio.wait_for(
                        client.write_gatt_char(REQUEST_UUID, json.dumps(request).encode(),
                                               response=True), timeout=10,
                    )
                    raw = await asyncio.wait_for(client.read_gatt_char(STREAM_UUID), timeout=10)
                    envelope = json.loads(bytes(raw).decode())
                    if (not isinstance(envelope, dict)
                            or envelope.get("type") != "sleep_history_chunk"
                            or envelope.get("offset") != len(fragments)
                            or type(envelope.get("total")) is not int):
                        raise ValueError("Invalid sleep chunk header")
                    if total is None:
                        total = envelope["total"]
                        if not 1 <= total <= 262144:
                            raise ValueError("Invalid sleep payload length")
                    if envelope["total"] != total:
                        raise ValueError("Sleep payload changed during transfer")
                    fragment = base64.b64decode(envelope["data"], validate=True)
                    if not fragment or len(fragment) > 64 or len(fragments) + len(fragment) > total:
                        raise ValueError("Invalid sleep chunk size")
                    fragments.extend(fragment)
                    if len(fragments) == total:
                        break
                reply = json.loads(fragments.decode("utf-8"))
            else:
                await asyncio.wait_for(
                    client.write_gatt_char(REQUEST_UUID, json.dumps(request).encode(),
                                           response=True), timeout=10,
                )
                raw = await asyncio.wait_for(client.read_gatt_char(STREAM_UUID), timeout=10)
                reply = json.loads(bytes(raw).decode())
            if not isinstance(reply, dict) or reply.get("type") != request["type"]:
                raise ValueError(f"Unexpected {kind} history response")
            if reply.get("done") is True:
                completed = True
                break
            record = reply.get("record")
            if not isinstance(record, dict):
                raise ValueError(f"Missing {kind} record")
            measured_at = record.get("measured_at")
            if not isinstance(measured_at, str) or not measured_at or len(measured_at) > 64:
                raise ValueError(f"Invalid {kind} timestamp")
            if kind == "steps":
                record_date = record.get("date")
                if (not isinstance(record_date, str) or len(record_date) != 10
                        or record_date <= after_date):
                    raise ValueError("Invalid steps date")
                date.fromisoformat(record_date)
                payload = {"kind": kind, "id": f"steps:{record_date}",
                           "measured_at": measured_at, "date": record_date}
                for field in ("steps", "kcal", "diatance"):
                    if field in record:
                        value = record[field]
                        if type(value) not in (int, float) or not 0 <= value <= 1e9:
                            raise ValueError(f"Invalid steps {field}")
                        payload[field] = value
                cursor_value = record_date
            else:
                record_id = record.get("id")
                if type(record_id) is not int or record_id <= after_id:
                    raise ValueError(f"Invalid {kind} ID")
                if kind in ("heart", "oxygen"):
                    field = "heart_rate" if kind == "heart" else "spo2"
                    value = record.get(field)
                    lower, upper = (35, 220) if kind == "heart" else (70, 100)
                    if type(value) is not int or not lower <= value <= upper:
                        raise ValueError(f"Invalid {kind} measurement")
                    payload = {"kind": kind, "id": f"{kind}:{record_id}",
                               "measured_at": measured_at,
                               "bpm" if kind == "heart" else "spo2": value}
                else:
                    digest = record.get("digest")
                    if (not isinstance(digest, str) or len(digest) != 64
                            or any(c not in "0123456789abcdef" for c in digest)):
                        raise ValueError("Invalid sleep digest")
                    payload = dict(record)
                    payload.pop("digest", None)
                    payload.pop("id", None)
                    payload.update(kind="sleep", id=f"sleep:{digest}")
                cursor_value = record_id
            await self.hass.async_add_executor_job(
                self.journal.insert, self.expected_device_id, kind,
                payload["id"], measured_at, payload,
            )
            if self.on_record is not None:
                self.on_record(kind, payload)
            if kind == "steps":
                await self.hass.async_add_executor_job(
                    self.journal.save_steps_cursor, self.expected_device_id, cursor_value
                )
                after_date = cursor_value
            else:
                await self.hass.async_add_executor_job(
                    self.journal.save_sync_cursor, self.expected_device_id, kind, cursor_value
                )
                after_id = cursor_value
            count += 1
        cursor = after_date if kind == "steps" else after_id
        _LOGGER.warning(
            "Watch Bridge GATT: %s history received=%s cursor=%s complete=%s",
            kind, count, cursor, completed,
        )

    def close(self):
        if self.task is not None and not self.task.done():
            self.task.cancel()
