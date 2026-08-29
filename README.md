<h1 align="center">Mi Recorder Enhancer</h1>

[中文文档](README.zh-CN.md)

## ✨ Features

- Conditional filtering
  - Storage location: local, synced, or cloud-only
  - Duration: less than or at least any number of seconds
  - Date: any date range
  - Transcription status: transcribed or not transcribed
- Manual transcription for one or multiple recordings

## 📥 Installation

Download the APK from the [latest GitHub release](https://github.com/jasongzy/MiRecorderEnhancer/releases/latest).

- Android 13 or newer
- LSPosed with modern Xposed API support
- Xiaomi Recorder: verified on `7.8.9.9`

Enable Mi Recorder Enhancer in LSPosed, then restart Xiaomi Recorder. The module declares `com.android.soundrecorder` as its static scope.
