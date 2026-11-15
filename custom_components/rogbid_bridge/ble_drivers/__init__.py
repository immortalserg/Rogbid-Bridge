"""Vendor-independent BLE driver interfaces.

No automatic scanning or connection is performed by importing this package.
Drivers are opt-in and must be validated with hardware before being enabled.
"""
from .base import BleDriver, BleIdentity, Measurement, DriverRegistry, DriverError
from .registry import registry

__all__ = ["BleDriver", "BleIdentity", "Measurement", "DriverRegistry", "DriverError", "registry"]
