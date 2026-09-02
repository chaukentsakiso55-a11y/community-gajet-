# Long-range offline architecture

## Why the app cannot do this alone

Bluetooth and Wi-Fi hardware in ordinary phones has limited range. Software can forward messages across a chain of phones, but it cannot increase the physical range of a radio. A community with gaps between active phones needs dedicated relay stations.

## Recommended deployment layers

1. **Android emergency terminal** — the current app provides the buttons, GPS, alert verification, alarm, and nearby forwarding.
2. **Household base station** — an always-powered Android device or small controller keeps the local relay available.
3. **Long-range radio bridge** — a certified low-power radio accessory transfers signed alert packets between separated areas without internet.
4. **Fixed relay station** — elevated, battery-backed or solar-supported nodes fill coverage gaps.
5. **Optional service gateway** — a separately authorised gateway may contact emergency services when cellular or internet service exists; local alerts must continue without it.

## Bridge contract

The radio bridge should treat the Android packet as opaque bytes. It must not edit the signed JSON. Each packet contains:

- protocol version
- unique alert ID
- origin terminal ID and human-readable name
- green, amber, or red level
- creation time
- emergency-only coordinates and GPS accuracy
- HMAC signature

Relay nodes should forward each unique alert ID only once and discard invalid, oversized, or expired traffic. Android devices repeat the same rule, preventing loops when a message arrives over more than one route.

## Hardware requirements

- Certified radio equipment approved for the deployment country
- Correct locally permitted frequency, output power, bandwidth, and duty cycle
- Unique relay identity
- Battery backup and low-battery indication
- Weather-resistant enclosure for outdoor installations
- Tamper evidence
- Local alert cache with automatic expiry
- Watchdog and periodic self-test
- Physical placement and range survey before public use

The radio frequency and transmission settings must be selected with qualified local guidance and current ICASA requirements. They are deliberately not hard-coded into this software prototype.

## Privacy boundary

Green and amber packets must never contain position data. A radio bridge must reject any non-emergency packet containing latitude, longitude, or accuracy fields. Emergency coordinates should expire from relay caches after the community's agreed emergency-retention period.

## Field validation before real use

- Test every planned home-to-relay and relay-to-relay link.
- Repeat tests during power failures and bad weather.
- Verify that a red alert reaches every intended terminal.
- Measure end-to-end delay and record coverage gaps.
- Confirm that green and amber messages never contain GPS fields.
- Confirm that an incorrect community code cannot trigger a trusted alarm.
- Practise false-alarm cancellation and local acknowledgement.
- Maintain a separate procedure for contacting authorised emergency services.
