# Watch Bridge for Wear OS — 0.3.0-rc1

Samsung Galaxy Watch4 experimental release candidate.

## Available features
- On-watch heart rate and SpO₂ measurements and local history
- BIA and ECG measurement screens; ECG CSV export
- Step-counter delta journal (not authoritative Samsung Health daily totals)
- MQTT and BLE GATT transport for heart, oxygen and steps
- Samsung Health historical activity and sleep import: **not implemented yet**

## Transport setup
On watch: **Настройки → Настройки приложения → MQTT и Bluetooth**.
Enter broker hostname/IP, port, username/password, enable MQTT and save.
TLS is enabled by default (port 8883). Use a broker with a valid trusted certificate.
For local plain TCP, disable TLS and use port 1883. Do not expose plain MQTT to the internet.
The MQTT client is active only while the application process is running; background delivery and durable replay are not yet implemented.
MQTT credentials are stored in Android private application preferences, not in an encrypted credential store.

Topics use the compatibility prefix `rogbid/<device_id>/history/<kind>` with QoS 1.
Kinds: `heart`, `oxygen`, `steps`.
Journal JSON includes `kind`, `id`, `measured_at` and measurement fields.
Step event kcal and distance are placeholders (zero) until Samsung Health history integration.

BLE GATT service: `f3e9b8c0-4471-4a42-a14b-6f2f7a91c001`.
- `...c002`: legacy 12-byte SpO₂/pulse packet
- `...c003`: latest journal JSON, readable with GATT offsets; a single-byte notification signals updates.
BLE currently has **no guaranteed offline queue**, pairing/access control beyond the operating system, or Home Assistant-side receiver for this new JSON characteristic. A companion receiver must be implemented to ingest it.

## Release status
This is a **release candidate**, not a verified production release. Build via
[GitHub Actions](https://github.com/immortalserg/Watch-bridge/actions/workflows/wearos.yml)
and test the signed APK on Galaxy Watch4 before publishing a stable GitHub Release.

Next: investigate Samsung Health on-watch storage/provider and implement reliable background sync.
