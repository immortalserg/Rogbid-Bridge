"""Pure-Python Bluetooth driver contract, independent of Home Assistant."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Iterable, Mapping


class DriverError(Exception):
    """A driver cannot interpret or communicate with the device."""


@dataclass(frozen=True)
class BleIdentity:
    """Bluetooth advertisement metadata supplied by the HA Bluetooth manager."""

    address: str
    name: str | None = None
    service_uuids: tuple[str, ...] = ()
    manufacturer_ids: tuple[int, ...] = ()
    connectable: bool = False


@dataclass(frozen=True)
class Measurement:
    """Normalized measurement; storage adapter handles persistence later."""

    kind: str
    measured_at: str
    record_id: str
    values: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.kind not in {"heart", "oxygen", "steps", "sleep"}:
            raise ValueError("Unsupported measurement kind")
        if not self.record_id or len(self.record_id) > 200:
            raise ValueError("Invalid record ID")
        if not isinstance(self.measured_at, str):
            raise ValueError("Invalid timestamp")
        try:
            datetime.fromisoformat(self.measured_at.replace("Z", "+00:00"))
        except ValueError as exc:
            raise ValueError("Invalid ISO-8601 timestamp") from exc


class BleDriver(ABC):
    """Implement protocol matching and parsing, not BLE scanning."""

    driver_id: str
    display_name: str

    @abstractmethod
    def matches(self, device: BleIdentity) -> bool:
        """Return True only when advertisement metadata reliably identifies device."""

    @abstractmethod
    def parse(self, payload: bytes) -> Iterable[Measurement]:
        """Decode a protocol-specific payload into normalized measurements."""


class DriverRegistry:
    """Explicit registry: no wildcard vendor probing or automatic connections."""

    def __init__(self) -> None:
        self._drivers: dict[str, BleDriver] = {}

    def register(self, driver: BleDriver) -> None:
        if not driver.driver_id or driver.driver_id in self._drivers:
            raise ValueError("Duplicate or empty driver ID")
        self._drivers[driver.driver_id] = driver

    def get(self, driver_id: str) -> BleDriver | None:
        return self._drivers.get(driver_id)

    def match(self, device: BleIdentity) -> list[BleDriver]:
        return [driver for driver in self._drivers.values() if driver.matches(device)]

    def all(self) -> tuple[BleDriver, ...]:
        return tuple(self._drivers.values())
