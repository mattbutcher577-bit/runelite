# RuneLite GE Bridge V5 Implementation Plan

> **For implementation:** use Superpowers test-driven-development for every production change, systematic-debugging for any unexpected failure, and verification-before-completion before claiming a stage complete.

**Goal:** Migrate the existing read-only GE bridge from protocol 4 to protocol 5 with authoritative session/freshness state, exact GE semantics/geometry, normalized offer state, raw/canonical item identity, and RuneLite-local mouse authority.

**Architecture:** Keep the RuneLite plugin strictly observational. Split new protocol sections into focused immutable data classes/readers/trackers, make `GeBridgePlugin` coordinate event-driven refreshes, and keep `GeBridgeHttpServer` localhost GET-only. V5 must fail closed: unknown widgets, stale sections, login transitions, invalid bounds, and contradictory state serialize as explicit unavailable/invalid values rather than guessed data.

**Primary source paths:**

- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgePlugin.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshot.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilder.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeClientState.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeState.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSearchState.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSearchResult.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInputState.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeInputTracker.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeHttpServer.java`
- `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java`
- `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeInputTrackerTest.java`
- `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeHttpServerTest.java`

**Baseline:** the branch currently still serializes protocol 4 and still reads `VarClientID.MESLAYERINPUT` into search state. The migration must preserve V4 as the rollback branch, not make V990 accept V4.

---

## Task 1: Lock protocol-5 session and freshness contract with failing tests

**Files:**
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshot.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilder.java`
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeSessionState.java`

### Step 1: Write the failing tests

Add assertions that a snapshot contains:

- `protocol == 5`
- non-empty `bridgeInstanceId`
- monotonic `snapshotSeq`
- RuneLite `clientTick`
- `lastLoginTick`
- `loginSettled`
- existing generated-at/tick fields remain available for compatibility diagnostics

Add a test that unavailable/logged-out state has `loginSettled == false` and never fabricates a logged-in session.

### Step 2: Run only the snapshot tests and confirm RED

Windows command:

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeSnapshotBuilderTest"
```

Expected failure: protocol remains 4 and the new session/freshness fields/classes are absent.

### Step 3: Implement the minimum protocol/session model

- Change `GeBridgeSnapshotBuilder.PROTOCOL` from 4 to 5.
- Add `GeBridgeSessionState` with immutable/read-only fields.
- Add the session state to `GeBridgeSnapshot` and builder arguments.
- Do not add any action method or mutable gameplay capability.

### Step 4: Re-run the focused test

Expected: PASS.

### Step 5: Commit

```cmd
git add runelite-client/src/main/java/net/runelite/client/plugins/gebridge runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeSnapshotBuilderTest.java
git commit -m "feat: add ge bridge v5 session contract"
```

**Checkpoint:** protocol 5 exists structurally, but no V990 live client should be started yet.

---

## Task 2: Implement bridge instance identity, snapshot sequence, login/hop settling

**Files:**
- Modify: `GeBridgePlugin.java`
- Modify/Create tests: `GeBridgeSnapshotBuilderTest.java`
- Create: `GeBridgeSessionTrackerTest.java` if extracting a tracker proves cleaner
- Optional create: `GeBridgeSessionTracker.java`

### Step 1: Write failing behavioral tests

Cover:

- plugin startup generates a new random `bridgeInstanceId`
- snapshot sequence increments on every published snapshot, independent of game tick
- leaving `LOGGED_IN` clears `loginSettled`
- returning to `LOGGED_IN` does not become settled until the configured number of fresh RuneLite ticks has elapsed
- a world hop/relogin cannot transiently expose an actionable empty-slot state
- `clientTick` comes from `Client.getTickCount()`, not the bridge-local counter

