# PixConverter v1.0.2

Offline Android image converter for:

- HEIC → JPEG
- HEIF → JPEG
- WebP → JPEG

## Storage

Converted files are written to:

`Pictures/PixConverter/`

Android 10+ uses MediaStore. Android 8/9 use the public Pictures directory and MediaScanner.

## Privacy

The application is designed to work without an internet connection. No backend, account, upload, analytics, or cloud processing is included.

## Build target

- Android Studio compatible
- AGP 8.6.1
- Kotlin 2.0.21
- Compose BOM 2024.09.03
- Material 3 1.3.1
- compileSdk 35
- minSdk 26
- Java/Kotlin JVM 17
- Gradle 8.7

## Release build

Build the distributable APK with the `release` variant in Android Studio. The
release configuration enables R8 code minification and Android resource
shrinking; do not distribute the debug APK because it retains developer
metadata and unused code. The extended Material icon library was also removed
in favour of the much smaller core icon set and platform icons.

## Notes

This is the first functional MVP. Native Android image decoding is used where available. HEIC decoding behavior on Android 8/8.1 can still depend on the device's platform codec support.
