# MH4U Builder (unofficial)

Android app that builds `libmh4u_code_native.so` + `.id` for 4u-native on the phone. No PC or Termux needed.
Ships Python (Chaquopy), zig 0.13 for aarch64 and the recompiler from 4u-native (`mh4n/tools`, `mh4n/config`).

## Usage

1. Give it All files access.
2. Pick the update `.cia` (USA 1.3.0). Game `.3ds`/`.cci` and mods folder are optional.
3. Pick your MH4U folder as output and hit Build.

Rebuild whenever you change code mods. Rebuilds are incremental, so small changes are quick.

`VERSION` has to match your MH4U app. For a new 4u-native release, swap in its `mh4n/tools`, `mh4n/config` and
`VERSION`, then rebuild the APK.

## Building the APK

CI does it (`.github/workflows/build.yml`). Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. From Termux, `sh termux-setup.sh` sets all of it up.

## License

Recompiler code is from 4u-native by khorne-hub (https://github.com/khorne-hub/4u-native), GPLv3 plus
ADDITIONAL-TERMS. This is a modified, unofficial build, not made or endorsed by khorne-hub.
