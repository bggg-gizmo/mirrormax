#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${MIRRORMAX_JAVA_HOME:-}" ]]; then export PATH="$MIRRORMAX_JAVA_HOME/bin:$PATH"; fi
mkdir -p test/fixtures test/classes
python3 test/interop.py make
javac -d test/classes src/com/backgroundgremlin/mirrormax/{Mirror,Gif}.java test/Checks.java
java -cp test/classes com.backgroundgremlin.mirrormax.Checks test/fixtures
python3 test/interop.py verify
