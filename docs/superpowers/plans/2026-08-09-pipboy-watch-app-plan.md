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

## Phase 6 — RADIO

- Integrate `MediaController`/`MediaSession` to read phone playback state
  and issue transport commands (play/pause/skip/volume).
- Style transport controls as a Pip-Boy radio dial.

**Done when:** controlling playback from the watch actually changes what's
playing on the phone.

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

## Phase 8 — Polish & Device Testing Pass

- Battery impact check, especially for MAP's GPS use and STAT's periodic
  Health Connect polling.
- Full permission-flow walkthrough on a fresh install (nothing should
  crash if a permission is denied — degrade gracefully).
- Visual polish pass on the CRT theme across all five tabs together.

**Done when:** a clean install → grant permissions → use all five tabs →
no crashes, no permanently-blank screens, acceptable battery drain over a
normal day of wear.

## Deferred to Post-v1 (per spec roadmap)

Not part of this plan — revisit only after v1 is solid and in daily use:
Evernote OAuth notebook sync, live map tile rendering, route-suggestion
intelligence, richer phone companion features (weather/calendar).
