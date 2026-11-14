"""One-shot GATT diagnostic for discovered Watch Bridge BLE advertisements."""
import asyncio
import json
import logging
import time

from bleak import BleakClient
from homeassistant.components import bluetooth

_LOGGER = logging.getLogger(__name__)

SERVICE_UUID = "d312a001-7a7b-4e72-9e5b-a21b3471a001"
REQUEST_UUID = "d312a002-7a7b-4e72-9e5b-a21b3471a001"
STREAM_UUID = "d312a003-7a7b-4e72-9e5b-a21b3471a001"


class GattDiagnostics:
    """Probe one advertisement at a time without interrupting MQTT transport."""

    def __init__(self, hass, expected_device_id, journal):
        self.hass = hass
        self.expected_device_id = expected_device_id
        self.journal = journal
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
                        await self._read_heart_history(client)
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


    async def _read_heart_history(self, client):
        """Resume history in ten-record batches, bounded per connection."""
        after_id = await self.hass.async_add_executor_job(
            self.journal.get_sync_cursor, self.expected_device_id, "heart"
        )
        count = 0
        batch_size = 10
        max_records = 100
        deadline = time.monotonic() + 45
        completed = False
        while count < max_records and time.monotonic() < deadline:
            request = json.dumps({"type": "heart_history", "after_id": after_id})
            await asyncio.wait_for(
                client.write_gatt_char(REQUEST_UUID, request.encode(), response=True),
                timeout=10,
            )
            raw = await asyncio.wait_for(client.read_gatt_char(STREAM_UUID), timeout=10)
            reply = json.loads(bytes(raw).decode())
            if not isinstance(reply, dict) or reply.get("type") != "heart_history":
                raise ValueError("Unexpected heart_history response")
            if reply.get("done") is True:
                completed = True
                break
            record = reply.get("record")
            if not isinstance(record, dict):
                raise ValueError("Missing heart history record")
            record_id = record.get("id")
            measured_at = record.get("measured_at")
            heart_rate = record.get("heart_rate")
            if (type(record_id) is not int or record_id <= after_id
                    or not isinstance(measured_at, str)
                    or type(heart_rate) is not int or not 35 <= heart_rate <= 220):
                raise ValueError("Invalid heart history record")
            payload = {"kind": "heart", "id": f"heart:{record_id}",
                       "measured_at": measured_at, "bpm": heart_rate}
            await self.hass.async_add_executor_job(
                self.journal.insert, self.expected_device_id, "heart",
                payload["id"], measured_at, payload,
            )
            await self.hass.async_add_executor_job(
                self.journal.save_sync_cursor, self.expected_device_id, "heart", record_id
            )
            after_id = record_id
            count += 1
            if count % batch_size == 0:
                _LOGGER.info("Watch Bridge GATT: heart history batch=%s cursor=%s", count // batch_size, after_id)
        _LOGGER.warning(
            "Watch Bridge GATT: heart history received=%s cursor=%s complete=%s",
            count, after_id, completed,
        )

    def close(self):
        if self.task is not None and not self.task.done():
            self.task.cancel()