### Step 2: Run the focused tests

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridge*Session*Test" --tests "net.runelite.client.plugins.gebridge.GeBridgeSnapshotBuilderTest"
```

Expected RED until tracker/plugin logic exists.

### Step 3: Implement minimum session tracking

- Generate `bridgeInstanceId` once per plugin startup.
- Increment `snapshotSeq` for every publication.
- Record `lastLoginTick` from RuneLite.
- Use a small explicit settling window after login/hop; make the constant named/tested.
- Publish unavailable/non-actionable state throughout settling.
- Never infer that transient login-time `EMPTY` offer events mean a slot is safely empty.

### Step 4: Re-run and commit

Commit message:

`feat: add ge bridge session resync state`

**Rollback:** checkout the preceding commit if login semantics regress; do not weaken the settle gate.

---

## Task 3: Replace hard-coded client geometry assumptions with exact RuneLite geometry

**Files:**
- Modify: `GeBridgeClientState.java`
- Modify: `GeBridgePlugin.java`
- Modify: `GeBridgeSnapshotBuilderTest.java`
- Create: `GeBridgeGeometryTest.java`

### Step 1: Write RED tests

Require serialization of:

- canvas width/height
- canvas screen X/Y + validity
- `realWidth/realHeight` from `Client.getRealDimensions()`
- `stretchedWidth/stretchedHeight` from `Client.getStretchedDimensions()`
- stretched-enabled state
- viewport fields

Test invalid/zero/missing geometry fails invalid rather than returning historical `773x535` defaults.

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeGeometryTest"
```

### Step 3: Implement

Read all dimensions directly from RuneLite. Keep `canvasScreenPoint()` defensive against hidden/unshowing canvases. Do not calculate Windows DPI by guessing; publish graphics scale/display identifiers only when reliably available.

### Step 4: PASS + commit

`feat: publish exact ge bridge geometry`

---

## Task 4: Remove raw search text and make search state freshness-aware

**Files:**
- Modify: `GeBridgeSearchState.java`
- Modify: `GeBridgeSearchResult.java` if identity fields change
- Modify: `GeBridgePlugin.java`
- Create: `GeBridgeSearchStateTest.java`
- Modify: `GeBridgeHttpServerTest.java`

### Step 1: Write privacy RED tests

Serialize a representative snapshot and assert JSON does **not** contain:

- `query`
- raw `MESLAYERINPUT`
- chat text
- arbitrary key chars
- clipboard/login/password fields

Require search state to contain only `open`, freshness (`updatedTick`, `updatedSeq`) and result records.

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeSearchStateTest" --tests "net.runelite.client.plugins.gebridge.GeBridgeHttpServerTest"
```

Expected RED because current V4 code serializes the query.

### Step 3: Implement

- Delete `query` from the serialized contract.
- Stop reading `VarClientID.MESLAYERINPUT` for bridge output.
- Preserve exact search result item IDs/names/bounds.
- Add section freshness fields.

### Step 4: PASS + commit

`fix: remove raw ge search text from bridge`

---

## Task 5: Make GE search refresh event-driven and post-build

**Files:**
- Modify: `GeBridgePlugin.java`
- Create: `GeBridgeSearchRefreshTest.java`

### Step 1: RED tests

Mock/drive event handlers and assert search freshness advances for:

- `ScriptPostFired` for GE item-search script
- `GrandExchangeSearched`
- normal game tick fallback

Assert the published state comes from the post-build/result widget list and that a stale previous-search sequence cannot be presented as a newly updated search.

### Step 2: Run focused test

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeSearchRefreshTest"
```

### Step 3: Implement minimum handlers

Schedule final result capture on the client thread after search processing. Keep normal tick refresh as a fallback. Do not mutate/consume the search event or alter search results.

### Step 4: PASS + commit

`feat: refresh ge search state from runelite events`

---

## Task 6: Add semantic GE input mode without serializing prompt text

**Files:**
- Create: `GeBridgeGeInputState.java`
- Create: `GeBridgeGeInputMode.java`
- Modify: `GeBridgeSnapshot.java`
- Modify: `GeBridgeSnapshotBuilder.java`
- Modify: `GeBridgePlugin.java`
- Create: `GeBridgeGeInputStateTest.java`

### Step 1: RED tests

Require mapping to exactly:

`NONE`, `ITEM_SEARCH`, `QUANTITY`, `PRICE`, `UNKNOWN`.

Tests should cover:

