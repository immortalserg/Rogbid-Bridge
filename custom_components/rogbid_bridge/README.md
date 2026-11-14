# Rogbid Bridge — Home Assistant integration (development)

**Status: initial scaffold; not ready for installation.**

The integration connects to the existing Home Assistant MQTT broker, subscribes to `rogbid/<device_id>/state`, and exposes live step, raw calorie and raw distance sensors. The watch's current v0.6.0 firmware-side app publishes **only the current state**, not complete historical records. Historical journal sync therefore requires a follow-up update to the Android app.

## Journal protocol (planned)

Publish QoS 1 JSON on `rogbid/<device_id>/history/<kind>`, where `kind` is one of `heart`, `oxygen`, `steps`, `sleep`. Example:

```json
{"kind":"heart","id":"heart:123","measured_at":"2026-11-14T12:30:00+01:00","bpm":72}
```

- `id` must be stable across retries; duplicate messages are ignored.
- `measured_at` must include the original measurement date/time and timezone.
- The journal uses a separate SQLite database under Home Assistant `.storage/rogbid_bridge/`, not the Recorder schema.
- MQTT history messages should not be retained; the watch must replay missing history after reconnection.
- The journal's UI and history API are not implemented yet.

## Next steps

1. Add Android export for heart, oxygen, daily steps and sleep history with stable IDs and original timestamps.
2. Add cursor/ack synchronization and safe replay after MQTT reconnect.
3. Add Home Assistant history API and Lovelace card for four journals and sleep-stage chart.
4. Add correct units and live heart, oxygen and sleep sensors.
5. Test on HAOS and publish a HACS-compatible integration release.
