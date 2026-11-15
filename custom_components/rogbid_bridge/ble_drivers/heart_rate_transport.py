"""Opt-in GATT transport for the standard BLE Heart Rate Service.

No scanning, automatic connections, journal writes or entity creation.
The caller owns device selection and the Home Assistant Bluetooth BLEDevice.
"""
from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import Callable

from .base import DriverError, Measurement
from .heart_rate import (
    HEART_RATE_MEASUREMENT_UUID,
    HEART_RATE_SERVICE_UUID,
    decode_heart_rate_measurement,
)

ReadingCallback = Callable[[Measurement], None]


class HeartRateSession:
    """Connect to one explicitly selected device and subscribe to notifications."""

    def __init__(self, device, on_measurement: ReadingCallback, client_factory=None):
        self.device = device
        self.on_measurement = on_measurement
        self._client_factory = client_factory
        self._client = None
        self._counter = 0
        self._disconnected = asyncio.Event()
        self._loop = None

    def _on_notification(self, _characteristic, payload: bytearray) -> None:
        try:
            decoded = decode_heart_rate_measurement(bytes(payload))
            self._counter += 1
            now = datetime.now(timezone.utc).isoformat()
            reading = Measurement(
                kind="heart",
                measured_at=now,
                record_id=f"ble-heart:{self._counter}:{now}",
                values=decoded,
            )
            self.on_measurement(reading)
        except (DriverError, ValueError):
            # A malformed notification must not tear down the BLE callback.
            return

    def _on_disconnect(self, _client):
        if self._loop is not None:
            self._loop.call_soon_threadsafe(self._disconnected.set)

    async def wait_disconnected(self):
        await self._disconnected.wait()

    async def __aenter__(self):
        self._loop = asyncio.get_running_loop()
        self._disconnected.clear()
        if self._client_factory is None:
            from bleak import BleakClient
            factory = BleakClient
        else:
            factory = self._client_factory
        client = factory(self.device, timeout=20.0, disconnected_callback=self._on_disconnect)
        self._client = client
        try:
            await asyncio.wait_for(client.connect(), timeout=25.0)
            service = client.services.get_service(HEART_RATE_SERVICE_UUID)
            if service is None:
                raise DriverError("Heart Rate Service not available")
            characteristic = service.get_characteristic(HEART_RATE_MEASUREMENT_UUID)
            if characteristic is None or "notify" not in characteristic.properties:
                raise DriverError("Heart Rate Measurement notifications unavailable")
            await asyncio.wait_for(
                client.start_notify(characteristic, self._on_notification),
                timeout=10.0,
            )
            return self
        except BaseException:
            await self._disconnect()
            raise

    async def _disconnect(self):
        client, self._client = self._client, None
        if client is not None and client.is_connected:
            await asyncio.wait_for(client.disconnect(), timeout=10.0)

    async def __aexit__(self, exc_type, exc, tb):
        try:
            if self._client is not None and self._client.is_connected:
                await asyncio.wait_for(
                    self._client.stop_notify(HEART_RATE_MEASUREMENT_UUID),
                    timeout=10.0,
                )
        finally:
            await self._disconnect()
