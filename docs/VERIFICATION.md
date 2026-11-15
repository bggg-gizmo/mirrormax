# Release verification

**MirrorMax 1.0.1** was installed on an Android 11 emulator. The exact release APK is pinned in [SHA256SUMS](../dist/SHA256SUMS).

- [Android instrumentation results](../test/evidence/android-export-results.txt) record eight export cases and the files published to Downloads/MirrorMax/Exports.
- [Independent export checks](../test/evidence/independent-export-check.txt) check sample dimensions, timing, GIF looping, H.264 video, optional audio, and the two-pass reflection against a reference.
- The [editor screenshot](images/editor-android11.png) and [export screenshot](images/export-android11.png) are from the installed Android app. The screenshots show the controls and live preview. The instrumentation results cover the save and storage path separately.

These checks were performed on an emulator. They do not establish the behavior of every hardware codec or every MOV variant.
