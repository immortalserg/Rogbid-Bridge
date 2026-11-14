"""Rogbid Bridge integration: MQTT transport and durable event journal."""
import json
import logging
from pathlib import Path
from homeassistant.components import mqtt, bluetooth
from homeassistant.components.bluetooth import BluetoothScanningMode
from homeassistant.components.http import StaticPathConfig
from homeassistant.core import HomeAssistant
from homeassistant.config_entries import ConfigEntry
from homeassistant.helpers.dispatcher import async_dispatcher_send
from .const import DOMAIN, CONF_DEVICE_ID
from .ble_diagnostics import GattDiagnostics
from .storage import Journal
from .websocket import async_register as register_websocket

_LOGGER = logging.getLogger(__name__)
PLATFORMS = ["sensor"]
CARD_URL = "/rogbid_bridge/rogbid-history-card.js"

def sensor_values(kind, record):
    """Convert stored measurements into HA sensor fields."""
    if kind == "heart":
        values = {"heart_rate": record.get("heart_rate", record.get("bpm"))}
    elif kind == "oxygen":
        values = {"spo2": record.get("spo2")}
    elif kind == "steps":
        values = {key: record.get(key) for key in ("steps", "kcal", "diatance")}
    elif kind == "sleep":
        values = {
            "sleep_duration": record.get("duration_minutes", record.get("sleep_duration")),
            "sleep_deep": record.get("deep_minutes", record.get("sleep_deep")),
            "sleep_light": record.get("light_minutes", record.get("sleep_light")),
            "sleep_fast": record.get("fast_minutes", record.get("rem_minutes", record.get("sleep_fast"))),
        }
    else:
        return {}
    return {key: value for key, value in values.items()
            if type(value) in (int, float) and value >= 0}



async def _register_lovelace_resource(hass: HomeAssistant) -> None:
    """Register the history card as a Lovelace module in storage mode."""
    lovelace = hass.data.get("lovelace")
    resources = getattr(lovelace, "resources", None)
    if resources is None:
        _LOGGER.info("Lovelace resources unavailable; card can be added manually")
        return
    try:
        await resources.async_load()
        if any(item.get("url", "").split("?")[0] == CARD_URL for item in resources.async_items()):
            return
        await resources.async_create_item({"res_type": "module", "url": CARD_URL})
        _LOGGER.info("Registered Rogbid history card in Lovelace resources")
    except (AttributeError, ValueError, RuntimeError) as exc:
        _LOGGER.warning("Could not register Rogbid history card automatically: %s", exc)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    device_id = entry.data[CONF_DEVICE_ID]
    journal = Journal(hass.config.path(".storage", "rogbid_bridge", f"{device_id}.sqlite3"))
    await hass.async_add_executor_job(journal.initialize)
    state = {}
    # Restore last saved values before registering entities.
    for history_kind in ("heart", "oxygen", "steps", "sleep"):
        latest = await hass.async_add_executor_job(journal.recent, device_id, history_kind, 1)
        if latest:
            state.update(sensor_values(history_kind, latest[0]))
    devices = hass.data.setdefault(DOMAIN, {})
    if not devices.get("_websocket_registered"):
        register_websocket(hass)
        devices["_websocket_registered"] = True
    if not devices.get("_card_registered"):
        await hass.http.async_register_static_paths([
            StaticPathConfig(
                CARD_URL,
                str(Path(__file__).with_name("rogbid-history-card.js")),
                False,
            )
        ])
        devices["_card_registered"] = True
        await _register_lovelace_resource(hass)
    devices[entry.entry_id] = {"state": state, "journal": journal}

    def on_ble_record(kind, record):
        values = sensor_values(kind, record)
        if values:
            state.update(values)
            async_dispatcher_send(hass, f"{DOMAIN}_{entry.entry_id}_update")

    gatt_diagnostics = GattDiagnostics(hass, device_id, journal, on_record=on_ble_record)
    entry.async_on_unload(gatt_diagnostics.close)

    # Diagnostic-only: advertisements are forwarded by ESPHome Bluetooth Proxy.
    def on_ble_advertisement(service_info, change):
        _LOGGER.warning(
            "Watch Bridge BLE discovered: address=%s name=%s rssi=%s source=%s connectable=%s uuids=%s change=%s",
            service_info.address, service_info.name, service_info.rssi,
            service_info.source, service_info.connectable,
            service_info.service_uuids, change,
        )
        gatt_diagnostics.on_advertisement(service_info)

    unsub_ble = bluetooth.async_register_callback(
        hass,
        on_ble_advertisement,
        {"service_uuid": "d312a001-7a7b-4e72-9e5b-a21b3471a001"},
        BluetoothScanningMode.ACTIVE,
    )
    entry.async_on_unload(unsub_ble)


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
            # Reject mismatches between the MQTT topic and JSON record kind.
            if msg.topic != f"rogbid/{device_id}/history/{kind}":
                _LOGGER.warning("Ignoring mismatched Rogbid history topic: %s", msg.topic)
                return
            record_id = str(payload["id"])
            measured_at = payload["measured_at"]
            if kind not in ("heart", "oxygen", "steps", "sleep") or not isinstance(measured_at, str):
                return
            if len(record_id) > 200 or len(measured_at) > 64:
                return
            inserted = await hass.async_add_executor_job(
                journal.insert, device_id, kind, record_id, measured_at, payload
            )
            if inserted:
                on_ble_record(kind, payload)
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
