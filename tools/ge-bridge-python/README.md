# RuneLite GE State Bridge v2

The custom RuneLite `GE State Bridge` exposes a read-only protocol-v2 snapshot at:

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

## Why v2 matters for V981

The old V981 preflight can reject a RuneLite window when the outer client area is wider than the game canvas, for example when the RuneLite sidebar is visible. Protocol v2 reports the actual RuneLite game canvas directly, so Python can validate the game surface without forcing the entire application window to `773x535`.

## Safety rule

A missing, stale, malformed, logged-out, not-ready, or incompatible bridge response returns no trusted snapshot. Python must treat that as `UNKNOWN/WAIT`, never as `EMPTY`.

Modal blockers also fail closed for mouse actions. Widget bounds must be valid before Python trusts them for GE geometry.

The RuneLite plugin is still strictly read-only. It does not click, type, invoke menu actions, place offers, cancel offers, or collect offers.

## Python client

`runelite_bridge.py` parses protocol v2 into immutable dataclasses. `v981_bridge_adapter.py` exposes compatibility helpers for exact slot state and GP plus v2 readiness, canvas, GE bounds, world/player, inventory-slot, and modal-blocker state.

Run the smoke check:

```text
python bridge_smoke_check.py
```

Run tests:

```text
python -m unittest -v test_runelite_bridge.py
```

## RuneLite build

From the RuneLite repository root on Windows, on branch `feat/runelite-ge-bridge-v2`:

```text
gradlew.bat :client:test --tests net.runelite.client.plugins.gebridge.*
gradlew.bat :client:shadowJar
```

Start that custom RuneLite build and enable `GE State Bridge`. A fresh logged-in protocol-v2 snapshot is then served from `/state`.
