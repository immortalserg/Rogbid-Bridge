# Rogbid Bridge — Home Assistant integration (development)

**Status: initial scaffold; not ready for installation.**

The integration connects to the existing Home Assistant MQTT broker, subscribes to `rogbid/<device_id>/state`, and exposes live step, raw calorie and raw distance sensors. The released watch app v0.6.0 publishes only current state. The Android changes in this development branch now also replay up to 500 pulse/SpO₂ measurements, 100 daily step records and 500 archived sleep sessions over MQTT each sync. This is not released or tested on the watch yet.

## Journal protocol (planned)

Publish QoS 1 JSON on `rogbid/<device_id>/history/<kind>`, where `kind` is one of `heart`, `oxygen`, `steps`, `sleep`. Example:

```json
{"kind":"heart","id":"heart:123","measured_at":"2026-11-14T12:30:00+01:00","bpm":72}
```

- `id` must be stable across retries; duplicate messages are ignored.
- `measured_at` must include the original measurement date/time and timezone.
- The journal uses a separate SQLite database under Home Assistant `.storage/rogbid_bridge/`, not the Recorder schema.
- MQTT history messages should not be retained; the watch must replay missing history after reconnection.
- A first WebSocket history API and Lovelace journal card are implemented in this branch, but remain untested on HAOS.

## Next steps

1. Verify the Android history export on the watch and the MQTT payloads in HA.
2. Add cursor/ack synchronization to replace the current bounded full replay and avoid growing MQTT traffic.
3. Test the initial history WebSocket API, four-tab Lovelace card and sleep-stage timeline on HAOS.
4. Validate live heart, oxygen and sleep sensors, and prevent duplicate MQTT Discovery entities.
5. Test on HAOS and publish a HACS-compatible integration release.

## First test of the history card (development branch)

Copy the entire `custom_components/rogbid_bridge` directory to `/config/custom_components/rogbid_bridge` in Home Assistant, then restart HA. Add **Rogbid Bridge** under Settings → Devices & services and enter the MQTT device ID used by the watch.

Add a dashboard JavaScript resource with URL:

```
/rogbid_bridge/rogbid-history-card.js
```

Resource type: **JavaScript module**. Then add a manual card:

```yaml
type: custom:rogbid-history-card
device_id: king
title: Rogbid Z10 history
```

Replace `king` with your configured device ID. The four tabs display stored pulse, SpO₂, steps and sleep records. The card displays journal tables; click a sleep session to view its stage timeline (deep/light/other). If no data appears, verify that the updated Android app from this development branch is installed and that the broker receives `rogbid/<device_id>/history/#` messages. **Do not install as a production integration yet.**
