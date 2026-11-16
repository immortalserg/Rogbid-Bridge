"""Reusable BLE session loop with bounded retries and cooperative shutdown."""
from __future__ import annotations

import asyncio
import logging

_LOGGER = logging.getLogger(__name__)


async def run_reconnecting_session(resolve_device, session_factory, on_measurement,
                                   *, retry_seconds=30, sleep=asyncio.sleep):
    """Retry missing devices and failed sessions; propagate cancellation.

    resolve_device: zero-argument callable returning a BLEDevice or None.
    session_factory: (device, callback) -> asynchronous context manager with
    wait_disconnected(). A disconnect triggers a fresh connection attempt.
    """
    if retry_seconds < 0:
        raise ValueError("retry_seconds must not be negative")
    while True:
        device = resolve_device()
        if device is not None:
            try:
                async with session_factory(device, on_measurement) as session:
                    await session.wait_disconnected()
            except asyncio.CancelledError:
                raise
            except Exception:
                _LOGGER.warning("BLE session failed; retrying", exc_info=True)
        await sleep(retry_seconds)
