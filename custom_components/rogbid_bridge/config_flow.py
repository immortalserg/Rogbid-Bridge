"""UI configuration for Rogbid Bridge."""
import re
import voluptuous as vol
from homeassistant import config_entries
from .const import DOMAIN, CONF_DEVICE_ID

class RogbidBridgeConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Configure a watch by its MQTT device ID."""
    VERSION = 1

    async def async_step_user(self, user_input=None):
        errors = {}
        if user_input is not None:
            device_id = user_input[CONF_DEVICE_ID].strip()
            if not re.fullmatch(r"[A-Za-z0-9_-]+", device_id):
                errors["base"] = "invalid_device_id"
            else:
                await self.async_set_unique_id(device_id)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(title=f"Rogbid {device_id}", data={CONF_DEVICE_ID: device_id})
        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema({vol.Required(CONF_DEVICE_ID): str}),
            errors=errors,
        )
