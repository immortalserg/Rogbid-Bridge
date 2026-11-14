# Watch Bridge for Wear OS

Samsung Galaxy Watch4 and compatible Wear OS watches.

## Implemented (beta, device testing pending)

- Heart rate: Wear OS Health Services MeasureClient and passive samples
- Daily steps, active calories and distance: passive DAILY aggregates
- Local SQLite event journal, stable event IDs, periodic MQTT sync
- MQTT credentials and explicit Start/Stop controls on watch
- Same `rogbid/<device_id>/history/<kind>` protocol as the Rogbid Z10 app
- No phone or BLE proxy required; Wi-Fi/network access to the broker is required

## Not implemented — placeholders

- Sleep stages and history
- Blood oxygen (SpO₂)
- Floors climbed
- Historical backfill from Samsung Health
- Full shared settings, language translations and advanced charts

**Placeholders do not create fictitious values or publish MQTT measurements.**

## Security and technical limitations

Use `ssl://broker:8883` (TLS), trusted certificates and a dedicated MQTT account. Plain
`tcp://` is not allowed for production deployments and must be enabled deliberately
if needed. The prototype currently stores MQTT credentials in app preferences; avoid
using a privileged MQTT account. Do not expose unencrypted MQTT outside a trusted LAN.

The health service is started explicitly by the user. Heart-rate MeasureClient polling
can use substantial battery; continuous tracking should be evaluated on hardware.
Daily aggregates can vary across manufacturers, watch firmware and Health Services
availability. No claim is made that this app can access Samsung Health history.

## Build and installation

```bash
gradle :wearos:assembleDebug
adb install -r wearos/build/outputs/apk/debug/wearos-debug.apk
```

Wear OS APK uses application ID `org.watchbridge.wearos`; it does not overwrite
the Rogbid Z10 app. See [shared protocol](../shared/protocol/README.md).

## Attribution

Health Services API choice and Watch4 DAILY totals behavior informed by the MIT
licensed project [galaxy-health-bridge](https://github.com/piyush140104/galaxy-health-bridge).
The MQTT integration and implementation here are independent.
