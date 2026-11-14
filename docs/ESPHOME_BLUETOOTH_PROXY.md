# Watch Bridge over ESPHome Bluetooth Proxy

## Goal

Use the **standard** ESPHome Bluetooth Proxy firmware. The ESP32 only forwards
BLE advertisement and GATT traffic to Home Assistant; it does not parse health
data, use MQTT or run custom Watch Bridge firmware.

```text
Galaxy Watch (Wear OS GATT server)
   -> BLE -> ESP32 / ESPHome Bluetooth Proxy
   -> ESPHome native API -> Home Assistant Bluetooth integration
   -> Watch Bridge GATT client -> existing SQLite history journal
```

## ESPHome example

Add the following to an existing, correctly configured ESP32 ESPHome device:

```yaml
esp32_ble_tracker:

bluetooth_proxy:
  active: true
```

Keep its existing `esp32:`, `wifi:` and `api:` sections. Do not add
`ble_client:` or a custom external component. Confirm the proxy is visible
under **Settings → Devices & services → Bluetooth** in Home Assistant.

Reference: https://esphome.io/components/bluetooth_proxy/

## BLE application contract (draft, not yet on the watch)

Custom service: `d312a001-7a7b-4e72-9e5b-a21b3471a001`
- REQUEST: `d312a002-7a7b-4e72-9e5b-a21b3471a001` — GATT write
- STREAM: `d312a003-7a7b-4e72-9e5b-a21b3471a001` — GATT notifications

The HA-side decoder lives in `custom_components/rogbid_bridge/ble_protocol.py`.
The planned client writes UTF-8 JSON `{"type":"sync","cursor":""}`, receives
`{"type":"record","payload":{...}}` messages containing existing history
records, then `{"type":"done","cursor":"..."}`. The cursor must advance only
after the records have been durably stored in Home Assistant.

BLE notifications have a small MTU. **Frame fragmentation, acknowledgement,
authentication and retry handling are not implemented yet**; the draft above
describes complete application messages, not raw notification boundaries.

## Implementation status

- [x] Standard ESPHome proxy configuration documented
- [x] UUIDs reserved and a standalone frame decoder with tests
- [ ] Wear OS GATT server and advertisements
- [ ] BLE fragmentation and acknowledgement protocol
- [ ] Home Assistant Bluetooth discovery and GATT client
- [ ] Secure device pairing / authorization
- [ ] Real-device testing on Galaxy Watch4 and ESP32

**This is a protocol scaffold, not an operational BLE synchronization path.**
The current Home Assistant integration continues to use MQTT unchanged. No
fabricated sleep, SpO2 or other unsupported data are generated.
