"""Experimental BLE driver registry.

Generic Bluetooth SIG heart-rate support is a protocol candidate only.
No specific watch models have been hardware verified.
"""
from .base import DriverRegistry
from .standard_heart_rate import StandardHeartRateDriver

registry = DriverRegistry()
registry.register(StandardHeartRateDriver())
