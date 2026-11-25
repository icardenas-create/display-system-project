#!/bin/bash
# Installs the Display System app on the Fire TV and turns off sleep and the screensaver.
# Run on a Mac on the same Wi-Fi as the TV:   bash setup-tv.sh
# Keep display-system.apk in the same folder as this script.
set -e
cd "$(dirname "$0")"

PACKAGE=com.displaysystem.app
TOOLS="$HOME/display-system-tools"

# 1. Get adb (Android's remote-control tool) if this Mac doesn't have it.
if command -v adb >/dev/null 2>&1; then
  ADB=adb
else
  ADB="$TOOLS/platform-tools/adb"
  if [ ! -x "$ADB" ]; then
    echo "Downloading adb from Google..."
    mkdir -p "$TOOLS"
    curl -fL -o "$TOOLS/platform-tools.zip" https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
    unzip -q -o "$TOOLS/platform-tools.zip" -d "$TOOLS"
  fi
fi

# 2. Connect to the TV.
read -r -p "TV IP address (Settings > My Fire TV > About > Network): " TV_IP
TARGET="$TV_IP:5555"
"$ADB" connect "$TARGET" || true
if [ "$("$ADB" -s "$TARGET" get-state 2>/dev/null)" != "device" ]; then
  echo
  echo "Not connected yet. Look at the TV: if it asks 'Allow USB debugging?', choose"
  echo "'Always allow from this computer' and OK. Then run this script again."
  echo "Also check: Settings > My Fire TV > Developer Options > ADB debugging is ON."
  exit 1
fi
SH() { "$ADB" -s "$TARGET" shell "$@"; }

# 3. Install or update the app.
if [ -f display-system.apk ]; then
  echo "Installing the app..."
  "$ADB" -s "$TARGET" install -r display-system.apk
else
  echo "display-system.apk is not next to this script. Skipping install."
fi

# 4. Let the app open itself when the TV turns on.
SH appops set $PACKAGE SYSTEM_ALERT_WINDOW allow

# 5. No screensaver, no sleep. The app also keeps the screen awake while it runs.
SH settings put secure screensaver_enabled 0
SH settings put system screen_off_timeout 2147483647
SH settings put global stay_on_while_plugged_in 7

# 6. App settings (blank answer keeps the current value).
echo
echo "Google Drive folder link. In Drive, share the folder as 'Anyone with the link: Viewer'"
read -r -p "and paste the link here: " FOLDER
read -r -p "Seconds per image (0 = static, stays on one image): " SECONDS_PER
read -r -p "Rotation: 90 = logo on right, 270 = logo on left, 0 = landscape: " ROTATION

ARGS=""
FOLDER="$(printf '%s' "$FOLDER" | tr -d "'\"\\\\ ")"
[ -n "$FOLDER" ] && ARGS="$ARGS --es folder '$FOLDER'"
case "$SECONDS_PER" in ''|*[!0-9]*) ;; *) ARGS="$ARGS --ei seconds $SECONDS_PER" ;; esac
case "$ROTATION" in 0|90|180|270) ARGS="$ARGS --ei rotation $ROTATION" ;; esac

# 7. Open the display.
SH "am start -n $PACKAGE/.MainActivity $ARGS"
echo
echo "Done. The display should be open on the TV."
