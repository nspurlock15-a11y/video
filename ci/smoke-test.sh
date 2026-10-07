#!/usr/bin/env bash
# Installs the APK on a running emulator, adds a folder of sample videos through the
# real system folder picker, plays an episode and enters picture-in-picture, failing
# if the app crashes at any point. Screenshots and logcat go to ci-out/.
set -uo pipefail
APK="$1"
PKG=com.localstream.app
OUT=ci-out
mkdir -p "$OUT"
HERE="$(cd "$(dirname "$0")" && pwd)"
ui() { python3 "$HERE/ui.py" "$@"; }
step=0
shot() { step=$((step + 1)); adb exec-out screencap -p > "$OUT/$(printf %02d $step)-$1.png"; }
check_alive() {
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then
    echo "::error::App crashed during: $1"
    adb logcat -d | grep -A 60 "FATAL EXCEPTION" | head -120
    finish 1
  fi
  if [ -z "$(adb shell pidof $PKG)" ]; then
    echo "::error::App process is gone after: $1"
    finish 1
  fi
  echo "OK: $1"
}
finish() {
  echo "---- app log ----"
  adb logcat -d | grep -E " (Library|AndroidRuntime|LocalStream|PlaybackService|ExoPlayerImpl)" | tail -80
  adb logcat -d > "$OUT/logcat.txt"
  adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null
  exit "$1"
}

# Sample library: a show with two seasons (plus a subtitle file) and a movie.
SRC=$(mktemp -d)
mk() { ffmpeg -loglevel error -y -f lavfi -i "testsrc=duration=$2:size=640x360:rate=24" \
  -f lavfi -i "sine=frequency=440:duration=$2" -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$SRC/$1"; }
mk s1e1.mkv 20; mk s1e2.mp4 8; mk s2e1.mp4 8; mk movie.mp4 8
printf '1\n00:00:01,000 --> 00:00:04,000\nHello subtitles\n' > "$SRC/s1e1.srt"
adb shell "mkdir -p '/sdcard/Movies/TV/Test Show/Season 1' '/sdcard/Movies/TV/Test Show/Season 2' '/sdcard/Movies/TV/Some Movie (2010)'"
adb push "$SRC/s1e1.mkv" "/sdcard/Movies/TV/Test Show/Season 1/Test.Show.S01E01.Pilot.720p.mkv"
adb push "$SRC/s1e1.srt" "/sdcard/Movies/TV/Test Show/Season 1/Test.Show.S01E01.Pilot.720p.srt"
adb push "$SRC/s1e2.mp4" "/sdcard/Movies/TV/Test Show/Season 1/Test.Show.S01E02.Second.mp4"
adb push "$SRC/s2e1.mp4" "/sdcard/Movies/TV/Test Show/Season 2/Test.Show.S02E01.mp4"
adb push "$SRC/movie.mp4" "/sdcard/Movies/TV/Some Movie (2010)/Some Movie (2010).mp4"

adb install -r "$APK" || finish 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 3
shot launched
check_alive "launch"

# Add the folder through the real system picker, like a user would.
ui tap "Add a folder" || finish 1
sleep 3
shot picker-opened
ui tap-optional "Show roots"
ui tap "sdk_gphone.*|Android SDK.*|Internal storage|Pixel.*" || { shot picker-roots; finish 1; }
ui tap "Movies" || { shot picker-movies; finish 1; }
shot picker-in-movies
ui tap "Use this folder" || { shot picker-use; finish 1; }
ui tap "Allow" || { shot picker-allow; finish 1; }
sleep 12
shot after-folder-added
check_alive "adding a folder"

ui wait "Test Show" || { shot no-show; finish 1; }
ui tap "Test Show" || finish 1
sleep 4
shot show-page
check_alive "opening the show page"

ui tap "Play S1:E1" || { shot no-play-button; finish 1; }
sleep 10
shot playing
check_alive "playing an episode"

adb shell input keyevent KEYCODE_HOME
sleep 5
shot home-pip
check_alive "going home (picture-in-picture)"

adb shell am start -W -n $PKG/.ui.MainActivity
sleep 4
shot back-in-app
check_alive "returning to the app"
echo "Smoke test passed"
finish 0
