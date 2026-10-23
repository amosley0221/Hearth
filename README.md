# Hearth

An ad-free home screen for a TCL Google TV, a phone remote that becomes a live second screen, and an iPad remote, all built as one system.

| Part | What it is | Where it runs |
| --- | --- | --- |
| **Hearth** (`tv/`) | Launcher that replaces the Google TV home: Home, Library, Sports, Home control, Games, Kids profile, Modes. Also hosts the iPad remote and the remote API. | TCL Google TV |
| **Hearth Remote** (`remote/`) | Phone remote: d-pad, keyboard, apps, Home Assistant controls, live game stats / now playing. Cover-screen and unfolded layouts for the Galaxy Z Fold. | Android phone |
| **iPad remote** (`tv/src/main/assets/web/`) | Same remote as a web app, served by the TV at `http://<tv-ip>:8765`. Add it to the iPad Home Screen. Includes the full settings editor. | iPad / any browser |

The design comes from the Hearth design handoff (`design_handoff_hearth`): Ink/Paper/Amber palette, Figtree + Newsreader, 1920×1080 TV canvas.

## Install

Every release on the [Releases page](https://github.com/amosley0221/Hearth/releases) has two APKs and release notes:

- `hearth-tv-<version>.apk`: the TV launcher (includes the iPad remote)
- `hearth-remote-<version>.apk`: the phone remote

All releases are signed with the same key and have increasing version codes, so **every update installs over the previous version, with no uninstall.** Pairing, settings and teams are kept.

### Phone: GitHub installer (Obtainium)

1. In Obtainium tap **Add app** and paste `https://github.com/amosley0221/Hearth`.
2. Under **Filter APKs by regular expression**, enter `hearth-remote-.*\.apk`.
3. Save. Obtainium shows each release's notes and updates the remote in place.

To also track the TV APK (for example to send it to the TV), add the same URL a second time with the filter `hearth-tv-.*\.apk`.

### TV: first install

1. On the TV: **Settings › System › About**, then click **Android TV OS build** 7 times to unlock Developer options.
2. **Settings › System › Developer options**: turn on **USB debugging**.
3. Install `hearth-tv-<version>.apk` once, using any one of these:
   - From a computer: `adb connect <tv-ip>` then `adb install hearth-tv-<version>.apk`
   - The **Downloader** app on the TV: open `https://github.com/amosley0221/Hearth/releases/latest`
   - Send it with a "Send files to TV" app.
4. Open Hearth, go to **Settings › Remote control & permissions**, and press **Connect & grant everything**. Choose **Always allow** on the USB debugging prompt. This turns on everything Hearth uses in one step: remote keys, now playing, pop-ups over apps, Continue Watching, and silent updates.
5. Make Hearth the home screen. Hearth's **Replace Google TV home** option (on by default) brings you back to Hearth whenever Home is pressed. To remove the stock launcher completely instead, run this from a computer:
   `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`
   Undo it with `pm enable`.

### TV updates

The TV updates itself, with no uninstall. You can start an update from any of these:

- **On the TV:** Settings › Updates › Install.
- **From the phone:** Hearth Remote › ⋯ › Updates › **Update TV**. The TV downloads the release from GitHub and installs it.
- **From the iPad:** Settings › Updates › **Update TV now**.

With the one-step ADB setup, TV updates install silently, and overnight if *Install automatically* is on. Without it, Android shows its one-tap **Update** confirmation on the TV.

### Pair the phone and iPad

- **Phone:** open Hearth Remote. It finds the TV on your Wi-Fi. Tap it, then type the 6-digit code the TV shows.
- **iPad:** open `http://<tv-ip>:8765` in Safari. The TV shows the address in Settings › Pair a remote. Tap **Show code on TV**, type the code, then **Share › Add to Home Screen**.
- **Basic remote fallback (phone):** ⋯ › Basic remote pairs with the TV's built-in remote service (the same one the Google TV app uses). Keys, volume and power then keep working even when Hearth isn't running.

## Set up the rest

The settings editor is easiest from the iPad or a computer browser: `http://<tv-ip>:8765/#settings`.

- **Home Assistant:** URL plus a long-lived access token. Then pick the lights, thermostat, camera, lock, speaker, doorbell trigger, weather, people and scenes to show.
- **Consoles:** the HDMI input for each console, a Home Assistant action to wake it (for example `button.press` on a PS5 wake button, or `media_player.turn_on` on the Xbox), and a status entity.
- **Pinned games:** name, console, key art URL, and for Xbox a launch action (`media_player.play_media`). PS5 tiles honestly say "Opens PS5".
- **Modes:** an ordered list of actions (HA calls, open app, switch input, wake console, volume, profile, wait, silence pop-ups). They can also be edited on the TV under Settings › Modes.
- **Sports:** leagues, favourite teams (ESPN public data), and which app plays each league.
- **Kids:** approved apps, daily limit, bedtime. The parent PIN is set on the TV.

See [docs/SETUP.md](docs/SETUP.md) for details, permissions and troubleshooting.

## Releasing a new version

Releases are automatic. Add a new section at the **top** of `CHANGELOG.md` and push it to `main`:

```
## v1.1.0
- What changed
```

The **Release** workflow sees the new version, builds both APKs, signs them with the release key, creates the `v1.1.0` tag and publishes a GitHub Release with the APKs and that section as the notes. Obtainium and the in-app updaters pick it up from there. Pushes that don't add a new version publish nothing.

You can also start a release manually: **Actions › Release › Run workflow** and enter the version, or push a tag `vX.Y.Z`.

The version code comes from the version number (`major*1000000 + minor*1000 + patch`). The workflow refuses a version that isn't higher than the last release, so updates always install over the existing apps.

### Signing key (one-time)

The release workflow needs these repository secrets (**Settings › Secrets and variables › Actions**):

| Secret | Value |
| --- | --- |
| `HEARTH_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `HEARTH_KEYSTORE_PASSWORD` | keystore password |
| `HEARTH_KEY_ALIAS` | `hearth` |
| `HEARTH_KEY_PASSWORD` | key password |

Keep a backup of the keystore. If it's lost, new versions can't install over the existing apps.

## Building locally

Requirements: JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug        # debug builds install side by side (.debug package suffix)
./gradlew assembleRelease      # needs HEARTH_KEYSTORE_FILE / _PASSWORD / HEARTH_KEY_ALIAS / HEARTH_KEY_PASSWORD
```

## Known limits

These are Android and Google TV limitations, not bugs:

- Streaming video plays in each streaming app, never inside Hearth. Tiles hand off to the app, and some apps only open to their home screen.
- PS5 games can't be launched remotely; Hearth wakes the PS5 and switches input. Switch is input-only. Xbox launches go through Microsoft's cloud, so they take a few seconds.
- Remote d-pad control of *other* apps needs Android 13+ (accessibility) or the one-step ADB setup.
- Kid mode is the look plus light enforcement. Use Google TV's Family Link kids profile for real blocking.
- Google TV updates can occasionally reset the default home screen. Re-run the setup step if that happens.
