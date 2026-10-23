#!/usr/bin/env bash
# Phone emulator: run the TV launcher and the phone remote on the same emulator, then
# discover, pair (reading the code from the debug log) and visit every tab.
set -x
mkdir -p shots
source .github/scripts/smoke-common.sh
adb install -r remote/build/outputs/apk/debug/remote-debug.apk
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
adb logcat -c
adb shell am start -W -n com.amosley.hearth.tv.debug/com.amosley.hearth.tv.MainActivity
sleep 8
adb shell am start -W -n com.amosley.hearth.remote.debug/com.amosley.hearth.remote.MainActivity
sleep 8
shot 01-connect
tap_text "Connect" || tap_text "Living Room TV"
sleep 3; shot 02-pairing
tap_text "Show code on TV"
sleep 3
CODE=$(adb logcat -d -s HearthPair | grep -oE 'code=[0-9]{6}' | tail -1 | cut -d= -f2)
echo "pair code: $CODE"
adb shell input text "$CODE"
sleep 5; shot 03-paired-remote
# Voice endpoint, using the token the phone just paired with.
TOKEN=$(adb shell run-as com.amosley.hearth.tv.debug cat files/hearth-config.json | grep -oE '"token":"[0-9a-f]+"' | head -1 | cut -d'"' -f4)
adb forward tcp:8765 tcp:8765
for q in "volume%2010" "open%20settings" "movie%20night" "end%20movie%20night" "play%20breaking%20bad"; do
  curl -s --max-time 15 "http://127.0.0.1:8765/api/voice?token=$TOKEN&q=$q" | tee -a shots/voice.txt; echo >> shots/voice.txt
done
adb shell am start -W -n com.amosley.hearth.remote.debug/com.amosley.hearth.remote.MainActivity; sleep 3
tap_text "Now"; sleep 2; shot 04-now
tap_text "Apps"; sleep 3; shot 05-apps
tap_text "Home"; sleep 2; shot 06-home
tap_text "Remote"; sleep 1
tap_text "⋯"; sleep 2; shot 07-settings
# Unfolded layout: widen the display like an open Fold
adb shell input keyevent 4; sleep 1
adb shell wm size 1812x2176; sleep 4; shot 08-unfolded
adb shell wm size reset
finish
