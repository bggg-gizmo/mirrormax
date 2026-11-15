# Build MirrorMax

The APK installs without build tools. The source build uses a Linux shell, OpenJDK 17, an Android API 36 platform `android.jar`, Android build-tools with `aapt2`, `d8`, `zipalign` and `apksigner`, plus Zip and OpenSSL. No Gradle is used.

Set these paths to your locally installed tools and run from the repository root.

```bash
export MIRRORMAX_JAVA_HOME=/absolute/path/to/jdk-17
export MIRRORMAX_BUILD_TOOLS=/absolute/path/to/build-tools
export MIRRORMAX_ANDROID_JAR=/absolute/path/to/android.jar
./build.sh
```

The script compiles Java and resources, converts the Java classes to DEX, packages assets, aligns the archive, signs it, and verifies the signature. The result is `out/mirrormax.apk`.

## Signing

The build creates a reusable local signing key in `local/` if one doesn't exist. To use your own key, set `MIRRORMAX_KEYSTORE`, `MIRRORMAX_KEYPASS_FILE`, and `MIRRORMAX_KEY_ALIAS`. The build reads the password from a file. Never commit either file. The published APK is already signed. An APK signed with your own key does not replace an installation signed with the release key.

## Run tests

Run `./test/run-core.sh` with Python, Pillow, and the JDK. This checks mirror mapping and GIF encoder/decoder interoperability. For the native export tests, follow the [Android instrumentation guide](../test/android/README.md). Its test APK must use the same signing identity as the app under test.
