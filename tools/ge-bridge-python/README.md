# RuneLite GE State Bridge v3

The custom RuneLite `GE State Bridge` exposes a read-only protocol-v3 snapshot at:

`http://127.0.0.1:17654/state`

## Authoritative state

When the bridge is healthy, Python can use RuneLite directly for:

- exact GE slot states and collect readiness
- offer item IDs, prices, quantities, and spend
- exact inventory item quantities and GP
- occupied/free inventory slots
- logged-in readiness and bridge tick
- current world and world type / members status
- local-player world X/Y/plane
- actual RuneLite canvas and viewport dimensions
- GE open / offer-setup state
- GE window, setup, and inventory widget bounds
- bank, world map, dialog, chat-input, and widget-drag blockers
- advisory `safeForMouseActions` and `safeForGeMouseActions` flags
- mouse move/click/press/release/wheel timestamps and canvas position
- mouse-button-down mask and last mouse button
- keyboard activity timestamp plus a privacy-safe control-key whitelist
- input idle time for manual-intervention pause/resync logic

## Keyboard privacy

Protocol v3 never exposes typed characters, chat text, usernames, passwords, clipboard contents, or arbitrary key sequences. It only reports keyboard event time and a small control-key whitelist such as `SHIFT`, `CTRL`, `ALT`, `ENTER`, `F8`, `TAB`, `BACKSPACE`, `DELETE`, and arrow keys.

## Why v3 matters for V983

V983 keeps the exact-state improvements from v2 and adds input awareness. RuneLite observes input without consuming or generating it. Python correlates a short expected-input window around its own PyAutoGUI calls; new RuneLite input that does not match an expected automation window is treated as manual intervention.

Manual intervention causes Python to pause the next action, wait for an idle period, fetch a fresh RuneLite snapshot, validate the canvas/world/interfaces/GE state again, and then resume from current state rather than continuing a stale click sequence.

The outer RuneLite sidebar width is still ignored in favour of the actual RuneLite game canvas.

## Safety rule

A missing, stale, malformed, logged-out, not-ready, or incompatible bridge response returns no trusted snapshot. Python must treat that as `UNKNOWN/WAIT`, never as `EMPTY`.

Modal blockers and invalid GE widget bounds fail closed. F8 remains the Python emergency stop.

The RuneLite plugin is strictly read-only. It does not click, type, invoke menu actions, place offers, cancel offers, or collect offers.

## Python client

`runelite_bridge.py` parses protocol v3 into immutable dataclasses. `v981_bridge_adapter.py` exposes compatibility helpers for exact slot/GP/inventory/world/GE state plus input idle time, recent input, mouse position, held buttons, and last safe control key.

Run the smoke check:

```text
python bridge_smoke_check.py
```

Run tests:

```text
python -m unittest -v test_runelite_bridge.py
```

## RuneLite build

From the RuneLite repository root on Windows, on branch `feat/runelite-ge-bridge-v3`:

```text
gradlew.bat :client:test --tests net.runelite.client.plugins.gebridge.*
gradlew.bat :client:shadowJar
```

Start that custom RuneLite build and enable `GE State Bridge`. A fresh logged-in protocol-v3 snapshot is then served from `/state`.