- maintained RuneLite message-layer/search state
- GE setup context
- internal prompt inspection only where necessary to distinguish quantity vs price
- contradictory/missing context => `UNKNOWN`
- serialized JSON contains only enum/bounds/freshness, never prompt text

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeGeInputStateTest"
```

### Step 3: Implement read-only mapper

Use RuneLite var/widget state as the primary signal. If prompt text must be inspected locally, discard it after deriving the enum.

### Step 4: PASS + commit

`feat: publish semantic ge input mode`

---

## Task 7: Publish exact actionable GE bounds

**Files:**
- Create: `GeBridgeGeActionState.java`
- Create: `GeBridgeGeActionSlot.java`
- Modify: `GeBridgeSnapshot.java`
- Modify: `GeBridgeSnapshotBuilder.java`
- Modify: `GeBridgePlugin.java`
- Create: `GeBridgeGeActionStateTest.java`

### Step 1: RED tests

Require exact bounds/validity for:

- window
- back
- collect
- setup/setup item
- quantity
- price
- confirm
- abort
- per actionable slot 1-3: slot/buy/sell

Test hidden/missing widgets yield `GeBridgeBounds.invalid()` and never a fabricated coordinate.

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeGeActionStateTest"
```

### Step 3: Implement

Prefer stable `WidgetInfo`/`InterfaceID` constants. Where a child must be resolved, isolate that resolver and cover it by tests. Do not call widget operations or menu actions.

### Step 4: PASS + commit

`feat: expose exact ge action bounds`

---

## Task 8: Add authoritative GE-side inventory entries with raw/canonical identity

**Files:**
- Create: `GeBridgeGeInventoryState.java`
- Create: `GeBridgeGeInventoryEntry.java`
- Modify: `GeBridgePlugin.java`
- Modify: `GeBridgeSnapshot.java`
- Inject/use RuneLite `ItemManager` read-only where needed
- Create: `GeBridgeGeInventoryStateTest.java`

### Step 1: RED tests

Require each visible entry to contain:

- inventory slot
- `rawItemId`
- `canonicalItemId`
- quantity
- exact bounds
- section tick/sequence

Cover noted/unnoted canonicalization while ensuring genuinely distinct raw variants remain distinguishable. Missing/ambiguous item state must not match by name alone.

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeGeInventoryStateTest"
```

### Step 3: Implement

Read the GE-side inventory widget/container. Add the dedicated `TRADINGPOST_SELL_*` / `GE_OFFER_*` container probe only as diagnostic metadata until tests plus sanitized live traces prove stable semantics.

### Step 4: PASS + commit

`feat: expose exact ge sell inventory state`

---

## Task 9: Normalize offer events, observe all slots, and protect login bursts

**Files:**
- Create: `GeBridgeOfferEvent.java`
- Create: `GeBridgeOfferEventTracker.java`
- Modify: `GeBridgePlugin.java`
- Modify: `GeBridgeSnapshot.java`
- Create: `GeBridgeOfferEventTrackerTest.java`

### Step 1: RED tests

Cover:

- every RuneLite GE slot is observed
- V5 does not mark extra slots as actionable; action policy remains Python's first-three rule
- identical offer updates de-duplicate
- full-quantity `BUYING/SELLING` immediately followed by `BOUGHT/SOLD` is normalized as one progression
- login/hopping transient `EMPTY` cannot clear persisted/observed identity prematurely
- normalized record contains only slot/item/state/qty/fill/price/spent/tick/seq

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeOfferEventTrackerTest"
```

### Step 3: Implement read-only tracker

Never submit trades or alter the client. Track only event metadata needed by V990.

### Step 4: PASS + commit

`feat: normalize ge offer events`

---

## Task 10: Add RuneLite-local mouse authority state and event ring

**Files:**
- Create: `GeBridgeMouseState.java`
- Create: `GeBridgeMouseEvent.java`
- Create: `GeBridgeMouseTracker.java`
- Modify: `GeBridgePlugin.java`
- Modify: `GeBridgeSnapshot.java`
- Create: `GeBridgeMouseTrackerTest.java`

### Step 1: RED tests

Require:

