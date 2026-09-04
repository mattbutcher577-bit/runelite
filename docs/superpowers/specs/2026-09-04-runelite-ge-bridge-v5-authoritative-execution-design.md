# RuneLite GE Bridge V5 Authoritative Execution Design

## Goal

Replace the remaining OCR- and hard-coded-coordinate-dependent Grand Exchange control path with a RuneLite-authoritative observation and geometry protocol. RuneLite remains strictly read-only; Python remains the only component that performs physical mouse/keyboard input.

The V5/V990 architecture must make every buy, sell, collect, cancel, and recovery decision depend on fresh RuneLite state transitions rather than screen guesses.

## Core rule

**RuneLite = authoritative eyes/state/geometry. Python = decisions + physical input only.**

No Python action may proceed because an OCR/template/colour heuristic says the UI probably looks correct when RuneLite can provide the exact state directly.

## Constraints

- Java remains read-only. It must never click, type, place/cancel/collect an offer, invoke a gameplay menu action, or mutate game state.
- The bridge remains localhost-only on `127.0.0.1:17654` and exposes `GET /state` only.
- Protocol increments to `5`.
- Python rejects protocol 2/3/4 when running V990.
- Missing, malformed, stale, logged-out, semantically incompatible, or contradictory state fails closed.
- F8 remains the hard emergency stop.
- Manual/unexpected input remains a pause/resynchronisation event rather than an automatic emergency stop.
- F2P-only and the existing first-three-GE-slot policy remain unchanged.
- Existing buy timeout/one-abort/sell-no-timeout rules remain unchanged unless a later design explicitly changes them.
- Search/chat privacy is tightened: never publish typed search text, chat text, username/password, clipboard data, arbitrary key characters, or arbitrary key sequences.
- OCR may remain as non-authoritative diagnostics during migration, but it cannot override valid RuneLite state.

## Why V5

V4 proved that exact GE slot state, exact GP, search-result item IDs, and RuneLite geometry are substantially more reliable than the legacy image path. The remaining failures are caused by old execution layers still carrying assumptions about window dimensions, static coordinates, generic chatbox state, item-selection OCR, inventory hover OCR, and ambiguous post-click success.

V5 removes those assumptions at the protocol boundary instead of adding more wrappers around them.

## Architecture

### 1. RuneLite V5 bridge

The bridge publishes a single coherent snapshot with:

- protocol/tick/freshness
- client/world/player state
- real and stretched dimensions
- exact canvas dimensions and screen origin
- interface/blocker state
- exact GE slot states
- exact GE setup state
- semantic GE input mode
- exact actionable GE widget bounds
- exact GE search-result IDs/names/bounds
- exact GE-side inventory item IDs/quantities/bounds
- exact inventory/GP state
- privacy-safe input activity
- per-section update ticks

The bridge never performs an action.

### 2. Python V990 adapter

One parser converts protocol 5 JSON into immutable typed state. It performs structural and semantic validation before the execution layer can read the snapshot.

The adapter owns coordinate conversion. Legacy window coordinates and RuneLite canvas coordinates are never mixed implicitly.

### 3. Python V990 state machine

The old action helpers are no longer the authority. V990 owns explicit action phases and validates a required RuneLite state before and after every physical action.

The state machine emits structured reason codes for every fail-closed stop.

## Protocol 5 changes

## Client geometry

Publish both RuneLite/game geometry and real client geometry explicitly.

`client` adds/retains:

- `canvasWidth`
- `canvasHeight`
- `canvasScreenX`
- `canvasScreenY`
- `canvasScreenPositionValid`
- `realWidth`
- `realHeight`
- `stretchedWidth`
- `stretchedHeight`
- `stretchedEnabled`
- `viewportWidth`
- `viewportHeight`
- `viewportXOffset`
- `viewportYOffset`

`realWidth/realHeight` come from RuneLite `Client.getRealDimensions()` rather than Python-side assumptions.

When stretched mode is active, V990 uses RuneLite's real/stretched dimensions to transform points using the same conceptual model RuneLite itself uses for translated mouse events.

No hard-coded `773x535` requirement remains in the authoritative path.

## Search privacy

V4's raw search query is removed from the JSON contract.

`search` contains only:

- `open`
- `updatedTick`
- `results[]`

Each result contains:

- `index`
- `itemId`
- `name`
- `iconBounds`
- `nameBounds`

The intended search term is already known to Python. RuneLite only needs to prove what item IDs/results are currently present.

## Event-driven search refresh

V5 refreshes search state on:

- `ScriptPostFired` for `ScriptID.GE_ITEM_SEARCH`
- `GrandExchangeSearched`
- normal game ticks
- relevant input refreshes where needed for freshness

