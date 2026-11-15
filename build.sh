#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
ROOT="$PWD"
JAVA_HOME="${MIRRORMAX_JAVA_HOME:-${JAVA_HOME:-$ROOT/../java}}"
if [[ -x "$JAVA_HOME/bin/javac" ]]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
B="${MIRRORMAX_BUILD_TOOLS:-$ROOT/../tool/android-16}"
P="${MIRRORMAX_ANDROID_JAR:-$ROOT/../tool/android-36/android.jar}"
for tool in javac jar keytool zip openssl; do command -v "$tool" >/dev/null || { printf 'Missing command %s\n' "$tool" >&2; exit 1; }; done
for tool in aapt2 d8 zipalign apksigner; do [[ -x "$B/$tool" ]] || { printf 'Set MIRRORMAX_BUILD_TOOLS to the directory containing %s.\n' "$tool" >&2; exit 1; }; done
[[ -f "$P" ]] || { printf 'Set MIRRORMAX_ANDROID_JAR to the Android API 36 android.jar.\n' >&2; exit 1; }
mkdir -p out local
rm -rf out/classes out/dex out/generated
mkdir -p out/classes out/dex out/generated
"$B/aapt2" compile --dir res -o out/resources.zip
"$B/aapt2" link -I "$P" --manifest AndroidManifest.xml --java out/generated -A assets -o out/base.apk out/resources.zip
find src out/generated -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 --release 11 -classpath "$P" -d out/classes
jar --create --file out/classes.jar -C out/classes .
"$B/d8" --release --min-api 29 --lib "$P" --output out/dex out/classes.jar
cp out/base.apk out/unaligned.apk
(cd out/dex && zip -q -X ../unaligned.apk classes.dex)
"$B/zipalign" -f -p 4 out/unaligned.apk out/aligned.apk
umask 077
KEY="${MIRRORMAX_KEYSTORE:-$ROOT/local/release.p12}"
PASS="${MIRRORMAX_KEYPASS_FILE:-$ROOT/local/keypass}"
ALIAS="${MIRRORMAX_KEY_ALIAS:-mirrormax}"
if [[ ! -s "$KEY" ]]; then
  [[ -z "${MIRRORMAX_KEYSTORE:-}" ]] || { printf 'Specified keystore does not exist.\n' >&2; exit 1; }
  if [[ ! -s "$PASS" ]]; then openssl rand -hex 24 > "$PASS"; fi
  keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass:file "$PASS" -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MirrorMax, O=Background Gremlin Group'
fi
[[ -s "$PASS" ]] || { printf 'The keystore password file is missing.\n' >&2; exit 1; }
"$B/apksigner" sign --ks "$KEY" --ks-key-alias "$ALIAS" --ks-pass "file:$PASS" --v4-signing-enabled false --out out/mirrormax.apk out/aligned.apk
"$B/apksigner" verify --min-sdk-version 29 --max-sdk-version 36 --verbose --print-certs out/mirrormax.apk
"$B/zipalign" -c -p 4 out/mirrormax.apk
