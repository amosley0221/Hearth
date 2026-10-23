#!/usr/bin/env bash
# Phone emulator: install Hearth Remote; run the TV launcher on the same emulator so the phone
# can discover and pair with it over the loopback network.
set -x
mkdir -p shots
adb install -r remote/build/outputs/apk/debug/remote-debug.apk
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
adb logcat -c
shot() { adb exec-out screencap -p > "shots/$1.png"; }
adb shell am start -W -n com.amosley.hearth.remote.debug/com.amosley.hearth.remote.MainActivity
sleep 8
shot 01-connect
# Start the TV app in the background so its server runs, then point the phone at it.
adb shell am start -W -n com.amosley.hearth.tv.debug/com.amosley.hearth.tv.MainActivity
sleep 6
shot 02-tv-on-phone
adb shell am start -W -n com.amosley.hearth.remote.debug/com.amosley.hearth.remote.MainActivity
sleep 6
shot 03-discovered
adb logcat -d > shots/logcat.txt
grep -A 30 "FATAL EXCEPTION" shots/logcat.txt > shots/crash.txt || rm -f shots/crash.txt
[ ! -f shots/crash.txt ]
