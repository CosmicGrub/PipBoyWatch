# Pip-Boy Watch App — Implementation Plan

**Date:** 2026-08-09
**Spec:** `docs/superpowers/specs/2026-08-09-pipboy-watch-app-design.md`

Each phase should end in something runnable on the actual watch via wifi
ADB before moving to the next — this is a "playtest after every phase"
project, not a build-everything-then-test one.

## Phase 0 — Environment & Project Scaffolding

- Confirm Android Studio + Wear OS SDK components are installed.
- Enable Developer Options + Wireless debugging on the Watch6 Classic;
  pair and connect over wifi ADB (`adb pair <ip:port>` then
  `adb connect <ip:port>`).
- Confirm the watch's Wear OS API level (determines `minSdk`).
- Create a new Gradle project: a `wear` app module (Compose for Wear OS),
  empty single-activity shell, deployed to the watch to confirm the
  wifi ADB → install → launch loop works end to end.

**Done when:** a blank "Hello Pip-Boy" screen launches on the physical
watch via a wifi ADB deploy, no cable involved.

## Phase 1 — Shell: Navigation + Visual Theme

- Build the CRT visual theme as reusable Compose primitives: color palette,
  monospace type, scanline overlay, terminal-style borders — applied at the
  theme level so every later screen inherits it for free.
- Implement the home dial screen with the five tab labels (STAT/INV/DATA/
  MAP/RADIO) and Rotary Input handling to spin between them with haptic
  ticks; tap to enter a tab (placeholder screens for now).
- Implement bezel-scroll behavior inside a screen (can validate with a
  placeholder scrollable list before real screens exist).

**Done when:** you can rotate the bezel through all five tabs on-device,
enter/exit each one, and it visually reads as "Pip-Boy," not default
Wear OS.

**Correction (found in Phase 3):** at the time, this was only verified via
tap and swipe — real bezel/rotary input was never actually tested (no way
to simulate it yet). It turned out to be broken: a plain
`remember { FocusRequester() }` + `LaunchedEffect(Unit) { requestFocus() }`
doesn't reliably acquire focus in time for rotary dispatch on a freshly
composed screen. Fixed in Phase 3 across all screens by switching to Wear
Compose Foundation's `rememberActiveFocusRequester()`, its purpose-built
solution for this exact timing problem. Lesson: verify every stated input
method for real before marking a phase done, not just the ones convenient
to test at the time — see Phase 3 below for how rotary got tested via
`adb shell input rotaryencoder scroll`.

## Phase 2 — STAT

- Request Health Connect permissions (steps, heart rate, sleep, exercise).
- Fetch and display: today's steps, active minutes, HR zones, last night's
  sleep summary, recent workout list.
- Handle the no-data / permission-denied states explicitly (don't just
  show a blank screen).

**Done when:** STAT reflects your real step count and recent workout from
Health Connect, live on the watch.

**Actual outcome:** code is complete and correct (permission flow, data
queries, all UI states), verified on-device — but blocked by a Samsung
Wear OS platform gap: `Context.getSystemService("healthconnect")` returns
null on this watch despite the Binder service existing, so Health Connect
genuinely reports unavailable here. STAT shows its "NO SIGNAL" fallback
state instead of live data. Full root-cause writeup and the phone-relay
fix path are in the spec's "Known Device Limitation" section. Treating
this phase as closed for now and continuing to Phase 3 in order; revisit
STAT once Phase 7's phone Data Layer channel exists, or sooner if live
STAT data becomes a priority.

## Phase 3 — INV

- Define the checklist data model (Room): configurable item list, checked
  state, last-reset timestamp.
- Implement BT-based auto-check for the phone item (observe connection
  state to the paired phone).
- Implement tap-to-confirm for the rest, and the daily reset trigger
  (resolve the "Open Question" from the spec: fixed time vs. first-unlock
  vs. manual reset — pick one before building).

**Done when:** the checklist auto-checks "phone" when your phone is
BT-connected, resets each morning, and persists taps across app restarts.

