# Firebase Setup

Radio-Oracle keeps Firebase Analytics and Crashlytics support, but the repository
does not track a Firebase client configuration file. Local and release builds
enable Firebase only when `app/google-services.json` exists.

## OpenARDF Project

Use the Google account `openardf@gmail.com` to create or administer the Firebase
project for Radio-Oracle. Do not reuse the legacy `ardf-manager` Firebase
project or client configuration.

Recommended project/app details:

- Firebase project name: `Radio-Oracle`
- Android package name: `org.openardf.radiooracle`
- Android app nickname: `Radio-Oracle Android`

After creating the Android app in Firebase Console, download its
`google-services.json` and place it at:

```sh
app/google-services.json
```

Keep that file local. It is ignored by Git.

## Verification

Without `app/google-services.json`, normal local builds run without Firebase:

```sh
./gradlew shared:testAndroidHostTest shared:desktopTest desktopApp:test app:testDebugUnitTest
```

With `app/google-services.json` present, verify that the Firebase plugins and
Crashlytics wiring still process the Android config:

```sh
./gradlew app:processDebugGoogleServices app:testDebugUnitTest
```

The app manifest deliberately removes the advertising-ID permissions added by
Firebase Analytics. Android unit-test manifest processing can warn that each
removal marker has no matching declaration because the main app merge has
already removed it; this waiver applies only to those two named permissions.
Release verification must still confirm that neither
`com.google.android.gms.permission.AD_ID` nor
`android.permission.ACCESS_ADSERVICES_AD_ID` appears in the merged app
manifest.

Radio-Oracle uses ordinary Crashlytics because it has no Android JNI/C++ code
of its own. The release bundle can still contain AndroidX DataStore's prebuilt
shared counter. A release Mac without the requested NDK strip tool may report
that library as packaged unchanged. Treat that as a narrowly waived toolchain
warning only when the bundle's 16 KB packaging and ELF alignment checks pass;
it is not evidence that the library is intrinsically unstrippable.