The `ScriptPostFired` path captures the result list after the GE search UI has been rebuilt.

Python requires `search.updatedTick` to be newer than or equal to the tick observed before its search-input action. This prevents stale results from a previous candidate being accepted.

## Semantic GE input mode

Add a `geInput` object:

- `mode`: `NONE`, `ITEM_SEARCH`, `QUANTITY`, `PRICE`, `UNKNOWN`
- `updatedTick`
- `promptBounds`
- `inputFieldBounds` where a stable widget exists

The bridge derives mode from RuneLite interface/widget/script/var state. It must fail to `UNKNOWN` rather than guess when the UI does not match a recognised semantic state.

Python rules:

- item-name typing only when mode is `ITEM_SEARCH`
- quantity typing only when mode is `QUANTITY`
- price typing only when mode is `PRICE`
- any mismatch stops the transition and logs a structured reason

## GE actionable bounds

Add a `geActions` object of read-only bounds. Every bound includes the existing `{x,y,width,height,valid}` representation in RuneLite canvas coordinates.

Required bounds:

- `window`
- `back`
- `collect`
- `setup`
- `setupItem`
- `quantityButton`
- `priceButton`
- `confirm`
- `abort`
- per slot 1-3:
  - `slotBounds`
  - `buyButton`
  - `sellButton`

Where the current RuneLite build does not provide a dedicated stable widget constant, V5 may resolve a child from the known GE container, but that resolution must be covered by Java tests and fail invalid rather than invent coordinates.

Python never fabricates a default position when a required bound is invalid.

## GE-side inventory entries

Add `geInventory`:

- `open`
- `updatedTick`
- `entries[]`

Each visible entry contains:

- `inventorySlot`
- `itemId`
- `quantity`
- `bounds`

This is the authoritative sell-selection source.

The Python sell flow selects the intended item by exact `itemId` and RuneLite bounds. Legacy hover/OCR inventory identity is removed from the critical path.

## Per-section freshness

The top-level snapshot remains timestamped, but critical sections also carry update ticks:

- `offersUpdatedTick`
- `search.updatedTick`
- `geInput.updatedTick`
- `geActions.updatedTick`
- `geInventory.updatedTick`
- `interfacesUpdatedTick`

Python records a pre-action tick/fingerprint and requires the relevant section to advance or reach the expected semantic state before accepting a transition.

A fresh HTTP response containing stale section data is not enough.

## Action fingerprints

Before every action, Python records a compact expected-state fingerprint containing only non-sensitive state:

- bridge tick
- GE slot visual/state/itemId/qty/price/spent
- GE open/setup state
- selected setup item ID
- semantic input mode
- relevant widget bounds
- inventory quantity of target item
- GP
- blocker state

Fingerprints are used for transition validation and sanitized replay tests.

## V990 state-transition execution

## Buy flow

For target slot `S` and item `I`:

1. `BUY_PRECHECK`
   - protocol 5/fresh
   - F2P valid
   - player/GE valid
   - target slot exact `EMPTY`
   - no blocker/manual hold
   - required slot-buy bound valid
   - sufficient exact GP/free inventory
2. `BUY_OPEN_SLOT`
   - physical click RuneLite `buyButton` bound
   - require GE setup to open after the pre-click tick
3. `BUY_ITEM_SEARCH_READY`
   - require semantic mode `ITEM_SEARCH`
4. `BUY_TYPE_SEARCH`
   - type planned name
   - require event-driven search state updated after the typing action
5. `BUY_SELECT_ITEM`
   - find result with exact planned `itemId`
   - click exact RuneLite result bounds
   - require `offerSetupItemId == planned itemId`
6. `BUY_QUANTITY_READY`
   - click RuneLite quantity control if required
   - require semantic mode `QUANTITY`
7. `BUY_ENTER_QUANTITY`
   - type exact quantity
   - require return to recognised setup state
8. `BUY_PRICE_READY`
   - click RuneLite price control
   - require semantic mode `PRICE`
9. `BUY_ENTER_PRICE`
   - type exact price
   - require return to recognised setup state
10. `BUY_CONFIRM`
    - re-check latest market/GP/rules
    - click exact confirm bound once
11. `BUY_CONFIRMED`
    - require target slot state to become `BUYING` or immediately `BOUGHT`
    - require exact item ID, total quantity, and offer price to match the planned order
    - buy timer starts only on this proof

No OCR title/form proof is authoritative in this flow.

## Buy monitoring

RuneLite slot state and exact sold/spent fields remain authoritative:

