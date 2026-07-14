# DevSwitch

Quick access to Android developer settings — a native Kotlin / Jetpack Compose app.

Flip USB debugging, wireless debugging and other developer toggles in one tap, watch
settings for external changes (get a notification with a "Turn back on" action when
e.g. wireless debugging turns itself off), and switch between free accent colors with
light / dark / system theme.

## Features

- **Instant toggles** — USB debugging, wireless debugging, keep-ADB-authorized,
  stay awake, developer options master switch, don't keep activities, mobile data
  always active, animation scales (off / 0.5× / 1× / 2× / 5×)
- **Change alerts** — tap the bell on any toggle; a quiet foreground service watches
  it and posts a high-priority notification (with one-tap revert) when the setting is
  changed outside the app
- **Theming** — light / dark / system + 9 accent colors + Material You dynamic color
- **In-app updates** — checks [GitHub releases](https://github.com/CedrickGD/DevSwitch/releases)
  and installs new versions in place

## Setup (one time)

DevSwitch changes protected settings, which Android only allows for apps that hold
`WRITE_SECURE_SETTINGS`. Grant it once over ADB after installing:

```
adb shell pm grant com.cedrickgd.devswitch android.permission.WRITE_SECURE_SETTINGS
```

The app shows this command (with a copy button) until the permission is granted.

> Note: on Samsung One UI, system-namespace settings like *show taps* /
> *pointer location* are blocked for third-party apps entirely, so DevSwitch only
> ships toggles that actually work there (global namespace).

## Building

```
./gradlew :app:assembleDebug     # debug build
./gradlew :app:assembleRelease   # release build (needs keystore.properties)
```

Release signing expects a `keystore.properties` in the project root (not committed):

```
storeFile=C:/path/to/devswitch-release.jks
storePassword=...
keyAlias=devswitch
keyPassword=...
```

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. `./gradlew :app:assembleRelease`
3. `gh release create v<version> app/build/outputs/apk/release/app-release.apk --title "DevSwitch <version>" --notes "..."`

Installed apps pick the release up via the in-app updater (Settings → Updates).
