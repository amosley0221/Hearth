# Helpers for the emulator smoke tests.
shot() { adb exec-out screencap -p > "shots/$1.png"; }
key() { adb shell input keyevent "$@"; sleep 1.2; }
# Taps the centre of the first on-screen element whose text/description matches $1.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -E "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+')
  if [ -z "$b" ]; then echo "tap_text: '$1' not found"; return 1; fi
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
}
finish() {
  adb logcat -d > shots/logcat.txt
  grep -B2 -A30 "Process: com.amosley" shots/logcat.txt > shots/crash.txt || rm -f shots/crash.txt
  grep -E "Hearth|com.amosley" shots/logcat.txt | tail -n 300 > shots/logcat-hearth.txt
  [ ! -f shots/crash.txt ]
}
