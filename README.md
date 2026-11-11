# Rogbid Bridge

Independent Android smartwatch bridge for MQTT telemetry and historical activity synchronization. Not tied to Home Assistant.

> **Status:** Early development; no APK released yet.

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

**Caveat:** Access from ADB shell does not establish access from an ordinary third-party APK. App permissions must be tested. Distance and calorie units remain unverified.

## Proposed MQTT topics

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
