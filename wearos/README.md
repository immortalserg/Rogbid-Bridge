# Watch Bridge for Wear OS

Initial standalone Wear OS module for Galaxy Watch4 and compatible Wear OS
watches. Currently contains a launchable prototype screen and persistent
device identity. It **does not yet read health data or connect to MQTT**.

Build: `gradle :wearos:assembleDebug`

The module uses its own application ID (`org.watchbridge.wearos`) and does not
replace the existing Rogbid Z10 Android application. Common journal protocol:
[`shared/protocol/README.md`](../shared/protocol/README.md).

Next milestones: watch-specific health permissions and Health Services adapter;
Wi-Fi MQTT connection and settings; local journals and replay; UI localization;
testing on a real Galaxy Watch4.