- canvas X/Y
- inside-canvas flag
- current button
- dragging
- idle ticks
- last press/move/release/wheel times
- last event type/button/position
- monotonic `eventSeq`
- client/canvas focus state
- bounded event ring, e.g. last 32 events
- move/drag/press/release/click/enter/exit/wheel capture
- tracker returns the original event without consuming/modifying it
- no desktop/global coordinates outside RuneLite, no keyboard text

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeMouseTrackerTest"
```

Expected RED: dedicated mouse authority classes do not yet exist.

### Step 3: Implement tracker

Register the new tracker with RuneLite `MouseManager`. Keep the existing privacy-safe key/input tracker separate or refactor shared refresh callbacks only if tests stay clear.

### Step 4: PASS + commit

`feat: add read-only runelite mouse authority state`

---

## Task 11: Publish focus/visibility and section-specific freshness coherently

**Files:**
- Modify: `GeBridgeClientState.java`
- Modify: `GeBridgeInterfaceState.java` if appropriate
- Modify: new V5 section classes
- Modify: `GeBridgePlugin.java`
- Create: `GeBridgeFreshnessTest.java`

### Step 1: RED tests

Require separate updated tick/sequence values for:

- offers
- search
- GE input
- action bounds
- GE inventory
- interfaces
- mouse

Require window/canvas focus/visibility to be false/unknown when unavailable.

### Step 2: Implement event-specific bumping

A newer HTTP response must not automatically mark unchanged sections as newly updated. Track freshness per section.

### Step 3: PASS + commit

`feat: add per-section ge bridge freshness`

---

## Task 12: Harden HTTP privacy/read-only contract

**Files:**
- Modify: `GeBridgeHttpServerTest.java`
- Modify only if needed: `GeBridgeHttpServer.java`
- Modify: snapshot/serialization tests

### Step 1: RED/strengthening tests

Assert:

- server binds to `127.0.0.1` only
- `GET /state` is the only state endpoint
- POST/PUT/PATCH/DELETE return method-not-allowed/not-found and never mutate state
- JSON contains no `query`, `typedText`, `keyChar`, `chat`, `password`, `username`, `clipboard`
- mouse event buffer contains RuneLite-local metadata only

### Step 2: Run

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.GeBridgeHttpServerTest"
```

### Step 3: Minimal fixes only if tests expose a gap

### Step 4: PASS + commit

`test: lock ge bridge v5 privacy contract`

---

## Task 13: Full Java regression and V5 build checkpoint

### Step 1: Run all GE bridge tests locally on Windows

```cmd
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.*"
```

Expected: all V5 bridge tests PASS.

### Step 2: Build custom RuneLite

```cmd
.\gradlew.bat :client:shadowJar
```

Expected: `BUILD SUCCESSFUL` and a current shaded client jar.

### Step 3: Do not claim success without this output

The assistant environment cannot prove the Windows RuneLite build or live widget semantics. If compilation/test/runtime output fails, invoke systematic-debugging before making any patch.

### Step 4: Commit any test-only adjustments separately

`test: verify ge bridge v5 contract`

---

## Task 14: Add a V5 smoke checker before any Python automation

**Files:**
- Create: `tools/gebridge/CHECK_RUNELITE_GE_BRIDGE_V5.ps1`
- Create: `tools/gebridge/CHECK_RUNELITE_GE_BRIDGE_V5.cmd`
- Optional tests: static/script checks under `tools/gebridge/tests/`

The checker must read only `http://127.0.0.1:17654/state` and print:

- protocol
- bridge instance/snapshot/client tick
- login settled
- exact GP/inventory availability
- all observed GE slots, clearly marking slots 1-3 as the only actionable policy slots
- GE semantic input mode
- search result count/freshness
- action-bound validity
- GE-side inventory freshness
- mouse position/event sequence/focus
- privacy-safe failure explanations

It must exit non-zero if protocol != 5, login not settled, required sections missing, or geometry/mouse state is malformed.

### Verification

Run:

```cmd
tools\gebridge\CHECK_RUNELITE_GE_BRIDGE_V5.cmd
```

No clicks are performed.

Commit:

`feat: add ge bridge v5 smoke checker`

---

# V5 Completion Gate

Do not move V990 from protocol-parser tests into live physical-input testing until all of the following are true:

1. Local Windows GE bridge tests pass.
2. `:client:shadowJar` builds successfully.
3. The V5 smoke checker reports protocol 5 and settled session state.
4. Search JSON contains no raw query text.
5. Mouse tracker is demonstrably read-only.
6. Exact GE action bounds and GE-side inventory are valid in sanitized live state at the GE.
7. V4/V989 remains available as a rollback path; V990 never accepts V4.

# Rollback Strategy

- Keep `feat/runelite-ge-bridge-v4` untouched.
- Each V5 feature is a small commit; revert the smallest failing commit rather than weakening fail-closed gates.
- Never patch Python to accept protocol 4 because V5 runtime is temporarily unavailable.
- Never reintroduce OCR or static coordinates as authority to make a V5 test pass.