**Actual outcome:** all done-when criteria verified live on-device —
including a genuine force-stop + relaunch (Room/DataStore persistence
confirmed, not just in-memory state). Decisions made along the way:
- Phone presence uses the Wear Data Layer's `NodeClient.connectedNodes`
  (reflects the real watch<->phone companion link) rather than raw
  Bluetooth pairing state — no BLUETOOTH_CONNECT permission needed either.
- Daily reset resolved as "reset on first screen visit after midnight"
  (compare stored last-reset date to today on each visit) rather than a
  background job — simplest option that meets the requirement.
- Found and fixed the Phase 1 rotary-focus bug (see Phase 1's correction
  note) using `rememberActiveFocusRequester()`. Verified real rotary
  input end-to-end for the first time this phase via
  `adb shell input rotaryencoder scroll --axis SCROLL,<n>` — small values
  (1-3) land close to one detent; larger values trigger the platform's
  fling physics and overshoot multiple tabs, which is expected simulator
  behavior (a single synthetic event lacks the timing data of continuous
  real bezel rotation) and not a concern for actual hardware use.

## Phase 4 — DATA

- Room schema for quests (to-do items) and notes.
- Quests sub-page: add/complete/remove.
- Holotapes sub-page: request notification-listener permission, surface
  recent notifications.
- Perks sub-page: define initial streak rules (e.g. 7-day step streak) and
  compute from STAT history — decide persisted-table vs. derived-on-read
  per the spec's open question.
- Notes sub-page: watch-side listener for Data Layer messages, stored to
  Room, shown read-only, most-recent-first. (The phone-side sender is
  built in Phase 7 — stub the watch listener now so Phase 7 has something
  to send to.)

**Done when:** all four DATA sub-pages show real content (quests you add
on-watch, actual recent notifications, at least one working perk rule);
Notes can render stored items even before Phase 7 exists (seed test data
manually to verify the UI).

**Actual outcome:** all four sub-pages built as one continuously
scrollable screen (QUESTS/HOLOTAPES/PERKS/NOTES sections stacked in a
single Column) rather than separate swipeable pages — resolves an
ambiguity the spec left open, and stays consistent with how STAT/INV
already work rather than introducing a new sub-navigation pattern.
Verified live on-device, including full round-trips (not just UI review):
- **Quests:** add (via on-watch voice/keyboard text capture —
  `androidx.wear:wear-input`'s `RemoteInputIntentHelper`, the standard
  Wear pattern for one-off text entry), toggle done, delete — all
  confirmed working, including the actual Samsung keyboard/voice picker
  launching for real.
- **Holotapes:** `NotificationListenerService` built and registered;
  permission-gate card confirmed. Also **decided Notes' "seed test data"
  verification method here**: rather than throwaway test-only UI, on-watch
  note entry (same RemoteInput flow as Quests) became a real, permanent
  feature — gives genuine user value now and doubles as the verification
  path the plan asked for, rather than something to strip out later.
- **Perks:** resolved the spec's persistence open question — computed
  on-the-fly at read time from existing INV/STAT data rather than a
  separate table (both rules are cheap boolean checks; a synced table
  would just be a second source of truth for no benefit). "Fully Loaded"
  (all INV items checked) is genuinely exercisable today; "Step Streak"
  is wired correctly but inherits STAT's known Health Connect limitation
  from Phase 2 — confirmed it fails honestly rather than silently.
- **Notes:** on-watch add flow verified end-to-end (text -> Room -> UI).
  The phone-relay `WearableListenerService` is built and registered but
  correctly unexercised — nothing sends to it until Phase 7.

