"""Live watch metrics. Historical measurements are stored separately."""
from homeassistant.components.sensor import (
    SensorEntity,
    SensorEntityDescription,
    SensorStateClass,
)
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from .const import DOMAIN, CONF_DEVICE_ID

SENSORS = (
    SensorEntityDescription(key="steps", name="Steps", native_unit_of_measurement="steps", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="kcal", name="Calories", native_unit_of_measurement="kcal", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="diatance", name="Distance", native_unit_of_measurement="km", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="heart_rate", name="Heart rate", native_unit_of_measurement="bpm", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="spo2", name="Blood oxygen", native_unit_of_measurement="%", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="sleep_duration", name="Sleep duration", native_unit_of_measurement="min", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="sleep_deep", name="Deep sleep", native_unit_of_measurement="min", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="sleep_light", name="Light sleep", native_unit_of_measurement="min", state_class=SensorStateClass.MEASUREMENT),
    SensorEntityDescription(key="sleep_fast", name="REM sleep", native_unit_of_measurement="min", state_class=SensorStateClass.MEASUREMENT),
)

async def async_setup_entry(hass, entry, async_add_entities):
    async_add_entities([
        RogbidSensor(entry, description, hass.data[DOMAIN][entry.entry_id]["state"])
        for description in SENSORS
    ])

class RogbidSensor(SensorEntity):
    _attr_has_entity_name = True

    def __init__(self, entry, description, state):
        self.entity_description = description
        self._state = state
        device_id = entry.data[CONF_DEVICE_ID]
        self._attr_unique_id = f"{device_id}_{description.key}"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, device_id)},
            name=f"Rogbid {device_id}",
            manufacturer="Rogbid",
            model="Z10",
        )
        self._signal = f"{DOMAIN}_{entry.entry_id}_update"

    @property
    def native_value(self):
        value = self._state.get(self.entity_description.key)
        if value is None:
            return None
        try:
            number = float(value)
        except (ValueError, TypeError):
            return None
        if self.entity_description.key == "kcal":
            return round(number / 1000)
        if self.entity_description.key == "diatance":
            return round(number / 1000, 1)
        return number

    async def async_added_to_hass(self):
        self.async_on_remove(
            async_dispatcher_connect(self.hass, self._signal, self.async_write_ha_state)
        )
