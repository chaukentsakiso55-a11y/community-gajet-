# Community Gadget

Community Gadget is a native Android prototype for a dedicated, offline community emergency network. It uses nearby Bluetooth and Wi-Fi radios through Android Nearby Connections, forwards each trusted alert once, and does not require a cloud server, mobile data, airtime, Firebase, or user accounts.

## Core behaviour

| Button | Meaning | Location | Receiving-device behaviour |
| --- | --- | --- | --- |
| Green | Zone secure | Never collected or sent | Quiet confirmation and status card |
| Amber | Suspicious activity | Never collected or sent | Warning tone, vibration, and alert card |
| Red | Immediate emergency | Current GPS position and accuracy | Repeating alarm, vibration, urgent notification, and location card |

The privacy rule is enforced in both the UI and signed-message codec: a green or amber packet containing coordinates is rejected.

## What works in this prototype

- Kotlin and Jetpack Compose Android app
- Three large one-tap status controls
- Foreground offline listening service
- Nearby device discovery and automatic connections
- Multi-hop forwarding with alert-ID deduplication
- HMAC-SHA256 trusted-zone signatures derived from a shared community code
- Red emergency alarm that continues until locally acknowledged
- Emergency-only high-accuracy GPS collection
- Emergency coordinates, reported accuracy, terminal identity, and timestamp
- Android notification action for acknowledging an alarm
- No backend, analytics, advertisements, or continuous location tracking
- Unit tests for signatures, tampering, community isolation, and the location privacy rule

## Range: an important physical limit

An Android app cannot create multi-kilometre radio range using software alone. The phone-only build relays over Bluetooth and Wi-Fi between devices that are close enough to form a chain. Walls, terrain, radio conditions, phone models, and Android power management affect each hop.

Reliable long-range offline coverage requires fixed, powered relay stations or certified long-range radio accessories. See [docs/LONG_RANGE_OFFLINE_ARCHITECTURE.md](docs/LONG_RANGE_OFFLINE_ARCHITECTURE.md). The app's alert format and deduplication model are designed so a radio bridge can carry the same trusted alert without changing its meaning.

## Build in Android Studio

1. Open the `community-gadget` folder in Android Studio.
2. Use JDK 17.
3. Allow Gradle sync to complete.
4. Connect an Android 8.0 or newer phone.
5. Run the `app` configuration.

Command-line verification:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Test the offline relay

1. Install the app on at least two Android phones.
2. Enter a different terminal name on each phone.
3. Enter the same private community code on every trusted phone.
4. Grant nearby-device, notification, and precise-location permissions.
5. Switch off mobile data. Wi-Fi and Bluetooth radios must remain enabled because they are the offline transport.
6. Wait until each phone reports a nearby peer.
7. Press amber on one phone and confirm the others warn.
8. Press red and confirm the others ring and display the sender's GPS coordinates.
9. Press acknowledge on each receiving phone to silence its local alarm.

For a multi-hop field test, place a third phone between two phones that cannot directly connect. The middle phone must be able to reach both sides. Coverage is not guaranteed until the dedicated relay hardware is built and field-tested.

## Safety and privacy

- This is a prototype, not a certified emergency or security system.
- Do not rely on it as the only way to contact police, fire, ambulance, or other authorised services.
- Exact location is attached only to red emergency alerts.
- The app does not request background-location permission.
- Community members should use a strong, private zone code and change it if a device is lost.
- Acknowledging an alarm silences only the local phone; it does not erase the alert from other devices.
- Community response procedures should focus on warning, moving to safety, observing from a safe place, and contacting authorised services when available.

## Project structure

```text
app/src/main/java/za/co/cyberpulse/communitygadget/
├── alert/        alarm, vibration, notifications, acknowledge action
├── data/         terminal identity and derived community key
├── domain/       alert model, validation, signing, and verification
├── location/     emergency-only GPS acquisition
├── network/      Nearby Connections relay and foreground service
├── permissions/  Android runtime permission rules
└── ui/           Compose setup and three-button terminal screens
```

## Ownership

Community Gadget is a Cyber Pulse project based on the community-safety concept led by Mawela Nkoriso, with software development led by Ntsakiso Chauke (Darthwolf).
