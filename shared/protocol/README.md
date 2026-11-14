# Watch Bridge shared protocol (v1)

The existing Rogbid Z10 app is the compatibility reference. All new watch
clients must use the same Home Assistant integration and journal schema.

## Identity and topics

Each installation creates a stable, random, unique device ID; do not use the
model name as the identity. The existing topic prefix is retained:

- `rogbid/<device_id>/state` — current state JSON
- `rogbid/<device_id>/history/<kind>` — non-retained QoS 1 journal event
- `rogbid/<device_id>/status` — availability

The prefix `rogbid/` is **legacy protocol v1**; changing it would require
a coordinated migration in Home Assistant. Wear OS clients should initially
use the same prefix.

## Journal events

Required JSON keys: `kind`, `id`, `measured_at`.
Allowed kinds: `heart`, `oxygen`, `steps`, `sleep`.

Examples:

```json
{"kind":"heart","id":"heart:123","measured_at":"2026-11-14T12:30:00+01:00","bpm":72}
```

```json
{"kind":"oxygen","id":"oxygen:123","measured_at":"2026-11-14T12:30:00+01:00","spo2":98}
```

```json
{"kind":"steps","id":"steps:2026-11-14","measured_at":"2026-11-14T00:00:00+01:00","date":"2026-11-14","steps":3200,"kcal":150,"diatance":2400}
```

`diatance` is the existing legacy spelling; it is retained for compatibility.
The watch should publish stable IDs for retries and replay. Step totals can
be updated under the same ID during the day. The Home Assistant SQLite journal
deduplicates by `(device_id, kind, record_id)`.

## Compatibility

- Do not change `custom_components/rogbid_bridge` domain or existing Android
  package name; these are installation identities.
- The Home Assistant journal is independent of each watch's local database.
- Deleted Home Assistant records have tombstones to prevent replay re-import.
- Never store MQTT passwords, signing keys or health history in Git.
- Wear OS Health Services, permissions, MQTT configuration and history collection
  are future implementation tasks, **not implemented by this scaffold**.