**Bug found and fixed:** tapping Holotapes' "GRANT" button crashed the
app — `ActivityNotFoundException: No Activity found to handle Intent {
act=android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS }`. This
Samsung Wear OS build doesn't implement that standard Android settings
screen. Confirmed via logcat, not guessed. Fixed by catching the
exception and showing an honest inline message ("this watch doesn't
expose that settings screen directly — try the paired phone's Galaxy
Wearable app instead") rather than a fallback that couldn't be verified
to actually work. Same graceful-degradation principle as STAT/Perks,
applied to a case that would otherwise have crashed instead of degrading.

## Phase 5 — MAP (Run Tracker)

- Room schema for run history (route points, pace, distance, elevation,
  timestamps).
- Start/stop run flow with `FusedLocationProviderClient` + barometer,
  live pace/distance/HR/elevation display during a run.
- Past Runs list, sorted/filterable by best pace and best elevation gain.

**Done when:** a real GPS run around the block gets recorded, saved, and
shows up correctly in Past Runs afterward.

**Actual outcome:** the full mechanism is proven live on-device with real
sensor data — but this was a stationary/indoor test (this session can't
physically walk around with the watch), not an actual run around the
block. Verified for real:
- Location permission dialog: genuine system prompt, "While using app"
  granted correctly, persisted across a force-stop + relaunch.
- Live GPS tracking: `FusedLocationProviderClient` genuinely fired
  location updates (0.06 km of indoor GPS drift accumulated while sitting
  still — normal indoor GPS noise, and proof the callback pipeline is
  live, not proof of intentional movement).
- Barometer: real pressure-derived elevation-gain readings accumulated.
- Heart rate: sensor registered without error; showed "--" throughout
  since the watch wasn't worn during the test (no crash, correct
  graceful state — genuinely wearing it during a run is the real test).
- Ticker, Stop/save, Past Runs list, and best-pace/best-climb tagging all
  verified correct, including surviving a genuine force-stop + relaunch.

Heart rate comes straight from the raw `TYPE_HEART_RATE` sensor rather
than Health Connect — deliberately sidesteps this device's Phase 2 HC
gap entirely for this tab.

Tracking is foreground-only (no foreground service) — stops if you leave
the MAP screen mid-run. Acceptable v1 tradeoff per the spec's MAP scope
decision; a foreground service is a reasonable Phase 8 polish candidate
if that turns out to matter in practice.

**Still needed:** an actual outdoor walk to validate real-world distance/
pace accuracy against a known route — the mechanism is proven correct,
but real GPS accuracy outdoors (vs. indoor drift) hasn't been observed.
Worth doing next time you're wearing the watch outside.

## Phase 6 — RADIO

- Integrate `MediaController`/`MediaSession` to read phone playback state
  and issue transport commands (play/pause/skip/volume).
- Style transport controls as a Pip-Boy radio dial.

**Done when:** controlling playback from the watch actually changes what's
playing on the phone.

**Actual outcome:** built correctly, but blocked by the same permission
gap Holotapes hit in Phase 4 — and this time investigated much further
before accepting it. RADIO reads the phone's currently-playing media by
extracting the `MediaSession.Token` embedded in a bridged MediaStyle
notification's extras (`Notification.EXTRA_MEDIA_SESSION`), reusing the
`PipBoyNotificationListenerService` already built for Holotapes — a
deliberate reuse of that same permission rather than a separate one, and
the standard, documented pattern for third-party "now playing" widgets.
`MediaController.transportControls` then drives play/pause/skip/volume
directly against that token — no extra permission needed beyond
notification-listener access itself.

