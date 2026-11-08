#!/usr/bin/env bash
# Builds FireKeys.java into the app's assets (src/main/assets/firekeys.dex), which FireTv copies to the Fire TV and
# runs there. Run it after changing FireKeys.java, then commit the dex with it. javac, then d8 from the newest
# build-tools.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
JAR="$SDK/platforms/android-34/android.jar"
D8=$(ls -d "$SDK"/build-tools/* | tail -1)/d8
[ -f "$D8" ] || D8="$D8.bat"
rm -rf build && mkdir -p build/classes
javac -nowarn -source 17 -target 17 -cp "$JAR" -d build/classes FireKeys.java 2>&1 | grep -v "^warning\|bootstrap" || true
# min-api 28: Fire OS 7 is Android 9.
"$D8" --min-api 28 --lib "$JAR" --output build build/classes/*.class
mv build/classes.dex ../src/main/assets/firekeys.dex
rm -rf build
echo "built src/main/assets/firekeys.dex"
