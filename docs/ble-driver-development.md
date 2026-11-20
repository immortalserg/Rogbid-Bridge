# Bluetooth driver foundation (experimental)

This is a **non-operational foundation** for future multi-vendor watch support. No additional watch models are supported yet.

## Scope

- Pure-Python `BleDriver`, `BleIdentity`, `Measurement`, and `DriverRegistry`.
- Explicit opt-in registration, no wildcard matching, no automatic scanning or GATT connections.
- No changes to existing Rogbid Z10 MQTT/GATT behavior, SQLite schema, entities or configuration flow.
- Synthetic tests that run without Home Assistant, Bluetooth hardware or third-party libraries.

## Running tests

```sh
python -m unittest discover -s tests -p 'test_ble_driver_foundation.py'
```

## Next development steps

1. Select a **specific model** and document its advertising identifiers and protocol.
2. Create protocol fixtures from public specifications or properly authorized traces, with sensitive identifiers removed.
3. Implement matching and decoding with tests for malformed packets, duplicates, timestamps and data ranges.
4. Add a separate Home Assistant transport adapter using the built-in Bluetooth manager and a connectable `BLEDevice`; do not start a separate scanner.
5. Implement pairing/authentication, GATT retry and timeouts if required by the model.
6. Wire normalized records into the existing journal, only after explicit configuration.
7. Mark model support as experimental until validated by someone with physical hardware.

## Gadgetbridge

Gadgetbridge's code is AGPL-3.0 licensed. This foundation is original and does not copy Gadgetbridge code. Review license obligations before copying or adapting any protected implementation. Use publicly documented protocols or independent implementation where appropriate.

## Limitations

Synthetic tests validate software contracts, not pairing, GATT interoperability, real-device measurements or BLE proxy support.
