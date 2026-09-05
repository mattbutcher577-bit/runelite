# RuneLite GE Bridge Protocol v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the read-only RuneLite bridge to protocol v2 and make the Python flipper consume authoritative RuneLite readiness, interface, viewport, GE widget-bounds, inventory, and safety state.

**Architecture:** The RuneLite plugin builds one immutable snapshot on the client thread and serves that cached snapshot over loopback HTTP. New small value objects represent client, player, interface, GE bounds, inventory summary, and safety state. Python parses those sections into immutable dataclasses and V981 uses them to bypass obsolete fixed-size/OCR readiness checks when fresh bridge state is available.

**Tech Stack:** Java 11, RuneLite API, Gson, JUnit 4, Python 3, requests, unittest.

**Spec:** `docs/superpowers/specs/2026-09-04-runelite-ge-bridge-v2-design.md`

## Global Constraints

- Java plugin is read-only; no click/type/menu/gameplay automation.
- HTTP remains `127.0.0.1:17654`, GET-only `/state`.
- Protocol is exactly `2`.
- Stale or invalid bridge data fails closed.
- Python keeps F8 and all existing mouse automation.
- Required click bounds must be valid before Python may trust bridge geometry.

---

### Task 1: Add protocol-v2 snapshot value objects and tests

**Files:**
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeBounds.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeClientState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgePlayerState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInterfaceState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInventoryState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSafetyState.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshot.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java`

**Interfaces:**
- Produces immutable Lombok `@Value` objects matching the v2 JSON schema.
- `GeBridgeBounds.invalid()` returns `(-1,-1,0,0,false)`.

- [ ] Write tests asserting protocol 2 and all v2 sections serialize from a built snapshot.
- [ ] Run `./gradlew :client:test --tests net.runelite.client.plugins.gebridge.GeBridgeSnapshotBuilderTest` and confirm the new assertions fail.
- [ ] Add the value objects and extend `GeBridgeSnapshot`.
- [ ] Re-run the focused test.
- [ ] Commit the task.

### Task 2: Build RuneLite client/player/interface/GE/safety state

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilder.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgePlugin.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java`

**Interfaces:**
- Builder consumes existing offer/inventory data plus pre-read immutable state values supplied by the plugin.
- Plugin reads `Client`, `Player`, `WorldType`, `WidgetInfo`, widget bounds, viewport dimensions, and interface visibility only on the client thread.

- [ ] Add failing tests for inventory occupied/free slots and safety boolean rules.
- [ ] Run the focused Java test and confirm failure.
- [ ] Change `PROTOCOL` to `2` and extend the builder signature to accept the v2 state objects and tick counter.
- [ ] In `GeBridgePlugin`, collect world number/types, members-world flag, canvas/viewport dimensions, top-level interface, FPS, local-player world location, GE/bank/world-map/dialog/chat-input visibility, drag state, and GE widget bounds.
- [ ] Derive `bridgeReady`, `modalBlocker`, `safeForMouseActions`, and `safeForGeMouseActions` without invoking any actions.
- [ ] Increment a logged-in game-tick counter on each `GameTick`; reset it when leaving `LOGGED_IN`.
- [ ] Re-run focused Java tests.
- [ ] Commit the task.

### Task 3: Update Python protocol-v2 parser and tests

**Files:**
- Modify: `tools/ge-bridge-python/test_runelite_bridge.py`
- Modify: `tools/ge-bridge-python/runelite_bridge.py`
- Modify: `tools/ge-bridge-python/v981_bridge_adapter.py`

**Interfaces:**
- `BridgeSnapshot` gains `tick`, `client`, `player`, `interfaces`, `ge`, `inventory_state`, and `safety`.
- Helpers expose `safe_for_mouse_actions`, `safe_for_ge_mouse_actions`, `canvas_size`, `ge_window_bounds`, and blocker state.

- [ ] Change tests to protocol 2 and add valid v2 sections.
- [ ] Add tests for stale/logged-out/protocol-1 rejection, modal blockers, bounds parsing, and safe helpers.
- [ ] Run `python -m unittest tools/ge-bridge-python/test_runelite_bridge.py` and confirm expected failures.
- [ ] Implement the v2 dataclasses/parser/helpers.
- [ ] Update `V981RuneLiteStateAdapter` to surface the new readiness/geometry helpers.
- [ ] Re-run Python tests.
- [ ] Commit the task.

### Task 4: Update bridge smoke checker and docs

**Files:**
- Modify: `tools/ge-bridge-python/bridge_smoke_check.py`
- Modify: `tools/ge-bridge-python/README.md`

**Interfaces:**
- Smoke checker prints game/world/canvas, safety flags, GP, first three exact slots, GE interface status, and GE bounds.

- [ ] Update the smoke checker for protocol v2.
- [ ] Update README setup and fail-closed behavior.
- [ ] Run `python -m py_compile` on all three Python bridge modules.
- [ ] Commit the task.

### Task 5: Patch the live V981 bridge-enabled copy

**Files:**
- Generate user artifact from `/mnt/data/V981_RUNELITE_BRIDGE_ENABLED.py` as `/mnt/data/V982_RUNELITE_STATE_BRIDGE.py`.

**Interfaces:**
- Preserve all existing mouse automation and F8 behavior.
- Add a final override block before the final main guard that reads protocol v2.
- Use protocol-v2 exact GE slot state and GP.
- Use `client.canvasWidth/canvasHeight` and valid GE widget bounds as authoritative geometry/readiness.
- Ignore the old 773x535 RuneLite geometry failure when a fresh v2 snapshot is valid.
- Fail closed on bridge loss, stale data, modal blocker, or invalid required GE bounds.

- [ ] Create a small failing/static validation script that confirms the source contains the final main guard and expected old bridge hook names.
- [ ] Generate the patched V982 copy with the v2 override block immediately before the final main guard.
- [ ] Run `python -m py_compile /mnt/data/V982_RUNELITE_STATE_BRIDGE.py`.
- [ ] Package the Python file plus setup/readme into a ZIP.

### Task 6: Final verification

**Files:** all modified files.

- [ ] Run focused Java GE bridge tests if CI/local Gradle is available; otherwise state that Java compilation remains unverified.
- [ ] Run Python unit tests.
- [ ] Run Python `py_compile` for the generated V982 file.
- [ ] Compare feature branch against the previous bridge branch and review changed-file list.
- [ ] Keep the work unmerged until verification is satisfactory.
