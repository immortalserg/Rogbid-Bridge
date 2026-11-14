"""Live Rogbid metrics; the independent journal stores historical records."""
from homeassistant.components.sensor import SensorEntity, SensorEntityDescription, SensorStateClass
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from .const import DOMAIN, CONF_DEVICE_ID

SENSORS = (
    SensorEntityDescription(key="steps", name="Steps", native_unit_of_measurement="steps", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="kcal", name="Calories (raw)", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="diatance", name="Distance (raw)", state_class=SensorStateClass.MEASUREMENT),
)

async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([RogbidSensor(entry, description, hass.data[DOMAIN][entry.entry_id]["state"]) for description in SENSORS])

class RogbidSensor(SensorEntity):
    _attr_has_entity_name = True

    def __init__(self, entry, description, state):
        self.entity_description = description
        self._state = state
        device_id = entry.data[CONF_DEVICE_ID]
        self._attr_unique_id = f"{device_id}_{description.key}"
        self._attr_device_info = DeviceInfo(identifiers={(DOMAIN, device_id)}, name=f"Rogbid {device_id}", manufacturer="Rogbid", model="Z10")
        self._signal = f"{DOMAIN}_{entry.entry_id}_update"

    @property
    def native_value(self):
        value = self._state.get(self.entity_description.key)
        try:
            return float(value) if value is not None else None
        except (ValueError, TypeError):
            return None

    async def async_added_to_hass(self):
        self.async_on_remove(async_dispatcher_connect(self.hass, self._signal, self.async_write_ha_state))
