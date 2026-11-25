# Android 10 branch

This branch is upstream `develop` plus the changes needed to run on Android 10–12
head units (tested target: UIS8581A / SC9863A, MCU ZA001, real API 29).

## What differs from upstream

- `minSdk` 29 instead of 33.
- Fallbacks for API 30–33 calls: PackageManager `*Flags` overloads
  (`data/common/PackageManagerCompat.kt`), `PlaybackState.isActive()` (music card),
  Material You colours, `TelephonyCallback`, GNSS status callback, Wi-Fi RSSI,
  `Geocoder` listener, Bluetooth / notification permissions, installer
  `setPackageSource`, and the Diagnostics collectors (display metrics, thermal
  headroom, exit history, SoC, location providers).
- The in-app updater is off (it follows upstream's API 33 releases); updates come
  from this fork's releases.

Part of the compat work comes from
[Liga1231/femto-car-launcher-ru](https://github.com/Liga1231/femto-car-launcher-ru) (commit `0584f30`).

## Device requirements

- Android System WebView 101 or newer for the map. Stock UIS8581A firmware ships
  Chromium 90; install `com.google.android.webview` (a `minAPI29` build) and select it
  under Developer options → WebView implementation.
- arm64-v8a.

## How the build works

`.github/workflows/android10.yml` runs nightly and on every push to `android10`:

1. Merges upstream `develop` (a plain merge: a conflict stops the run and opens an issue).
2. Runs `lintStableRelease`. Lint's NewApi check fails the build when upstream adds a
   call newer than Android 10, so a crashing APK is never published; the lint report is
   attached to the failed run.
3. Builds `assembleStableRelease`, signed with the key from the repository secrets, and
   publishes it as a GitHub release.

### One-time signing setup

```sh
keytool -genkeypair -v -keystore femto-release.jks -alias femto \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 femto-release.jks   # PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("femto-release.jks"))
```

Add these repository secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` (`femto`), `RELEASE_KEY_PASSWORD`. Keep the `.jks` file safe: if the key
changes, the next APK cannot be installed over the old one without uninstalling it first.
