"""Read-only Home Assistant Bluetooth discovery adapter.

Uses Home Assistant's shared scanner cache; never scans or connects itself.
"""
from __future__ import annotations

from dataclasses import dataclass

from .base import BleIdentity, DriverRegistry


@dataclass(frozen=True)
class DriverCandidate:
    """A potential driver match, not a verified supported device."""

    address: str
    name: str | None
    driver_id: str
    driver_name: str
    connectable: bool


def identity_from_service_info(info: object) -> BleIdentity:
    """Convert a Home Assistant BluetoothServiceInfoBleak-like object."""
    manufacturer_data = getattr(info, "manufacturer_data", {}) or {}
    return BleIdentity(
        address=str(getattr(info, "address")),
        name=getattr(info, "name", None),
        service_uuids=tuple(str(value).lower() for value in
                            (getattr(info, "service_uuids", ()) or ())),
        manufacturer_ids=tuple(sorted(int(value) for value in manufacturer_data)),
        connectable=bool(getattr(info, "connectable", False)),
    )


def find_candidates(service_infos: object, registry: DriverRegistry) -> list[DriverCandidate]:
    """Match cached advertisements without initiating Bluetooth connections."""
    results: list[DriverCandidate] = []
    seen: set[tuple[str, str]] = set()
    for info in service_infos:
        identity = identity_from_service_info(info)
        for driver in registry.match(identity):
            key = (identity.address, driver.driver_id)
            if key in seen:
                continue
            seen.add(key)
            results.append(
                DriverCandidate(
                    address=identity.address,
                    name=identity.name,
                    driver_id=driver.driver_id,
                    driver_name=driver.display_name,
                    connectable=identity.connectable,
                )
            )
    return results


def discovered_candidates(hass: object, registry: DriverRegistry) -> list[DriverCandidate]:
    """Inspect Home Assistant's BLE cache, including passive-only proxies.

    Import Home Assistant only when called; pure-Python tests remain runnable.
    """
    from homeassistant.components import bluetooth

    return find_candidates(
        bluetooth.async_discovered_service_info(hass, connectable=False),
        registry,
    )
