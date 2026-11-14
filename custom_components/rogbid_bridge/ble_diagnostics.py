"""One-shot GATT diagnostic for discovered Watch Bridge BLE advertisements."""
import asyncio
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

    def __init__(self, hass):
        self.hass = hass
        self.task = None
        self.last_attempt = 0.0

    def on_advertisement(self, service_info):
        if not service_info.connectable:
            return
        if self.task is not None and not self.task.done():
            return
        # Android may rotate random BLE addresses. Limit probes globally.
        if time.monotonic() - self.last_attempt < 120:
            return
        self.last_attempt = time.monotonic()
        self.task = self.hass.async_create_task(
            self._probe(service_info.address),
            eager_start=False,
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

    def close(self):
        if self.task is not None and not self.task.done():
            self.task.cancel()
