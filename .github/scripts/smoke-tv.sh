#!/usr/bin/env bash
# Android TV emulator: install Hearth, make it home, walk through the sections.
set -x
mkdir -p shots
source .github/scripts/smoke-common.sh
PKG=com.amosley.hearth.tv.debug
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
adb logcat -c
adb shell cmd package set-home-activity $PKG/com.amosley.hearth.tv.MainActivity || true
adb shell am start -W -n $PKG/com.amosley.hearth.tv.MainActivity
sleep 15
shot 01-home
key 20; key 20; shot 02-home-scrolled
key 4; key 4; sleep 1
key 21; key 21; key 21; shot 03-rail-open
for s in library sports smart games; do key 20; sleep 2; shot "04-$s"; done
key 22; sleep 1; shot 05-games-focus
key 21; key 21; key 21; key 20; key 20; key 20; sleep 1; shot 06-settings
key 22; key 23; sleep 2; shot 07-settings-page
key 4; key 4; key 4; sleep 1
# Long-press OK on a dock app: tile options
key 3; sleep 2; key --longpress 23; sleep 2; shot 08-tile-options
key 4
adb forward tcp:8765 tcp:8765
curl -s --max-time 5 http://127.0.0.1:8765/api/info | tee shots/api-info.json
curl -s --max-time 5 -o /dev/null -w "web %{http_code}\n" http://127.0.0.1:8765/ | tee shots/web-status.txt
finish
