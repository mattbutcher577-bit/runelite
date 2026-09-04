# RuneLite GE Bridge V3 Input Awareness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-safe RuneLite input observations and make the Python GE flipper pause/resync intelligently using the complete v3 state feed.

**Architecture:** A read-only Java input tracker registers with RuneLite `MouseManager`, `MouseWheelListener`, and `KeyManager`, publishes sanitized observations into protocol-v3 snapshots, and never consumes events. Python parses protocol 3, classifies recent unexpected input versus a short self-generated automation grace window, and blocks/resyncs new actions using RuneLite state rather than fixed-window/OCR guesses.

**Tech Stack:** Java 11, RuneLite plugin APIs, Gson localhost HTTP JSON, Python 3, requests, unittest, existing V982/V981 pyautogui state machine.

**Spec:** `docs/superpowers/specs/2026-09-04-runelite-ge-bridge-v3-input-awareness-design.md`

## Global Constraints

- Java is read-only and never performs gameplay input.
- Endpoint remains `GET http://127.0.0.1:17654/state` only.
- Protocol is exactly `3`.
- No typed characters or sensitive keyboard text are serialized.
- Invalid/stale state is `UNKNOWN/WAIT`, never `EMPTY`.
- F8 emergency stop remains unchanged.

---

### Task 1: Input tracker and snapshot model

**Files:**
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInputState.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInputTracker.java`
- Create: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeInputTrackerTest.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshot.java`

**Interfaces:**
- Produces: `GeBridgeInputTracker.snapshot(long nowEpochMs): GeBridgeInputState`
- `GeBridgeInputState` exposes sanitized timestamps, mouse position/buttons/wheel, control key, and input idle time.

- [ ] Write tests first for mouse movement/click tracking, control-key whitelist, typed-character privacy, and event non-consumption.
- [ ] Verify tests fail because tracker/state classes do not exist.
- [ ] Implement tracker and immutable state model.
- [ ] Register the new `input` field on `GeBridgeSnapshot`.
- [ ] Run the GE bridge tests.

### Task 2: Protocol-v3 plugin integration

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilder.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgePlugin.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeHttpServerTest.java`

**Interfaces:**
- Snapshot `protocol == 3`.
- Snapshot includes `input` on available and unavailable states.

- [ ] Update tests first to require protocol 3 and input state serialization.
- [ ] Verify tests fail against v2 production code.
- [ ] Inject/register `MouseManager`, `KeyManager`, and wheel listener via `GeBridgeInputTracker` in startup; unregister on shutdown.
- [ ] Add tracker snapshot to each state refresh.
- [ ] Keep all listeners observational and return original events unchanged.
- [ ] Run all GE bridge tests.

### Task 3: Python v3 parser and intervention logic

**Files:**
- Modify: `tools/ge-bridge-python/runelite_bridge.py`
- Modify: `tools/ge-bridge-python/v981_bridge_adapter.py`
- Modify: `tools/ge-bridge-python/test_runelite_bridge.py`
- Modify: `tools/ge-bridge-python/bridge_smoke_check.py`

**Interfaces:**
- `BridgeInputState` mirrors protocol-v3 input fields.
- `RuneLiteBridgeClient.read_state()` accepts only protocol 3.
- Adapter exposes `input_idle_ms()`, `recent_input(window_ms)`, `mouse_position()`, and existing safety/world/GE helpers.

- [ ] Add failing parser/intervention tests first.
- [ ] Update parser and adapter.
- [ ] Run `python -m unittest -v test_runelite_bridge.py`.

### Task 4: V983 live bot integration

**Files:**
- Create user artifact: `V983_RUNELITE_INPUT_AWARE.py` from V982.

**Interfaces:**
- `v983_bridge_pre_action_guard(action_name)` blocks on stale bridge, modal blockers, invalid GE state/bounds, or unexpected recent input.
- `v983_note_automation_input(...)` creates a short self-input grace window.
- Existing pyautogui functions remain the execution layer; F8 remains unchanged.

- [ ] Append an isolated V983 override layer before the final main guard.
- [ ] Wrap the central mouse/keyboard helpers (not every call site) so self-generated activity is marked.
- [ ] Require a fresh protocol-v3 snapshot before starting a new GE click sequence.
- [ ] On unexpected input: pause, wait for idle, fetch fresh state, revalidate, then return control to existing state machine rather than continuing stale actions.
- [ ] Compile with `python -m py_compile`.

### Task 5: Windows setup and diagnostics

**Files:**
- Create user artifacts: `RUNELITE_GE_BRIDGE_V3_SETUP.ps1`, `RUNELITE_GE_BRIDGE_V3_START.bat`, `CHECK_RUNELITE_GE_BRIDGE_V3.bat`, README, ZIP package.

- [ ] Fetch/check out `feat/runelite-ge-bridge-v3` while preserving untracked local plugins.
- [ ] Run Java GE bridge tests and `:client:shadowJar`; do not launch on failure.
- [ ] Diagnostic must print protocol, canvas/GE/safety state, and input timestamps/position without typed text.
- [ ] Package V983 + setup + launcher + diagnostic + README.