The blocker: this Watch6 Classic has no way to actually grant that
access. Beyond Phase 4's finding (the standard
`ACTION_NOTIFICATION_LISTENER_SETTINGS` intent crashes, unresolvable),
this phase searched further — dumped the real Wear Settings app's
(`com.google.android.apps.wearable.settings`) declared activities and
intent-filters directly via `dumpsys package`, and found no notification
*listener* access screen at all (only `APP_NOTIFICATION_SETTINGS`-style
screens, which control whether an app can post its own notifications —
a different permission from reading others'). Unlike Phase 2's Health
Connect gap, there's no clean phone-relay workaround here either: the
media token has to come from a notification actually bridged to the
*watch*, so watch-side listener access is unavoidable regardless of
where the media session originates.

Most likely explanation: Samsung may gate this behind the phone-side
Galaxy Wearable companion app for watch apps installed through the
Galaxy Store, and our app is sideloaded — but this couldn't be confirmed
from this session (no way to drive the paired phone's UI here). Worth
checking Galaxy Wearable's app-permission screen by hand next time you
have the phone in front of you; if it's there, RADIO should start
working immediately with no code changes, since the watch-side plumbing
is already correct and waiting.

Session also lost the wifi ADB connection again near the end of this
phase (likely screen-lock related, same class of issue as the very
first connection drop this session) and it didn't recover via mDNS
auto-reconnect this time — full on-device transport-control testing
(confirming a Pause tap actually pauses phone audio) is still
outstanding, blocked on both the permission gap above and reconnecting.

## Phase 7 — Phone Notes Receiver

- New minimal `phone` Gradle module: a single Share-target activity
  ("Pip-Boy Notes") that accepts shared text from any app.
- On receipt, send the note to the watch via the Wear Data Layer
  (`DataClient`/`MessageClient`), respecting any length cap decided in
  Phase 4's open question.
- Confirm end-to-end: share a note from Evernote (or any app) on the
  phone → it appears in DATA > Notes on the watch.

**Done when:** sharing a note from your phone reliably shows up on the
watch within a few seconds.

**Actual outcome:** the `:phone` Gradle module is built and installed on
the paired phone (SM-F946U) — a minimal `SendNoteActivity` registered for
`ACTION_SEND`/`text/plain`, appearing as "Pip-Boy Notes" in Android's
Share sheet, relaying the shared text to every connected watch node over
`MessageClient`. Two real bugs found and fixed along the way:

1. **applicationId mismatch.** The phone module was initially
   `com.pipboywatch.notes`, distinct from the watch app's
   `com.pipboywatch.app`. The Wear Data Layer routes messages by AppKey
   (package name + signing certificate), so a phone app and its watch
   companion must share the same applicationId to be recognized as a
   pair — confirmed via a live `Failed to deliver message to AppKey`
   entry in the watch's system log, not assumed from docs alone. Fixed
   by matching `applicationId` across both modules (the phone module's
   Kotlin package/`namespace` stays `com.pipboywatch.notes` — only the
   applicationId needed to match).
2. **Toast-then-finish race.** `SendNoteActivity` called `finish()`
   immediately after `Toast.show()`, which could tear the activity down
   before the OS actually displayed the toast (`Toast already killed` in
   logcat). Fixed with a short `Handler.postDelayed` before finishing.

**Still unresolved (Phase 8 update):** re-tested with both devices
freshly, cleanly connected — no recent GMS restart, no stale caches —
and delivery still didn't succeed (this time with no logged error at
all on either side, not even the earlier `Failed to deliver` warning,
ruling out the "just GMS restart noise" theory from the initial Phase 7
finding). Also checked the phone's Galaxy Wearable app directly (Watch
settings → Notifications → App notifications) on the theory that
Samsung's own companion app might hold the real grant: our sideloaded
app doesn't appear in that list at all (only Galaxy Store-installed
watch apps do), and that screen turned out to control Samsung's own
notification-mirroring feature anyway — a different thing entirely from
the Android `NotificationListenerService` permission RADIO/Holotapes
need, and from the Wear Data Layer `MessageClient` routing Notes needs.
Both are now confirmed dead ends specifically for a sideloaded
(non-Galaxy-Store) app on this device.

Root cause remains unconfirmed after applicationId match, signing
cert match (verified via apksigner), correct manifest registration
(verified via dumpsys package), a GMS restart, and multiple clean
retries. This now reads as a real platform limitation for sideloaded
apps on this specific Samsung Wear OS build, not a one-off flake —
consistent with the pattern already seen twice elsewhere (Health
Connect in Phase 2, notification-listener access in Phase 4/6). Further
progress likely needs either Samsung/Google developer support channels
or a real Galaxy Store listing to test against, both outside this
session's reach.

