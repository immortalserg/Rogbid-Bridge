"""Authenticated WebSocket history API for the Rogbid Bridge dashboard card."""
import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant

from .const import CONF_DEVICE_ID, DOMAIN

KINDS = ("heart", "oxygen", "steps", "sleep")


@websocket_api.websocket_command(
    {
        vol.Required("type"): "rogbid_bridge/history",
        vol.Required("device_id"): str,
        vol.Required("kind"): vol.In(KINDS),
        vol.Optional("limit", default=100): vol.All(vol.Coerce(int), vol.Range(min=1, max=500)),
    }
)
@websocket_api.async_response
async def websocket_history(hass: HomeAssistant, connection, msg: dict) -> None:
    """Return history only for a configured watch; no arbitrary database access."""
    entries = hass.data.get(DOMAIN, {})
    device_id = msg["device_id"]
    journal = None
    for entry_id, item in entries.items():
        entry = hass.config_entries.async_get_entry(entry_id)
        if entry and entry.data.get(CONF_DEVICE_ID) == device_id:
            journal = item["journal"]
            break

    if journal is None:
        connection.send_error(msg["id"], "not_found", "Rogbid device is not configured")
        return

    try:
        rows = await hass.async_add_executor_job(
            journal.recent, device_id, msg["kind"], msg["limit"]
        )
    except Exception:
        connection.send_error(msg["id"], "read_failed", "Unable to read Rogbid journal")
        return
    connection.send_result(msg["id"], {"records": rows})



@websocket_api.websocket_command({
    vol.Required("type"): "rogbid_bridge/delete_record",
    vol.Required("device_id"): str,
    vol.Required("kind"): vol.In(KINDS),
    vol.Required("record_id"): str,
})
@websocket_api.async_response
async def websocket_delete_record(hass: HomeAssistant, connection, msg: dict) -> None:
    """Delete one local HA history record; never send a command to the watch."""
    if not connection.user.is_admin:
        connection.send_error(msg["id"], "unauthorized", "Administrator access required")
        return
    journal = None
    for entry_id, item in hass.data.get(DOMAIN, {}).items():
        entry = hass.config_entries.async_get_entry(entry_id)
        if entry and entry.data.get(CONF_DEVICE_ID) == msg["device_id"]:
            journal = item["journal"]
            break
    if journal is None:
        connection.send_error(msg["id"], "not_found", "Watch not configured")
        return
    try:
        deleted = await hass.async_add_executor_job(
            journal.delete, msg["device_id"], msg["kind"], msg["record_id"]
        )
    except Exception:
        connection.send_error(msg["id"], "delete_failed", "Could not delete record")
        return
    connection.send_result(msg["id"], {"deleted": deleted})


@websocket_api.websocket_command({vol.Required("type"): "rogbid_bridge/devices"})
@websocket_api.async_response
async def websocket_devices(hass: HomeAssistant, connection, msg: dict) -> None:
    """List configured watches for the history card and its visual editor."""
    watches = []
    for entry in hass.config_entries.async_entries(DOMAIN):
        device_id = entry.data.get(CONF_DEVICE_ID)
        if device_id and entry.entry_id in hass.data.get(DOMAIN, {}):
            watches.append({"device_id": device_id, "name": entry.title})
    connection.send_result(msg["id"], {"devices": watches})


def async_register(hass: HomeAssistant) -> None:
    """Register once, independently of the number of configured watches."""
    websocket_api.async_register_command(hass, websocket_history)
    websocket_api.async_register_command(hass, websocket_devices)
    websocket_api.async_register_command(hass, websocket_delete_record)
