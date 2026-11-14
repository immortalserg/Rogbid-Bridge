"""Rogbid Bridge integration: MQTT transport and durable event journal."""
import json
import logging
from homeassistant.components import mqtt
from homeassistant.core import HomeAssistant
from homeassistant.config_entries import ConfigEntry
from homeassistant.helpers.dispatcher import async_dispatcher_send
from .const import DOMAIN, CONF_DEVICE_ID
from .storage import Journal

_LOGGER = logging.getLogger(__name__)
PLATFORMS = ["sensor"]

async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    device_id = entry.data[CONF_DEVICE_ID]
    journal = Journal(hass.config.path(".storage", "rogbid_bridge", f"{device_id}.sqlite3"))
    await hass.async_add_executor_job(journal.initialize)
    state = {}
    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = {"state": state, "journal": journal}

    async def on_state(msg):
        try:
            payload = json.loads(msg.payload)
            if not isinstance(payload, dict):
                return
            state.update(payload)
            async_dispatcher_send(hass, f"{DOMAIN}_{entry.entry_id}_update")
        except (ValueError, TypeError):
            _LOGGER.warning("Invalid state payload for Rogbid %s", device_id)

    async def on_history(msg):
        try:
            payload = json.loads(msg.payload)
            if not isinstance(payload, dict):
                return
            kind = payload["kind"]
            record_id = str(payload["id"])
            measured_at = payload["measured_at"]
            if kind not in ("heart", "oxygen", "steps", "sleep") or not isinstance(measured_at, str):
                return
            if len(record_id) > 200 or len(measured_at) > 64:
                return
            await hass.async_add_executor_job(journal.insert, device_id, kind, record_id, measured_at, payload)
        except (KeyError, TypeError, ValueError):
            _LOGGER.warning("Invalid history payload for Rogbid %s", device_id)

    unsub_state = await mqtt.async_subscribe(hass, f"rogbid/{device_id}/state", on_state, 1)
    unsub_history = await mqtt.async_subscribe(hass, f"rogbid/{device_id}/history/+", on_history, 1)
    entry.async_on_unload(unsub_state)
    entry.async_on_unload(unsub_history)
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True

async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    result = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if result:
        hass.data[DOMAIN].pop(entry.entry_id, None)
    return result
