# Rogbid Bridge

Independent Android smartwatch bridge for MQTT telemetry and historical activity synchronization. Not tied to Home Assistant.

> **Status:** Android 9 watch app, v0.6.0. Signed APK builds are produced by GitHub Actions.

## Target

- Rogbid Z10, Android 9 (device model `KING`)
- Kotlin Android app
- MQTT over Wi-Fi
- Consumers include Home Assistant, Node-RED and any MQTT subscriber

## Verified on the test watch via ADB

Current steps:

```sh
adb shell settings get system today_steps
```

Historical activity:

```sh
adb shell 'content query --uri content://com.watchHealth.Walk/query --sort "date DESC"'
```

The provider uses `content://com.watchHealth.Walk/query`, backed by SQLite `database.db` / `health`. Columns: `date`, `steps`, `diatance` (firmware spelling), `kcal`, `long_time`, `targetsteps`.

**Caveat:** Access from ADB shell does not establish access from an ordinary third-party APK. App permissions must be tested. On the tested watch, the activity history displays calories converted to rounded kilocalories and distance converted from metres to kilometres (one decimal place).

## Current features

- Pulse and SpO₂ measurements and local history
- Steps, calories (kcal), and distance (km) history
- Sleep archive, history, stage graph and diagnostic screen
- Manual and automatic MQTT synchronization, Home Assistant MQTT Discovery
- Nine color themes and eight interface languages: Russian, English, French, German, Luxembourgish, Italian, Portuguese, Spanish; automatic system language or manual selection

## MQTT topics

- `rogbid/<device_id>/state`: current readings
- `rogbid/<device_id>/history/steps`: dated activity records
- `rogbid/<device_id>/status`: availability

MQTT is a transport, not a historical database. Consumers must store history if needed; synchronization will include replay/deduplication.

## Roadmap

1. Kotlin Android project and GitHub Actions APK build
2. Test app access to system steps and activity provider
3. MQTT configuration, authentication, TLS and credential storage
4. Current data and historical synchronization, including reconnects
5. Investigate heart rate, SpO₂ and blood pressure data sources
6. Optional Home Assistant MQTT Discovery

## Security

Never commit broker credentials, signing keys or personal health data. Use MQTT TLS for untrusted networks.

## License

MIT.