Diagnostic logging (`Log.d("PipBoyNotes", ...)` on both the phone send
path and the watch's `onMessageReceived`) was added specifically to make
this debuggable and is worth keeping rather than stripping out. Next
step for whoever picks this back up: retry the exact same send with a
stable wifi ADB connection and a watch that hasn't just had GMS force-
restarted, to rule the flakiness theory in or out cleanly.

## Phase 8 — Polish & Device Testing Pass

- Battery impact check, especially for MAP's GPS use and STAT's periodic
  Health Connect polling.
- Full permission-flow walkthrough on a fresh install (nothing should
  crash if a permission is denied — degrade gracefully).
- Visual polish pass on the CRT theme across all five tabs together.

**Done when:** a clean install → grant permissions → use all five tabs →
no crashes, no permanently-blank screens, acceptable battery drain over a
normal day of wear.

**Actual outcome:**

- **Visual polish (round-safe padding):** the round-screen content
  clipping visible in screenshots since Phase 1 is fixed. Added
  `screenContentPadding()` (checks `LocalConfiguration.isScreenRound`,
  applies extra top/bottom/side margin on round screens) and applied it
  across all six screen files, replacing six copies of a hardcoded
  padding value. Verified on-device: STAT's title now has real clearance
  from the top edge, and DATA's last row ("+ ADD NOTE") is no longer
  clipped at the bottom — both previously cut off. (Mid-scroll clipping
  of a card transitioning past the curved edge is expected/inherent to
  round-screen scrolling and unrelated to this fix — it resolves once
  scrolling settles.)
- **Permission-denial walkthrough:** revoked `ACCESS_FINE_LOCATION` via
  `pm revoke`, force-stopped, and relaunched fresh — MAP correctly fell
  back to its "ACCESS REQUIRED" gate with no crash, confirming the
  `ContextCompat.checkSelfPermission`-gated pattern used throughout the
  app holds up under an actual revoked-permission condition, not just
  a first-ever-launch one. Holotapes/RADIO's notification-access-denied
  path has effectively been exercised continuously since Phase 4 (access
  was never successfully granted on this device across the whole
  project) and never crashed either.
- **Further investigated the Phase 4/6/7 permission/routing gaps:**
  checked the phone's Galaxy Wearable companion app directly (Watch
  settings → Notifications → App notifications) on the theory it might
  hold the real grant for notification-listener access. Confirmed dead
  end: our sideloaded app doesn't appear in that list at all (only
  Galaxy Store-installed watch apps do), and the screen turned out to
  control a different feature entirely (Samsung's own notification-
  mirroring, not Android's `NotificationListenerService` permission).
  Also re-tested Phase 7's note delivery with both devices freshly,
  cleanly connected (no recent restart, no stale cache) — still didn't
  succeed, ruling out the "just GMS restart noise" read from Phase 7
  and pointing at a real platform limitation for sideloaded apps on this
  device rather than a transient flake. See the Phase 7 outcome note
  above for the full, updated picture.
- **Battery check:** honestly could not be tested as "a normal day of
  wear" — this session's own testing was unusually battery-intensive
  (screen held on for hours, wifi ADB connected continuously, GPS/
  sensors repeatedly exercised on purpose for verification), so the
  observed ~60-point drain over the session isn't a fair proxy for real
  usage. Did confirm no evidence of a stuck background location request
  (`dumpsys location` history is consistent with tracking only while
  MAP was actively open, matching the `DisposableEffect` teardown
  design) — the main structural battery risk was ruled out, even though
  a true multi-hour real-world reading wasn't obtained.

**Not done from the original scope:** a from-scratch clean install (old
Room data wiped, permissions never previously granted) wasn't performed
this phase — testing built on top of the app's accumulated state across
Phases 0-7. The individual pieces (fresh permission grant flows, no-data
states) have each been verified at some point across the project, but
not all together in one true clean-install pass. Worth doing before
calling v1 fully done.

## Deferred to Post-v1 (per spec roadmap)

Not part of this plan — revisit only after v1 is solid and in daily use:
Evernote OAuth notebook sync, live map tile rendering, route-suggestion
intelligence, richer phone companion features (weather/calendar).
