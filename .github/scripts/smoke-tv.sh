#!/usr/bin/env bash
# Android TV emulator: install Hearth, make it home, walk through the sections.
set -x
mkdir -p shots
PKG=com.amosley.hearth.tv.debug
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
adb logcat -c
adb shell cmd package set-home-activity $PKG/com.amosley.hearth.tv.MainActivity || true
adb shell am start -W -n $PKG/com.amosley.hearth.tv.MainActivity
sleep 12
shot() { adb exec-out screencap -p > "shots/$1.png"; }
key() { adb shell input keyevent "$@"; sleep 1.2; }
shot 01-home
key 20; key 20; shot 02-home-scrolled
key 4; key 4; sleep 1
# Rail: open it and visit each section
key 21; key 21; key 21; shot 03-rail-open
for i in 1 2 3 4; do key 20; shot "04-section-$i"; done
key 20; key 20; key 20; key 20; key 20; shot 05-settings
key 22; shot 06-settings-focus
# Server + web remote
adb forward tcp:8765 tcp:8765
curl -s --max-time 5 http://127.0.0.1:8765/api/info | tee shots/api-info.json
curl -s --max-time 5 -o shots/web-index.html -w "%{http_code}\n" http://127.0.0.1:8765/
# Kids profile + PIN pad via the profile picker
key 3; sleep 2; shot 07-after-home-key
adb logcat -d > shots/logcat.txt
grep -A 30 "FATAL EXCEPTION" shots/logcat.txt > shots/crash.txt || rm -f shots/crash.txt
grep -E "Hearth|AndroidRuntime" shots/logcat.txt | tail -n 200 > shots/logcat-hearth.txt
[ ! -f shots/crash.txt ]