- `BUYING` / visual ORANGE = pending/partial
- `BOUGHT` / visual GREEN = collect-ready completed buy
- `CANCELLED_BUY` / visual RED = cancelled/collectable
- `EMPTY` = empty only when RuneLite says EMPTY

The existing timeout and one-abort rules consume exact RuneLite quantities.

## Collect flow

1. Require exact collect-ready offer state.
2. Snapshot target inventory quantity and relevant slot state.
3. Click exact RuneLite collect bound once.
4. Require the source slot to change appropriately and/or exact target inventory quantity to increase.
5. Any collateral collected items are detected by exact inventory delta and registered as sell-first obligations.

No repeated blind global-collect clicking.

## Sell flow

1. Require a sell obligation with exact target item ID and quantity.
2. Require an exact empty GE slot and valid sell-button bound.
3. Click the RuneLite sell slot control.
4. Require GE-side inventory to be open/fresh.
5. Find exact target `itemId` in `geInventory.entries`.
6. Click its RuneLite bounds.
7. Require selected setup item ID to match target.
8. Use semantic quantity/price modes exactly as in buy flow.
9. Confirm once.
10. Require slot to become exact `SELLING` or `SOLD` with matching item ID/quantity/price.

SELL retains no timeout, abort, or automatic reprice.

## Cancel/abort flow

- Only an existing buy obligation may trigger the one-abort rule.
- Require exact matching slot identity before clicking abort.
- Click exact RuneLite abort bound once.
- Require `CANCELLED_BUY` or a legitimate completed-state transition.
- Never abort because a visual/OCR detector thinks a bar is empty.

## Manual-input handling

The V3 privacy-safe input tracker remains.

V990 tracks expected automation windows around its own physical actions. Unexpected input causes:

1. `MANUAL_HOLD`
2. stop issuing new physical input
3. wait until RuneLite reports buttons released and configured idle period
4. fetch a fresh protocol-5 snapshot
5. rebuild state-machine position from current authoritative RuneLite state
6. resume only from a valid phase boundary

It never resumes a stale mid-sequence click plan.

## Structured reason codes

Generic `V923 ERROR` is replaced in the authoritative execution path with explicit codes. Minimum set:

- `BRIDGE_OFFLINE`
- `BRIDGE_STALE`
- `PROTOCOL_MISMATCH`
- `GAME_NOT_LOGGED_IN`
- `WORLD_NOT_F2P`
- `PLAYER_UNAVAILABLE`
- `PLAYER_NOT_AT_GE`
- `GE_NOT_OPEN`
- `BLOCKER_ACTIVE`
- `MANUAL_INPUT_HOLD`
- `SLOT_NOT_EMPTY`
- `SLOT_IDENTITY_CHANGED`
- `ACTION_BOUNDS_INVALID`
- `GE_SETUP_TIMEOUT`
- `GE_INPUT_MODE_MISMATCH`
- `SEARCH_NOT_UPDATED`
- `SEARCH_RESULTS_EMPTY`
- `SEARCH_ITEM_ID_NOT_FOUND`
- `SEARCH_RESULT_BOUNDS_INVALID`
- `SELECTED_ITEM_MISMATCH`
- `GE_INVENTORY_NOT_UPDATED`
- `SELL_ITEM_NOT_FOUND`
- `QUANTITY_MODE_TIMEOUT`
- `PRICE_MODE_TIMEOUT`
- `CONFIRM_STATE_TIMEOUT`
- `CONFIRMED_ITEM_MISMATCH`
- `CONFIRMED_QUANTITY_MISMATCH`
- `CONFIRMED_PRICE_MISMATCH`
- `COLLECT_STATE_MISMATCH`
- `GP_UNAVAILABLE`
- `INVENTORY_UNAVAILABLE`

Every failure log includes phase, code, tick, slot, planned item ID, and a privacy-safe state summary.

## Sanitized replay traces

V990 may optionally write JSONL traces containing only non-sensitive state required for deterministic testing:

- timestamp/tick
- phase
- reason code
- slot states
- item IDs/quantities/prices/spent
- semantic input mode
- valid widget bounds
- GP/inventory aggregate
- blocker flags
- input timestamps/control-key whitelist values

Never persist:

- chat text
- search text
- username/password
- clipboard
- arbitrary typed characters
- account-identifying UI strings

Replay tests feed these snapshots into the state machine without RuneLite or PyAutoGUI and assert the next permitted action/fail-closed reason.

## OCR and legacy migration

V990 migration occurs in stages:

### Stage A: V5 protocol and parser

Build protocol 5 and parsing/tests while V989 remains available.

### Stage B: authoritative buy path

Route new buys through the V990 transition state machine. Legacy OCR may display diagnostics only.

