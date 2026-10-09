# Whistle Transcriber

Android app that transcribes audio fully on-device with [Whistle](https://cactuscompute.com/blog/whistle), Cactus Compute's 16.9 MB speech-to-text model (English, German, French, Spanish, Italian, Dutch, Polish). No internet permission; nothing leaves the phone.

- Pick an audio file or record a quick test from the microphone.
- Share voice messages (e.g. from WhatsApp) to the app via **Share → Transcribe**; the result opens when done.
- Files of any length: audio is decoded to 16 kHz mono and split at quiet points into parts of at most 30 s (Whistle's limit), then the texts are joined.
- Audio and transcript are stored side by side in `Download/Whistle/` (`<timestamp>_<name>.<ext>` + `.txt`), visible in any file explorer.
- Transcripts are selectable and copyable; delete single results (audio + text) or all of them.

Requires an arm64 phone with Android 11+.

## Build

Requirements: Android SDK (`ANDROID_HOME` set, or `local.properties` with `sdk.dir`) and a JDK. If `JAVA_HOME` is unset, Android Studio's bundled JDK at `/opt/android-studio/jbr` is used.

```sh
./build.sh
```

The first build downloads the Needle engine (`libneedle.a`, `needle.h` for android-arm64) and `whistle.cact` from Hugging Face; NDK and CMake are installed by Gradle if missing. The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## Install

Enable USB debugging on the phone, connect it via USB and accept the debugging prompt, then:

```sh
./install.sh
```

This builds if needed, installs the APK and launches the app. With several devices connected, choose one with `ANDROID_SERIAL=<serial> ./install.sh` (see `adb devices`).

## Notes

- Files created by a previous installation are no longer visible to the app after an uninstall/reinstall (Android scoped storage); they stay in `Download/Whistle/` for file explorers. Normal updates are unaffected.
- Recording stops when the app goes to the background, since Android blocks background microphone access.
