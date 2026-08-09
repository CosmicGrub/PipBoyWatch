# Pip-Boy Watch App — Design Spec

**Date:** 2026-08-09
**Target device:** Samsung Galaxy Watch6 Classic (Wear OS, One UI Watch)
**Status:** Approved — ready for implementation planning

## Overview

A standalone Wear OS app that reskins the watch as a Fallout-style Pip-Boy: a
green-phosphor CRT interface with five tabs (STAT / INV / DATA / MAP / RADIO)
navigated via the watch's physical rotating bezel, showing real health data,
a real EDC checklist, a real quest/notes log, real run tracking, and real
media controls — Pip-Boy presentation over genuinely useful watch
functionality, not a game.

## Goals

- Build a fully standalone Wear OS app (no phone app required for core
  functionality) that can be iterated on quickly via wifi ADB.
- Reskin real watch data/functionality as Pip-Boy screens rather than
  building abstracted game mechanics disconnected from real activity.
- Keep the physical rotating bezel as the primary navigation gimmick,
  mirroring the real Pip-Boy's rotary dial.
- Design the data layer so a phone companion app (needed later for Evernote
  sync and the Notes share-receiver) can be added without reworking the
  watch app's core architecture.

## Non-Goals (v1)

- No live map tile rendering on MAP (v2+).
- No automatic route discovery/suggestion beyond resurfacing the user's own
  best past runs (v2+/v3+).
- No full Evernote notebook sync (v2, pending Evernote API key approval).
- No ColorNote sync of any kind — no officially supported or reliable path
  exists (see Research Notes below); ColorNote notes are out of scope for
  automated sync.
- No cellular/standalone-from-phone operation is assumed beyond what the
  watch's own GPS/BT/WiFi provide.

## Architecture

- **Watch app:** Kotlin + Jetpack Compose for Wear OS, standalone APK
  installed directly on the Watch6 Classic.
- **Phone component (v1, minimal):** a single-purpose Android Share-sheet
  receiver ("Pip-Boy Notes") — not a full companion app. It accepts shared
  text from any app (ColorNote, Evernote, or anything else with a Share
  action), and forwards it to the watch over the Wear Data Layer.
- **Data Layer sync:** Wear `DataClient`/`MessageClient` APIs carry shared
  notes from phone to watch. This is one-directional (phone → watch) for v1.
- **Local persistence (watch):** Room database for checklist state, quest/
  to-do items, received notes, run history, and cached notification-log
  snapshots. DataStore for simple key-value settings (e.g. last checklist
  reset time).
- **Health data:** Health Connect API for steps, heart rate, sleep, and
  workout history (written by Samsung Health on One UI Watch 5+).
- **Location/run tracking:** `FusedLocationProviderClient` for GPS, plus the
  device barometer for elevation during runs.
- **Media control:** `MediaController`/`MediaSession` APIs to read and
  control phone playback for the RADIO tab.
- **Notifications:** `NotificationListenerService` (requires user-granted
  permission) feeds the DATA tab's "Holotapes" notification log.
- **Bezel input:** Wear OS Rotary Input API drives dial navigation on the
  home screen and scroll/zoom within individual screens.
- **Dev workflow:** wifi ADB (`adb pair` + `adb connect` after enabling
  Wireless debugging in watch Developer Options) for cable-free iteration.

## Navigation

The home screen displays the current tab name centered on the round face.
Rotating the physical bezel spins through STAT → INV → DATA → MAP → RADIO
one detent at a time, with a haptic tick per notch; tapping selects the
current tab. Inside a screen, the bezel's role switches to scrolling or
zooming that screen's content. Swipe-back or the crown returns to the home
dial.

## Visual Style

Green-phosphor CRT aesthetic: monospace typeface, subtle scanline overlay,
amber/green foreground on near-black background, slight screen-curvature
vignette, chunky retro-terminal borders on cards and lists.

## Screens (v1)

### STAT
Today's steps, active minutes, heart-rate zones (live when worn snugly),
last night's sleep summary, and a list of recent workouts — all pulled from
Health Connect and refreshed periodically. No SPECIAL-stat abstraction —
this is a real daily health dashboard styled as a Pip-Boy status readout.

### INV
An EDC checklist (phone, keys, wallet, watch, ID, and any other items the
user configures). The phone row auto-checks itself based on Bluetooth
connection state to the paired phone. All other items are tap-to-confirm.
The checklist resets each morning (time configurable, default early morning
reset).

### DATA
Four sub-pages, matching the real Pip-Boy's DATA tab structure:
- **Quests** — a simple on-watch to-do list (add, complete, remove items).
- **Holotapes** — recent notification history, sourced from
  `NotificationListenerService`.
- **Perks** — streak/achievement badges (e.g. a 7-day step streak unlocks a
  perk), computed from STAT history.
- **Notes** — read-only feed of notes received from the phone's "Pip-Boy
  Notes" Share-sheet receiver, most recent first.

### MAP
A run tracker, not a general map viewer. Start/stop a run; while active,
show live pace, distance, heart rate, and elevation (via barometer). On
stop, the GPS track and stats are saved. A "Past Runs" list surfaces the
user's best-pace and best-elevation-gain runs so they can pick a
known-good route to repeat. No live map tiles are rendered in v1.

### RADIO
Now-playing transport controls (play/pause/skip/volume) for whatever media
is active on the paired phone, styled as a Pip-Boy radio dial.

## Research Notes: Note-Taking App Integration

- **ColorNote** has no official API and no clean export path. Its backup
  format is proprietary and encrypted; the only known way to extract data is
  via community-built, unofficial decryptor tools that reverse-engineer the
  format into JSON. There is no live or automatic sync path. ColorNote sync
  is explicitly out of scope for this app; if the user wants their ColorNote
  content available, they would need to manually migrate it into Evernote
  or share individual notes via the Share-sheet receiver like any other app.
- **Evernote** has an official API (EDAM), but personal/hobby access is
  gated: developer tokens are currently listed as unavailable except for
  proven necessity, and a production OAuth API key requires a manual review
  request (up to ~5 business days, not guaranteed to be approved). The
  classic API is deprecated but still functional for existing integrations.
  Full automatic notebook sync against Evernote is planned as a v2 feature,
  contingent on API key approval.

## Roadmap (post-v1)

Added feature-by-feature as needed, not as a fixed big-bang release:
- Full Evernote OAuth notebook sync (pending API key approval).
- Live map tile rendering on MAP.
- Genuine new-route suggestions using external map/road data.
- Richer phone integration (weather, calendar) once a fuller phone
  companion app exists beyond the Notes Share-sheet receiver.

## Permissions Required

- Health Connect (read: steps, heart rate, sleep, exercise)
- Body sensors (heart rate)
- Location (foreground, for run tracking)
- Bluetooth (phone presence detection for INV)
- Notification access (`NotificationListenerService`, for Holotapes)
- Media session access (for RADIO)

## Open Questions for Implementation Planning

- Exact daily reset time/trigger for the INV checklist (fixed time vs.
  "first unlock after midnight" vs. a manual reset button).
- Whether Perks/streaks need their own persistence table or can be derived
  on-the-fly from existing STAT history at read time.
- Data Layer message size/frequency limits for the Notes feature, and
  whether a simple length cap is needed for very long shared notes.
