"""Independent HA sensor entities for future explicitly configured BLE devices.

These entities are intentionally not registered by the existing Rogbid setup.
A future BLE config-entry flow will own their lifecycle and device identity.
"""
from __future__ import annotations

from homeassistant.components.sensor import SensorEntity, SensorEntityDescription, SensorStateClass
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo

from ..const import DOMAIN

BLE_HEART_RATE = SensorEntityDescription(
    key="heart_rate",
    name="Heart rate",
    native_unit_of_measurement="bpm",
    state_class=SensorStateClass.MEASUREMENT,
)


class BleWatchSensor(SensorEntity):
    """One BLE watch measurement, isolated from legacy Rogbid sensors."""

    _attr_has_entity_name = True

    def __init__(self, device_id: str, device_name: str, manufacturer: str,
                 model: str, description: SensorEntityDescription,
                 state: dict, signal: str):
        if not device_id.startswith("ble_"):
            raise ValueError("Expected a BLE device ID")
        self.entity_description = description
        self._state = state
        self._signal = signal
        self._attr_unique_id = f"{device_id}_{description.key}"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, device_id)},
            name=device_name,
            manufacturer=manufacturer,
            model=model,
        )

    @property
    def native_value(self):
        value = self._state.get(self.entity_description.key)
        if type(value) not in (int, float) or value < 0:
            return None
        return value

    async def async_added_to_hass(self):
        self.async_on_remove(
            async_dispatcher_connect(self.hass, self._signal, self.async_write_ha_state)
        )
