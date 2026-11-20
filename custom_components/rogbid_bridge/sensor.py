"""Live watch metrics. Historical measurements are stored separately."""
from homeassistant.components.sensor import (
    SensorEntity,
    SensorEntityDescription,
    SensorStateClass,
)
from datetime import timedelta
import json
import logging
from homeassistant.util import dt as dt_util
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from .const import DOMAIN, CONF_DEVICE_ID

_LOGGER = logging.getLogger(__name__)

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
    runtime = hass.data[DOMAIN][entry.entry_id]
    if runtime.get("ble"):
        from .ble_drivers.entities import BLE_HEART_RATE, BleWatchSensor
        async_add_entities([
            BleWatchSensor(
                entry.data[CONF_DEVICE_ID],
                entry.data.get("ble_name", "BLE heart-rate device"),
                "Bluetooth SIG",
                "Heart Rate Service",
                BLE_HEART_RATE,
                runtime["state"],
                f"{DOMAIN}_{entry.entry_id}_update",
            )
        ], True)
        return
    entities = [
        RogbidSensor(entry, description, runtime["state"])
        for description in SENSORS
    ]
    for metric, unit in (("steps", "steps"), ("kcal", "kcal"), ("diatance", "km")):
        for days in (7, 30, 365):
            entities.append(RogbidTotalSensor(entry, runtime["journal"], metric, unit, days))
    entities.append(RogbidLastSleepSensor(entry, runtime["journal"]))
    async_add_entities(entities, True)

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

class RogbidTotalSensor(SensorEntity):
    """Rolling daily totals from the watch's durable SQLite history."""
    _attr_has_entity_name = True
    _attr_state_class = SensorStateClass.MEASUREMENT

    def __init__(self, entry, journal, metric, unit, days):
        self._journal = journal
        self._device_id = entry.data[CONF_DEVICE_ID]
        self._metric = metric
        self._days = days
        self._attr_name = f"{'Distance' if metric == 'diatance' else 'Calories' if metric == 'kcal' else 'Steps'} last {days} days"
        self._attr_unique_id = f"{self._device_id}_{metric}_last_{days}_days"
        self._attr_native_unit_of_measurement = unit
        self._attr_native_value = None
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=f"Rogbid {self._device_id}",
            manufacturer="Rogbid",
            model="Z10",
        )
        self._signal = f"{DOMAIN}_{entry.entry_id}_update"

    async def async_update(self):
        end = dt_util.now().date()
        start = end - timedelta(days=self._days - 1)
        try:
            totals = await self.hass.async_add_executor_job(
                self._journal.step_totals, self._device_id,
                start.isoformat(), end.isoformat(),
            )
            value = totals[self._metric]
            if self._metric in ("kcal", "diatance"):
                value /= 1000
            self._attr_native_value = round(value, 1) if self._metric == "diatance" else round(value)
            self._attr_available = True
        except Exception:
            _LOGGER.exception("Failed to read Rogbid history totals for %s", self._device_id)
            self._attr_available = False

    async def async_added_to_hass(self):
        self.async_on_remove(async_dispatcher_connect(
            self.hass, self._signal, self._schedule_refresh
        ))
        self.async_on_remove(async_track_time_interval(
            self.hass, self._scheduled_refresh, timedelta(minutes=5)
        ))

    def _schedule_refresh(self):
        self.hass.async_create_task(self._refresh())

    async def _scheduled_refresh(self, _now):
        await self._refresh()

    async def _refresh(self):
        await self.async_update()
        self.async_write_ha_state()

class RogbidLastSleepSensor(SensorEntity):
    """Latest sleep timeline exposed in one structured HA attribute."""
    _attr_has_entity_name = True
    _attr_name = "Last sleep details"
    _attr_icon = "mdi:sleep"

    def __init__(self, entry, journal):
        self._journal = journal
        self._device_id = entry.data[CONF_DEVICE_ID]
        self._attr_unique_id = f"{self._device_id}_last_sleep_details"
        self._attr_native_value = None
        self._attr_extra_state_attributes = {}
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, self._device_id)},
            name=f"Rogbid {self._device_id}",
            manufacturer="Rogbid",
            model="Z10",
        )
        self._signal = f"{DOMAIN}_{entry.entry_id}_update"

    async def async_update(self):
        try:
            rows = await self.hass.async_add_executor_job(
                self._journal.recent, self._device_id, "sleep", 1
            )
            if not rows:
                self._attr_native_value = None
                self._attr_extra_state_attributes = {}
                return
            record = rows[0]
            stages = record.get("stages_json", record.get("sleep_stages", []))
            if isinstance(stages, str):
                stages = json.loads(stages)
            if not isinstance(stages, list):
                stages = []
            details = {
                "sleep_date": record.get("sleep_date", record.get("measured_at", "")[:10]),
                "start_time": record.get("start_time", record.get("sleep_start")),
                "end_time": record.get("end_time", record.get("sleep_end")),
                "stages": stages,
            }
            # HA states have a 255-character limit; keep the timeline in one attribute.
            self._attr_native_value = details["sleep_date"] or "unknown"
            self._attr_extra_state_attributes = {"sleep_json": details}
            self._attr_available = True
        except Exception:
            _LOGGER.exception("Failed to read latest Rogbid sleep record")
            self._attr_available = False

    async def async_added_to_hass(self):
        self.async_on_remove(async_dispatcher_connect(
            self.hass, self._signal, self._schedule_refresh
        ))
        self.async_on_remove(async_track_time_interval(
            self.hass, self._scheduled_refresh, timedelta(minutes=5)
        ))

    def _schedule_refresh(self):
        self.hass.async_create_task(self._refresh())

    async def _scheduled_refresh(self, _now):
        await self._refresh()

    async def _refresh(self):
        await self.async_update()
        self.async_write_ha_state()
