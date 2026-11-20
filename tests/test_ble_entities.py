"""Validate BLE entity metadata without a running Home Assistant instance."""
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge"
PKG = "_watch_ble_entities_test"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg

const = types.ModuleType(PKG + ".const")
const.DOMAIN = "rogbid_bridge"
sys.modules[const.__name__] = const

ha = types.ModuleType("homeassistant")
ha.__path__ = []
components = types.ModuleType("homeassistant.components")
components.__path__ = []
sensor = types.ModuleType("homeassistant.components.sensor")
helpers = types.ModuleType("homeassistant.helpers")
helpers.__path__ = []
dispatcher = types.ModuleType("homeassistant.helpers.dispatcher")
entity = types.ModuleType("homeassistant.helpers.entity")

class SensorEntity:
    pass

class SensorEntityDescription:
    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)

class SensorStateClass:
    MEASUREMENT = "measurement"

class DeviceInfo(dict):
    pass

sensor.SensorEntity = SensorEntity
sensor.SensorEntityDescription = SensorEntityDescription
sensor.SensorStateClass = SensorStateClass
dispatcher.async_dispatcher_connect = lambda *args: lambda: None
entity.DeviceInfo = DeviceInfo

for module in (ha, components, sensor, helpers, dispatcher, entity):
    sys.modules[module.__name__] = module

entities = importlib.import_module(PKG + ".ble_drivers.entities")


class EntityTests(unittest.TestCase):
    def test_separate_device_identity_and_unique_id(self):
        state = {"heart_rate": 72}
        obj = entities.BleWatchSensor("ble_test123", "Test BLE watch",
                                      "Fixture", "Mock", entities.BLE_HEART_RATE,
                                      state, "test_signal")
        self.assertEqual(obj._attr_unique_id, "ble_test123_heart_rate")
        self.assertEqual(obj._attr_device_info["identifiers"],
                         {("rogbid_bridge", "ble_test123")})
        self.assertEqual(obj.native_value, 72)

    def test_invalid_state(self):
        obj = entities.BleWatchSensor("ble_test123", "Test", "Fixture", "Mock",
                                      entities.BLE_HEART_RATE, {"heart_rate": "72"}, "signal")
        self.assertIsNone(obj.native_value)

    def test_legacy_id_rejected(self):
        with self.assertRaises(ValueError):
            entities.BleWatchSensor("z10_existing", "Test", "Fixture", "Mock",
                                    entities.BLE_HEART_RATE, {}, "signal")


if __name__ == "__main__":
    unittest.main()
