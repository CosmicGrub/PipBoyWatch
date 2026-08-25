# Release signing & publishing

System 07 from the RobCo Systems Roadmap. This documents the one-time
sequence for ever publishing PipBoyWatch beyond your own sideloaded
devices — most of it is genuinely irreversible once done, so it needs to
happen in the right order, not worked out live during your first upload.

## Local release signing (do this first, any time)

1. Generate a release keystore, once, and back it up somewhere durable —
   losing it means losing the ability to ever publish an update under the
   same identity:
   ```
   keytool -genkeypair -v -keystore release-keystore.jks -alias pipboywatch \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Put the resulting `.jks` outside the repo (both `keystore.properties`
   and any `.jks`/`.keystore` file are gitignored — never commit either).
2. Copy `wear/keystore.properties.example` to `wear/keystore.properties`
   and `phone/keystore.properties.example` to
   `phone/keystore.properties`, filling in the real path/passwords.
3. **`keyAlias` must be the exact same alias in both files** (the same
   key entry in the same, or an identically-keyed, keystore). This is not
   a style preference: the Wear Data Layer routes messages between the
   watch and phone by AppKey, which is package name **+ signing
   certificate** together. Every debug/sideloaded test so far has worked
   because Android's own default debug keystore happens to be shared by
   both modules automatically — a mismatched *release* signature between
   wear and phone would silently break phone<->watch message delivery in
   a signed build, in exactly the same "Failed to deliver message to
   AppKey" way a Phase 7 applicationId mismatch already did once (see
   phone/build.gradle.kts's own comment on that). Verified directly while
   building this system: signing both modules with a real test keystore
   sharing one alias produces two APKs with byte-identical certificate
   digests (checked via `apksigner verify --print-certs`); this is what
   that guarantees.
4. `./gradlew :wear:assembleRelease :phone:assembleRelease`. Without
   `keystore.properties`, this still succeeds and produces
   `*-release-unsigned.apk` in each module's `build/outputs/apk/release/`
   — that's the same behavior AGP has always had for a release build
   type with no signingConfig assigned, not something this system
   changed. With `keystore.properties` in place, the same command
   produces real signed `wear-release.apk` / `phone-release.apk`.

## Play Store publishing (the actual irreversible step)

**Publish wear and phone under one Play Store listing, not two.** Per
Android's own Wear OS packaging guidance: wear and phone APKs are
uploaded as separate APK artifacts, but under the *same* listing, managed
via the Multi-APK delivery method — this is also what makes Play App
Signing safe for this app specifically. Play App Signing operates per
listing: one listing means Play re-signs both the wear and phone APK
with the *same* Google-managed certificate, preserving the AppKey
package+signature match this app depends on. **Two separate listings
would each get a different Play-managed certificate** and break
phone<->watch delivery in production, the same failure mode described
above, except undiagnosable from outside without knowing this — and
unlike everything else in this document, which app-side or local
keystore, this one can't be fixed retroactively without moving the whole
app to a new listing.

The sequence, once you're ready to publish for real:

1. Create the one Play Store listing (covers both wear and phone).
2. Opt into Play App Signing on the very first upload — this is the
   irreversible part; there is no "undo" once a listing has an upload
   under Play App Signing.
3. Upload both APKs under that listing via Multi-APK delivery. Confirm
   the wear form factor is declared correctly (it has its own
   independent version-code scheme — see
   [Set app version information](https://developer.android.com/studio/publish/versioning#appversioning)
   — separate from phone's).
4. Personal Play developer accounts require a mandatory 12-tester/14-day
   closed test before production publishing is allowed at all — start
   this well before you actually want the app live, not the week you
   decide to publish.
5. Start staged rollout at 5–10%, not 100%, specifically because a
   phone+wear paired release doubles the surface for a cross-module bug
   (a phone-side change breaking a watch-side assumption, or vice versa)
   to slip past the unit test suite, which only covers pure logic, not
   the cross-device integration path.

## Deliberately not yet done

No real keystore is generated or committed as part of this system — that
stays a manual, one-time action for whoever actually decides to publish,
matching this being a "prepare the runway" system, not a "we're
publishing now" one.
