"""Fake sessions test reconnects, failure recovery and cancellation."""
import asyncio
import importlib
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1] / "custom_components" / "rogbid_bridge" / "ble_drivers"
PKG = "_watch_ble_reconnect_test"
pkg = types.ModuleType(PKG)
pkg.__path__ = [str(ROOT)]
sys.modules[PKG] = pkg
reconnect = importlib.import_module(PKG + ".reconnect")


class FakeSession:
    def __init__(self, fail=False):
        self.fail = fail
        self.closed = False

    async def __aenter__(self):
        if self.fail:
            raise ConnectionError("test disconnect")
        return self

    async def wait_disconnected(self):
        return

    async def __aexit__(self, *args):
        self.closed = True


class ReconnectTests(unittest.IsolatedAsyncioTestCase):
    async def test_disconnect_reconnects_and_cleans_up(self):
        sessions = []
        def factory(device, callback):
            session = FakeSession()
            sessions.append(session)
            return session

        async def sleep(_delay):
            if len(sessions) == 2:
                raise asyncio.CancelledError

        with self.assertRaises(asyncio.CancelledError):
            await reconnect.run_reconnecting_session(
                lambda: "fake-device", factory, lambda _: None,
                retry_seconds=0, sleep=sleep,
            )
        self.assertEqual(len(sessions), 2)
        self.assertTrue(all(s.closed for s in sessions))

    async def test_connection_failure_is_retried(self):
        attempts = []
        def factory(device, callback):
            attempts.append(device)
            return FakeSession(fail=len(attempts) == 1)

        async def sleep(_delay):
            if len(attempts) == 2:
                raise asyncio.CancelledError

        with self.assertRaises(asyncio.CancelledError):
            await reconnect.run_reconnecting_session(
                lambda: "fake-device", factory, lambda _: None,
                retry_seconds=0, sleep=sleep,
            )
        self.assertEqual(len(attempts), 2)

    async def test_unavailable_device_retries_without_connecting(self):
        calls = []
        async def sleep(_delay):
            raise asyncio.CancelledError
        with self.assertRaises(asyncio.CancelledError):
            await reconnect.run_reconnecting_session(
                lambda: None, lambda *a: calls.append(a), lambda _: None,
                retry_seconds=0, sleep=sleep,
            )
        self.assertEqual(calls, [])

    async def test_cancellation_during_session_is_not_swallowed(self):
        class CancelSession(FakeSession):
            async def wait_disconnected(self):
                raise asyncio.CancelledError
        session = CancelSession()
        with self.assertRaises(asyncio.CancelledError):
            await reconnect.run_reconnecting_session(
                lambda: "fake", lambda *args: session, lambda _: None,
                retry_seconds=0,
            )
        self.assertTrue(session.closed)

    async def test_invalid_retry_delay(self):
        with self.assertRaises(ValueError):
            await reconnect.run_reconnecting_session(
                lambda: None, lambda *a: None, lambda _: None,
                retry_seconds=-1,
            )


if __name__ == "__main__":
    unittest.main()
