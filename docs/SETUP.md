# Hearth setup details

## Permissions Hearth uses on the TV

| Permission | What it's for | One-step ADB setup | Manual |
| --- | --- | --- | --- |
| Accessibility service "Hearth remote control" | Remote keys in other apps (d-pad needs Android 13+), Home button returns to Hearth, typing from the phone into search fields, knowing which app is open, Kids enforcement | ✓ | Settings › Accessibility (may be hidden on some TVs) |
| Notification access "Hearth now playing" | Title, artwork and position of what's playing (remote Now tab, Continue Watching) | ✓ | Often hidden on Google TV, so use ADB |
| Display over other apps | Doorbell pop-up over whatever is playing | ✓ | Settings › Apps › Special app access |
| Install unknown apps | Self-updates from GitHub | ✓ | Settings › Apps › Security & restrictions |
| READ_EPG_DATA | Google TV "Watch Next" in Continue Watching (where the TV allows it) | ✓ | ADB only |
| ADB (USB debugging, "Always allow") | Key presses on Android 12 and older, Menu/Power keys, silent updates | Press **Connect & grant everything** | — |

To run the one-step setup by hand from a computer:

```
adb connect <tv-ip>
adb shell settings put secure enabled_accessibility_services com.amosley.hearth.tv/com.amosley.hearth.tv.system.InputService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener com.amosley.hearth.tv/com.amosley.hearth.tv.system.MediaListener
adb shell appops set com.amosley.hearth.tv SYSTEM_ALERT_WINDOW allow
adb shell appops set com.amosley.hearth.tv REQUEST_INSTALL_PACKAGES allow
adb shell pm grant com.amosley.hearth.tv com.android.providers.tv.permission.READ_EPG_DATA
```

## Network

- The TV listens on port **8765** (HTTP + WebSocket), LAN only, and advertises itself over mDNS as `_hearth._tcp`.
- Every remote pairs once with a 6-digit code shown on the TV. Unpaired devices only get the pairing screen and app icons.
- Forget a device on the TV: Settings › Pair a remote.

## Home Assistant

- Uses the WebSocket API with a long-lived token stored on the TV. Phones and iPads never need the token; they go through the TV.
- Calls with no entity target the configured device for that domain (room lights, thermostat, lock, speaker). Hearth never sends an untargeted `light.turn_on`.
- Doorbell: set a `binary_sensor` (turns `on` when pressed) or an `event` entity. The pop-up shows the configured camera.
- Consoles: anything Home Assistant can call works as the wake action, for example:
  - PS5 (ps5-mqtt / PlayStation Network add-on): `switch.turn_on` on `switch.ps5_power`, or a `button.press`.
  - Xbox: `media_player.turn_on` on `media_player.xbox`. To launch a game, use `media_player.play_media` with data `{"media_content_type": "game", "media_content_id": "<product id>"}`.

## Media Library app integration

Hearth's Continue Watching and Recently Added rows read from an optional content provider exposed by the media library app:

- Authority: `com.hearth.medialibrary` (or `com.amosley.medialibrary`)
- Paths: `/continue_watching`, `/recently_added`
- Columns: `title`, `subtitle`, `progress` (0–1), `art_uri`, `intent_uri` (an `Intent.toUri(URI_INTENT_SCHEME)` that opens the item), `package`, `updated_at` (epoch ms)

Without it, Continue Watching comes from Hearth's own history: anything with a media session that was paused part-way through.

## Troubleshooting

- **Phone says "Keys only reach Hearth":** finish Settings › Remote control on the TV. Android 12 TVs need the ADB step for d-pad control of other apps.
- **Pressing Home opens Google TV:** check that *Replace Google TV home* is on and the accessibility service is enabled, or disable the stock launcher with ADB (see the README).
- **Update says "Install failed (signatures)":** the installed app was signed with a different key. Uninstall once, then install the release APK. From then on, updates install in place.
- **iPad can't connect:** use `http://`, not `https://`, and make sure the iPad is on the same network as the TV.
