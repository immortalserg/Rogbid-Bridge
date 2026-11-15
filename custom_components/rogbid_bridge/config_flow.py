"""UI configuration for Rogbid Bridge: discover watches from MQTT."""
import asyncio
import hashlib
import re

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.components import mqtt

from .const import DOMAIN, CONF_DEVICE_ID
from .ble_drivers.ha_adapter import discovered_candidates
from .ble_drivers.registry import registry

WATCH_TOPIC = re.compile(r"^rogbid/(z10_[a-f0-9]{32})/state$")
DISCOVERY_SECONDS = 3


class RogbidBridgeConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Select a watch seen on MQTT or enter its device ID manually."""

    VERSION = 1

    async def _discover_watches(self):
        found = set()

        def on_state(message):
            match = WATCH_TOPIC.fullmatch(message.topic)
            if match:
                found.add(match.group(1))

        unsubscribe = None
        try:
            unsubscribe = await mqtt.async_subscribe(
                self.hass, "rogbid/+/state", on_state, 0
            )
            # The watch state is retained; broker replays it on subscribe.
            await asyncio.sleep(DISCOVERY_SECONDS)
        except Exception:
            # Keep manual entry available even if MQTT is not yet connected.
            pass
        finally:
            if unsubscribe is not None:
                unsubscribe()
        return sorted(found)

    async def async_step_user(self, user_input=None):
        return self.async_show_menu(
            step_id="user",
            menu_options=["mqtt_watch", "bluetooth_watch"],
        )

    async def async_step_bluetooth_watch(self, user_input=None):
        """List possible BLE devices, but do not create unsupported entries."""
        candidates = discovered_candidates(self.hass, registry)
        choices = {
            f"{candidate.driver_id}|{candidate.address}":
                f"{candidate.name or candidate.address} · {candidate.driver_name}"
            for candidate in candidates
            if candidate.connectable
        }
        if not choices:
            return self.async_abort(reason="no_ble_candidates")
        if user_input is not None:
            selection = user_input.get("candidate")
            if selection not in choices:
                return self.async_abort(reason="no_ble_candidates")
            driver_id, address = selection.split("|", 1)
            device_id = "ble_" + hashlib.sha256(address.upper().encode()).hexdigest()[:24]
            await self.async_set_unique_id(device_id)
            self._abort_if_unique_id_configured()
            candidate = next(c for c in candidates if c.address == address and c.driver_id == driver_id)
            return self.async_create_entry(
                title=candidate.name or f"BLE {address}",
                data={
                    CONF_DEVICE_ID: device_id,
                    "transport": "ble",
                    "ble_address": address,
                    "ble_driver": driver_id,
                    "ble_name": candidate.name or f"BLE {address}",
                },
            )
        return self.async_show_form(
            step_id="bluetooth_watch",
            data_schema=vol.Schema({vol.Required("candidate"): vol.In(choices)}),
        )

    async def async_step_mqtt_watch(self, user_input=None):
        errors = {}
        if user_input is not None:
            device_id = (user_input.get(CONF_DEVICE_ID) or "").strip()
            if not re.fullmatch(r"[A-Za-z0-9_-]+", device_id):
                errors["base"] = "invalid_device_id"
            else:
                await self.async_set_unique_id(device_id)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(
                    title=f"Rogbid {device_id[-8:]}",
                    data={CONF_DEVICE_ID: device_id},
                )

        watches = await self._discover_watches()
        if watches:
            schema = vol.Schema({
                vol.Required(CONF_DEVICE_ID): vol.In({
                    device_id: f"Rogbid Z10 · {device_id[-8:]}"
                    for device_id in watches
                })
            })
        else:
            schema = vol.Schema({vol.Required(CONF_DEVICE_ID): str})
        return self.async_show_form(
            step_id="mqtt_watch", data_schema=schema, errors=errors
        )
