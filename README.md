# Parakeet Voice-to-Text

Android app that transcribes audio on-device with [NVIDIA Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) (int8, via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)). It covers 25 European languages including German and English, with punctuation and capitalisation; the language is detected automatically.

- Pick an audio file or record a quick test from the microphone.
- Share voice messages (e.g. from WhatsApp) to the app via **Share → Transcribe**; the result opens when done.
- Files of any length: audio is decoded to 16 kHz mono and split at quiet points into parts of at most 30 s, then the texts are joined.
- Audio and transcript are stored side by side in `Download/Parakeet/` (`<timestamp>_<name>.<ext>` + `.txt`), visible in any file explorer.
- Transcripts are selectable and copyable; delete single results (audio + text) or all of them.

The model (~670 MB) is downloaded once from Hugging Face on the first transcription (resumable, SHA-256 verified) and kept in the app's private storage. That download is the only network access; audio never leaves the phone.

Requires an arm64 phone with Android 11+ and roughly 1 GB of free storage.

## Build

Requirements: Android SDK (`ANDROID_HOME` set, or `local.properties` with `sdk.dir`) and a JDK. If `JAVA_HOME` is unset, Android Studio's bundled JDK at `/opt/android-studio/jbr` is used.

```sh
./build.sh
```

Gradle fetches the sherpa-onnx Android AAR from its GitHub release. The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## Install

Enable USB debugging on the phone, connect it via USB and accept the debugging prompt, then:

```sh
./install.sh
```

This builds if needed, installs the APK and launches the app. With several devices connected, choose one with `ANDROID_SERIAL=<serial> ./install.sh` (see `adb devices`). Reinstalling keeps the downloaded model; uninstalling removes it.

## Notes

- Files created by a previous installation are no longer visible to the app after an uninstall/reinstall (Android scoped storage); they stay in `Download/Parakeet/` for file explorers. Normal updates are unaffected.
- Recording stops when the app goes to the background, since Android blocks background microphone access.
