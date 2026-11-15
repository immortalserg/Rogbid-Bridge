"""Bluetooth heart-rate runtime, isolated from legacy MQTT setup."""
from __future__ import annotations

import asyncio
import logging

from homeassistant.components import bluetooth
from homeassistant.exceptions import ConfigEntryNotReady
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .ble_drivers.heart_rate_transport import HeartRateSession
from .ble_drivers.persistence import BleJournalWriter
from .ble_drivers.registry import registry
from .const import CONF_DEVICE_ID, DOMAIN
from .storage import Journal

_LOGGER = logging.getLogger(__name__)


async def async_setup_ble_entry(hass, entry):
    """Start one explicitly selected standard Heart Rate device."""
    device_id = entry.data[CONF_DEVICE_ID]
    address = entry.data["ble_address"]
    driver_id = entry.data["ble_driver"]
    if not device_id.startswith("ble_") or registry.get(driver_id) is None:
        raise ConfigEntryNotReady("Unsupported BLE driver")
    ble_device = bluetooth.async_ble_device_from_address(hass, address, connectable=True)
    if ble_device is None:
        raise ConfigEntryNotReady("Bluetooth device is not currently reachable")
    journal = Journal(hass.config.path(".storage", "rogbid_bridge", f"{device_id}.sqlite3"))
    await hass.async_add_executor_job(journal.initialize)
    state = {}
    latest = await hass.async_add_executor_job(journal.recent, device_id, "heart", 1)
    if latest:
        state["heart_rate"] = latest[0].get("heart_rate")
    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = {
        "state": state,
        "journal": journal,
        "ble": True,
    }

    def on_saved(kind, payload):
        if kind == "heart":
            state["heart_rate"] = payload["heart_rate"]
            async_dispatcher_send(hass, f"{DOMAIN}_{entry.entry_id}_update")

    writer = BleJournalWriter(hass, journal, device_id, on_saved)
    queue = asyncio.Queue(maxsize=100)

    def on_measurement(measurement):
        try:
            queue.put_nowait(measurement)
        except asyncio.QueueFull:
            _LOGGER.warning("BLE heart-rate queue full for %s", device_id)

    async def consume():
        while True:
            reading = await queue.get()
            try:
                await writer.save(reading)
            except Exception:
                _LOGGER.exception("Unable to persist BLE heart-rate measurement")
            finally:
                queue.task_done()

    async def stream():
        while True:
            device = bluetooth.async_ble_device_from_address(hass, address, connectable=True)
            if device is not None:
                try:
                    async with HeartRateSession(device, on_measurement):
                        while True:
                            await asyncio.sleep(5)
                except asyncio.CancelledError:
                    raise
                except Exception:
                    _LOGGER.warning("BLE heart-rate session failed for %s; retrying", address, exc_info=True)
            await asyncio.sleep(30)

    consumer = hass.async_create_task(consume())
    streamer = hass.async_create_task(stream())

    def stop():
        streamer.cancel()
        consumer.cancel()

    entry.async_on_unload(stop)
    await hass.config_entries.async_forward_entry_setups(entry, ["sensor"])
    return True
