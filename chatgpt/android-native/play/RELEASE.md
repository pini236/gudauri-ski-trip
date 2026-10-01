# Google Play release

Package: `com.pini.gudauri` · version `0.1.0` / code `1` · target API 36.

This document describes production/store signing. Debug test builds use the
separate package `com.pini.gudauri.chatgpt.test` and a distinct launcher label;
see [`TESTING_HE.md`](TESTING_HE.md). They must not replace the installed app or
be presented as a Google Play release.

## Required account-side information

The repository has no authenticated Google Play Console publishing connection.
The owner must provide account access, app ownership and the final public support
email / privacy-policy URL. Do not substitute GitHub or Google Drive credentials.
If this package already exists, use that app's upload key and increase versionCode.
If it is a new app, choose Play App Signing and retain the upload key securely.

New personal accounts may need a closed test with 12 opted-in testers for 14
continuous days before applying for production access. This cannot be replaced by
automated emulator testing. Check the actual account's requirements in Console.

Primary documentation:
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/studio/publish/upload-bundle

## Files to prepare

1. Signed Android App Bundle from `:app:bundleRelease`.
2. App icon is ready in `assets/icon-512.png`; genuine emulator screenshots are in `../qa/screenshots/`. Add physical-phone captures after hardware validation.
3. Feature graphic is ready in `assets/feature-1024x500.png`; listing text in `listing-he.md`.
4. Complete `privacy-draft-he.md` with developer identity/support contact, host it, and add the final contact/link to the in-app privacy information. The existing web source must remain unchanged; host separately.
5. Accurate Data safety, content rating, app access and target-audience answers.

## Signing without committing secrets

Build configuration supports an optional, untracked `signing.properties` file:

```properties
storeFile=/absolute/path/to/your-upload-key.jks
storePassword=your-password
keyAlias=upload
keyPassword=your-password
```

Use your existing key if this application has already been registered. Store keys
and passwords privately and back them up. They are neither website data nor public
repository content.

## Verification before production

- Unit tests, Android flow tests and release lint/build.
- Confirm bundled JSON files equal the website data byte for byte.
- Inspect actual light/dark home, 3D map, top view, selection and details screens.
- Exercise gestures, background/resume, orientation, larger font and no network.
- On a physical midrange Android phone: frame times while orbiting/zooming,
  cold start, peak memory, sustained use and battery/thermal behavior.
- Play internal testing / pre-launch report, then the applicable testing track.
- Complete privacy/contact and declaration forms before submitting for review.

Do not claim smoothness on all devices, complete piste coverage, live operational
status, route safety or store publication based only on the emulator.