### Stage C: authoritative collect/sell path

Use exact RuneLite collect and GE-side inventory state. Remove hover/OCR sell identity from the critical path.

### Stage D: authoritative cancel/recovery

Move one-abort and recovery to exact RuneLite states/action bounds.

### Stage E: delete dead legacy authority

After replay and live validation, remove or disable the old GE OCR/colour/hard-coded-coordinate routes that can no longer be reached by production execution.

The huge historical script can temporarily retain legacy code for rollback, but V990's final live lock must prevent those aliases from becoming authoritative again.

## Java implementation units

Expected focused classes/changes:

- `GeBridgeClientState` — real/stretched geometry fields
- `GeBridgeSearchState` — remove query, add update tick
- new `GeBridgeGeInputState`
- new `GeBridgeGeActionState`
- new `GeBridgeGeActionSlot`
- new `GeBridgeInventoryEntry`
- new `GeBridgeGeInventoryState`
- `GeBridgeSnapshot` / `GeBridgeSnapshotBuilder` — protocol 5 sections/freshness
- `GeBridgePlugin` — event-driven refresh and read-only state readers
- Java tests for protocol, privacy, widget state, event freshness, and invalid-bound behaviour

Keep classes narrow rather than putting every new field directly in `GeBridgePlugin`.

## Python implementation units

Do not add another large undifferentiated patch block if avoidable. V990 should introduce focused support modules alongside the generated bot script:

- protocol-5 parser/models
- coordinate transformer
- reason-code/state-policy module
- transition engine
- sanitized replay loader
- smoke checker

The final generated bot may embed/import these as required by the user's launch workflow, but the source-of-truth logic remains testable in small modules.

## Test strategy

### Java unit tests

- protocol exactly 5
- no raw search query in serialized JSON
- no `typedText`/`keyChar`/chat/login/clipboard fields
- real/stretched/canvas geometry serialization
- semantic input-mode mapping
- exact action bounds and invalid-bound fallback
- GE search result IDs/bounds
- GE-side inventory ID/quantity/bounds
- section update ticks
- POST remains 405/read-only

### Python unit tests

- reject protocol 2/3/4
- fail closed on missing/invalid V5 sections
- geometry conversion, including stretched mode
- stale per-section ticks rejected
- exact item-ID selection
- wrong selected item rejected
- semantic mode gating for item/qty/price typing
- manual-input hold/resync
- structured reason codes
- exact slot transitions
- collect inventory deltas
- sell inventory selection
- one-abort rule
- sell no-timeout/no-reprice rule

### Replay tests

Fixtures cover:

- successful complete buy
- search results delayed one tick
- stale previous-item search results
- wrong variant result present first
- user moves mouse mid-search
- setup closes unexpectedly
- quantity prompt never appears
- price prompt never appears
- confirm creates wrong/mismatched offer
- immediate buy completion
- partial buy at timeout
- one abort + partial collect + sell
- completed buy collect with pre-existing stack merge
- collateral global collect inventory
- successful sell
- sell remains pending indefinitely without timeout/reprice
- RuneLite logout/world hop/GE close during every major phase

### Windows/live verification

Before claiming runtime success:

1. run GE bridge Java unit tests on the user's RuneLite clone
2. build custom RuneLite
3. verify `/state` protocol 5 and privacy fields
4. run protocol-5 smoke checker
5. run Python unit/replay tests
6. perform a controlled live GE test with low-risk quantities
7. capture structured logs for each transition

No assistant-side static test is described as a live gameplay test.

## Rollback

- V4/V989 remains available until V5/V990 completes controlled live verification.
- V5 uses a separate branch: `feat/runelite-ge-bridge-v5`.
- No destructive reset of untracked local plugin directories.
- Setup scripts must refuse tracked dirty-state destruction and use explicit branch/fetch logic.

## Success criteria

V5/V990 is complete when:

- every production GE click target comes from fresh RuneLite bounds or an explicitly documented non-GE window control that cannot be exposed by RuneLite
- no authoritative buy/sell/collect/cancel decision depends on OCR, template matching, colour-bar detection, or hard-coded GE coordinates
- raw typed search text is absent from the bridge
- Python types only in a RuneLite-proven semantic input mode
- Python verifies every major action through an exact RuneLite state transition
- exact item ID is proven before quantity/price entry and after confirmation
- startup cannot report PASS with unknown authoritative slots
- failures identify the exact transition/reason instead of generic `V923 ERROR`
- sanitized replay tests cover all critical transaction paths
- Java remains read-only and localhost-only
- existing F8, F2P, fail-closed, buy-timeout, one-abort, and sell-no-timeout/no-reprice safety rules remain intact
