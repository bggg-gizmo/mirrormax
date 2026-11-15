# Android export integration

Use a disposable Android emulator or a dedicated test device. Build the MirrorMax app first (see the [build guide](../../docs/BUILDING.md)). Run build.sh in this directory with the same tool paths and signing environment as the app build. Then run, with Android platform-tools on PATH:

```bash
adb install -r ../../out/mirrormax.apk
adb install -r out/integration.apk
adb shell am instrument -w -r com.backgroundgremlin.mirrormax.instrumentation/com.backgroundgremlin.mirrormax.ExportIntegration | tee result.txt
```

Verify that the output ends in ANDROID EXPORT INTEGRATION PASSED. It opens each resulting MP4 through Android's MediaExtractor, checks H.264 and audio presence, and validates all files with Android's MediaStore. Files are intentionally left in the test device's Downloads/MirrorMax/Exports directory for examination.

For independent comparison on the build host, use an empty/clean test-device Exports folder before the test or pull just the eight newly reported files into the local verification directory. The script requires Python with Pillow and NumPy, and FFmpeg with ffprobe.

```bash
mkdir -p bridge/device-exports
adb pull /sdcard/Download/MirrorMax/Exports/. bridge/device-exports
python3 verify-exports.py
```
