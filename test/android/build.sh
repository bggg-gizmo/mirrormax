#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
APP="$(cd ../.. && pwd)"
if [[ -n "${MIRRORMAX_JAVA_HOME:-}" ]]; then export JAVA_HOME="$MIRRORMAX_JAVA_HOME"; export PATH="$JAVA_HOME/bin:$PATH"; fi
B="${MIRRORMAX_BUILD_TOOLS:-$APP/../tool/android-16}"
A="${MIRRORMAX_ANDROID_JAR:-$APP/../tool/android-36/android.jar}"
K="${MIRRORMAX_KEYSTORE:-$APP/local/release.p12}"
P="${MIRRORMAX_KEYPASS_FILE:-$APP/local/keypass}"
mkdir -p out/classes out/dex
javac --release 11 -cp "$A:$APP/out/classes.jar" -d out/classes src/com/backgroundgremlin/mirrormax/ExportIntegration.java
jar --create --file out/test.jar -C out/classes .
"$B/d8" --min-api 29 --lib "$A" --classpath "$APP/out/classes.jar" --output out/dex out/test.jar
"$B/aapt2" link -I "$A" --manifest Manifest.xml -A assets -o out/unaligned.apk
(cd out/dex && zip -q -X ../unaligned.apk classes.dex)
"$B/zipalign" -f -p 4 out/unaligned.apk out/aligned.apk
"$B/apksigner" sign --v4-signing-enabled false --ks "$K" --ks-pass "file:$P" --ks-key-alias "${MIRRORMAX_KEY_ALIAS:-mirrormax}" --out out/integration.apk out/aligned.apk
