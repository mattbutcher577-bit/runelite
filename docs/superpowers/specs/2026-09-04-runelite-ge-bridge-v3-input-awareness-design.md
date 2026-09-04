# RuneLite GE Bridge V3 Input Awareness Design

## Goal

Extend the existing read-only GE State Bridge so Python can observe RuneLite mouse/keyboard activity and use the full RuneLite state feed to pause, re-sync, and avoid stale or conflicting mouse actions.

## Constraints

- Java remains read-only: no clicks, typing, GE actions, offer placement, cancellation, collection, or gameplay automation.
- The bridge binds only to `127.0.0.1:17654` and continues exposing `GET /state` only.
- Protocol increments to `3`; stale, logged-out, malformed, or incompatible state fails closed in Python.
- F8 remains the Python emergency stop.
- Keyboard privacy: never publish typed characters, chat text, usernames, passwords, clipboard content, or arbitrary key sequences.
- Existing exact GE offer state, GP, inventory, world/player state, interface blockers, viewport/canvas dimensions, and widget bounds remain available.

## Input state

Add an `input` object with raw read-only observations:

- `lastInputEpochMs`
- `lastMouseMoveEpochMs`
- `lastMouseClickEpochMs`
- `lastMousePressEpochMs`
- `lastMouseReleaseEpochMs`
- `lastMouseWheelEpochMs`
- `lastKeyboardEpochMs`
- `mouseX`, `mouseY`
- `mouseInsideCanvas`
- `mouseButtonsDownMask`
- `lastMouseButton`
- `lastWheelRotation`
- `lastControlKey` (whitelist only: SHIFT, CTRL, ALT, ESCAPE, ENTER, F8, TAB, BACKSPACE, DELETE, arrows)
- `inputIdleMs`

Mouse movement is reported at RuneLite canvas coordinates. Keyboard events report only the whitelisted control-key name and timestamps; typed characters are not stored or serialized.

## Python intelligence

Python treats RuneLite v3 as the authoritative observation layer. It should:

1. Fail closed when the bridge is missing/stale/logged out/protocol-mismatched.
2. Use exact GE slot state, GP, inventory/free slots, world/player location, interface blockers, game canvas dimensions, GE widget bounds, and input state.
3. Pause new click sequences when recent unexpected input is observed.
4. Distinguish PyAutoGUI-generated activity from manual intervention with a short expected-input window around its own mouse/keyboard actions.
5. After unexpected/manual input, wait for an idle period, fetch a fresh RuneLite snapshot, revalidate GE state/bounds/blockers, then resume from state rather than continuing a stale click sequence.
6. Never interpret `UNKNOWN` as `EMPTY`.
7. Keep OCR only for diagnostics/fallback where it cannot override valid RuneLite state.

## Safety behavior

Manual/unexpected input is not an emergency stop. It triggers `PAUSE/RESYNC`. F8 remains the hard emergency stop. Any modal blocker, login transition, missing GE interface when a GE action is required, stale snapshot, or changed canvas/widget bounds blocks new actions until state is fresh again.

## Testing

Java tests cover protocol 3 serialization and input tracker behavior without consuming events. Python tests cover parsing, privacy-safe control-key fields, recent-input detection, automation grace windows, and fail-closed behavior. The full RuneLite Gradle test/build is performed on the user's Windows clone before launch.
