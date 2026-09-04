# V990 Authoritative Execution Implementation Plan

> **For implementation:** use Superpowers test-driven-development for every production change, systematic-debugging for every unexpected test/runtime failure, and verification-before-completion before claiming V990 works.

**Goal:** Build a new modular Python V990 execution engine that accepts protocol 5 only, uses RuneLite as the sole authority for GE state/geometry/mouse proof, performs physical input through Python only, and preserves F8/F2P/first-three-slot/fail-closed/20m-buy/one-abort/sell-no-timeout rules.

**Architecture:** Create a testable Python package under `tools/ge_v990/`. Keep the protocol parser, coordinate transforms, mouse transaction layer, GE state machine, ledger, replay/diagnostics, and Windows packaging separate. The final standalone Windows artifact may bundle this package, but generated standalone files are outputs rather than the source of truth.

**New source tree:**

```text
tools/ge_v990/
  pyproject.toml
  ge_v990/
    __init__.py
    bridge_client.py
    protocol.py
    models.py
    reason_codes.py
    coordinate_spaces.py
    mouse_models.py
    mouse_policy.py
    mouse_transaction.py
    session_policy.py
    ge_policy.py
    execution_state_machine.py
    buy_flow.py
    collect_flow.py
    sell_flow.py
    cancel_flow.py
    ge_limit_ledger.py
    replay.py
    diagnostics.py
    physical_input.py
    main.py
  tests/
    ...
  fixtures/
    replay/
  scripts/
    CHECK_V990_PROTOCOL5.cmd
    START_V990.cmd
    BUILD_V990_STANDALONE.cmd
```

No current Python/V989 source exists in the repository, so this package is intentionally new rather than pretending generated Downloads artifacts are maintainable source files.

---

## Task 1: Create the package skeleton and test harness

**Files:**
- Create: `tools/ge_v990/pyproject.toml`
- Create: `tools/ge_v990/ge_v990/__init__.py`
- Create: `tools/ge_v990/tests/test_imports.py`

### Step 1: Write a failing import test

Test imports for the future package and assert a public version/engine name such as `V990`.

### Step 2: Run and confirm RED

From repo root:

```cmd
python -m pytest tools\ge_v990\tests\test_imports.py -q
```

Expected: import/module failure.

### Step 3: Add the minimal package metadata

Use Python 3.11+ compatibility. Initial dependencies should be deliberately small:

- `requests` or stdlib HTTP client
- `pyautogui` for physical input
- `pytest` in development/test extras

Do not add ML dependencies here; market intelligence is a later plan.

### Step 4: Re-run PASS and commit

`chore: scaffold modular v990 engine`

---

## Task 2: Build strict protocol-5 typed models and parser

**Files:**
- Create: `ge_v990/models.py`
- Create: `ge_v990/protocol.py`
- Create: `ge_v990/reason_codes.py`
- Create: `tests/test_protocol.py`
- Create: `tests/fixtures_protocol.py` or JSON fixtures under `fixtures/replay/`

### Step 1: RED tests

Cover:

- protocol 5 accepted
- protocols 2/3/4 rejected with `PROTOCOL_MISMATCH`
- missing `bridgeInstanceId`, `snapshotSeq`, `clientTick`, session, GE, inventory, safety, input, search, semantic input, actions, GE inventory, mouse => fail closed
- malformed numeric/bounds fields rejected
- unknown enum values map to explicit `UNKNOWN` or parser failure according to contract
- stale generated-at/snapshot sequence rejected
- raw search query field is not required and, if present unexpectedly, is ignored/rejected as contract drift according to chosen strictness
- immutable typed state produced on success

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_protocol.py -q
```

### Step 3: Implement minimal parser

Use small dataclasses/enums. Keep parsing separate from action policy. Never fill missing GP/slots/bounds with guessed values.

### Step 4: PASS + commit

`feat: add strict protocol 5 parser`

---

## Task 3: Implement bridge client, freshness, and session restart detection

**Files:**
- Create: `ge_v990/bridge_client.py`
- Create: `ge_v990/session_policy.py`
- Create: `tests/test_bridge_client.py`
- Create: `tests/test_session_policy.py`

### Step 1: RED tests

Mock localhost responses and require:

- only `http://127.0.0.1:17654/state`
- timeout/unreachable => `BRIDGE_OFFLINE`
- stale timestamp/sequence => `BRIDGE_STALE`
- changed `bridgeInstanceId` => `LOGIN_RESYNC`
- decreased/restarted `snapshotSeq` => resync
- `loginSettled == false` => no physical GE input
- world hop/login transition => resync
- a resync must observe fresh stable snapshots before returning actionable

### Step 2: Run focused tests

```cmd
python -m pytest tools\ge_v990\tests\test_bridge_client.py tools\ge_v990\tests\test_session_policy.py -q
```

### Step 3: Implement

No retry loop may turn an unknown state into permission to act. Retrying only seeks a new authoritative snapshot.

### Step 4: PASS + commit

`feat: add v990 bridge session resync`

---

## Task 4: Implement explicit coordinate spaces and stretched-mode transforms

**Files:**
- Create: `ge_v990/coordinate_spaces.py`
- Create: `tests/test_coordinate_spaces.py`

### Step 1: RED tests

Model explicit point/bounds types for:

- `RUNELITE_CANVAS`
- `RUNELITE_REAL`
- `RUNELITE_STRETCHED`
- `OS_SCREEN`

Test:

- canvas -> screen conversion using exact canvas screen origin
- stretched <-> real conversion mirroring RuneLite ratios
- integer rounding tolerance
- negative multi-monitor origins
- zero/missing/contradictory dimensions => failure
- geometry fingerprint changes invalidate cached target points
- no hard-coded `773x535`

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_coordinate_spaces.py -q
```

### Step 3: Implement minimal pure functions/classes

No PyAutoGUI calls in this module.

### Step 4: PASS + commit

`feat: add exact v990 coordinate transforms`

---

## Task 5: Add passive OS cursor <-> RuneLite mouse calibration

**Files:**
- Create: `ge_v990/mouse_models.py`
- Create: `ge_v990/mouse_policy.py`
- Create: `tests/test_mouse_policy.py`

### Step 1: RED tests

Given OS cursor and RuneLite mouse state, require:

- close transformed points => calibrated
- mismatch beyond fixed tolerance => `MOUSE_COORDINATE_DESYNC`
- cursor outside canvas => `MOUSE_OUTSIDE_CANVAS`
- focus missing => `WINDOW_NOT_ACTIVE` / `CANVAS_NOT_FOCUSED`
- monitor/geometry fingerprint changes require recalibration
- tolerance does not auto-grow after failure

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_mouse_policy.py -q
```

### Step 3: Implement

Use an injected OS cursor reader for tests; production adapter may use PyAutoGUI position. Calibration performs no click.

### Step 4: PASS + commit

`feat: add passive mouse geometry calibration`

---

## Task 6: Wrap physical input behind one interface and keep F8 emergency stop

**Files:**
- Create: `ge_v990/physical_input.py`
- Create: `tests/test_physical_input.py`

### Step 1: RED tests with fake adapter

Require methods for:

- move cursor
- press/release/click button
- wheel
- type planned string/numeric value
- read OS cursor
- F8 emergency-stop state/callback

Tests must prove the higher layers can use a fake adapter with zero real clicks.

### Step 2: Implement PyAutoGUI adapter

All direct PyAutoGUI calls belong here. No business/state-machine module imports `pyautogui` directly.

F8 must stop new physical input immediately and remain independent of AI/market state.

### Step 3: PASS + commit

`refactor: isolate v990 physical input adapter`

---

## Task 7: Implement mouse action transactions with end-to-end proof

**Files:**
- Create: `ge_v990/mouse_transaction.py`
- Modify: `mouse_models.py`
- Modify: `mouse_policy.py`
- Create: `tests/test_mouse_transaction.py`

### Step 1: RED tests

For an immutable `MouseActionIntent`, cover:

- safe interior target derivation from exact RuneLite bounds
- focus lost before press => no press
- move command but RuneLite never observes target => `MOUSE_TARGET_MISS`
- move observed in target => permit press
- press not observed => `MOUSE_PRESS_NOT_OBSERVED`
- release not observed => `MOUSE_RELEASE_NOT_OBSERVED`
- event sequence stale => `MOUSE_EVENT_STALE`
- bridge instance changes mid-transaction => abandon/resync
- button remains held => `MOUSE_BUTTON_STUCK`
- first press/release observed but semantic transition slow => never issue duplicate click
- every action gets a unique action ID

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_mouse_transaction.py -q
```

### Step 3: Implement stateful transaction coordinator

It may call only the injected physical-input adapter and read fresh RuneLite snapshots. Mouse proof never counts as final GE semantic success.

### Step 4: PASS + commit

`feat: add verified v990 mouse transactions`

---

## Task 8: Add central GE policy and structured reason codes

**Files:**
- Create: `ge_v990/ge_policy.py`
- Expand: `reason_codes.py`
- Create: `tests/test_ge_policy.py`

### Step 1: RED tests for hard constraints

Require new-action denial when any of these fail:

- protocol/session fresh
- F2P world only
- player present
- GE interface exact open
- no blocker/manual hold
- required widget bounds valid
- first-three-slot action policy
- slot exact state required by phase
- exact GP/inventory available
- buy value <= 20m ceiling

Do **not** require a hard-coded Varrock coordinate rectangle when exact GE interface state is valid.

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_ge_policy.py -q
```

### Step 3: Implement pure policy decisions

Return explicit allowed/denied objects with one structured reason code, never booleans with hidden fallback logic.

### Step 4: PASS + commit

`feat: add fail-closed v990 ge policy`

---

## Task 9: Build the generic execution state-machine framework

**Files:**
- Create: `ge_v990/execution_state_machine.py`
- Create: `tests/test_execution_state_machine.py`

### Step 1: RED tests

Require:

- explicit phase enum
- immutable planned obligation/order identity
- every phase names its precondition, physical action (if any), and expected semantic transition
- timeout yields a reason code, not implicit retry
- manual input yields `MANUAL_HOLD`
- F8 yields immediate stopped state
- no stale mid-sequence action resumes after hold; rebuild from fresh RuneLite phase boundary

### Step 2: Implement only generic engine

Do not implement buy/sell details yet.

### Step 3: PASS + commit

`feat: add v990 transaction state machine core`

---

## Task 10: Implement authoritative buy flow

**Files:**
- Create: `ge_v990/buy_flow.py`
- Create: `tests/test_buy_flow.py`
- Add replay fixtures under `fixtures/replay/buy_*.jsonl`

### Step 1: RED tests for full path

Cover the approved sequence:

1. precheck exact empty actionable slot
2. click exact RuneLite buy bound through mouse transaction
3. require setup/item-search semantic mode
4. type planned item name only in `ITEM_SEARCH`
5. require search section freshness newer than pre-type point
6. select exact planned item ID/bounds
7. require setup selected item ID match
8. quantity mode only -> type quantity
9. price mode only -> type price
10. re-check market/GP/policy before confirm
11. confirm once
12. require exact slot `BUYING` or immediate `BOUGHT` with matching item/qty/price
13. timer starts only after semantic proof

Failure fixtures:

- delayed search one tick
- stale prior search
- exact ID absent
- wrong variant first
- setup closes
- quantity mode never appears
- price mode never appears
- confirmation creates mismatched item/qty/price
- mouse focus loss/miss
- immediate completion

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_buy_flow.py -q
```

### Step 3: Implement minimum buy machine

No OCR/template/title/pixel authority. No fallback click coordinate.

### Step 4: PASS + commit

`feat: add authoritative v990 buy flow`

---

## Task 11: Implement buy monitoring and one-abort timeout rule

**Files:**
- Create: `ge_v990/cancel_flow.py`
- Modify: `buy_flow.py`
- Create: `tests/test_buy_monitoring.py`
- Create: `tests/test_cancel_flow.py`

### Step 1: RED tests

Preserve existing behavior:

- configurable/current 20-minute buy timeout policy
- partial exact fills recognized
- only one abort per buy obligation
- abort allowed only for exact matching slot/item obligation
- click exact abort bound once
- require exact `CANCELLED_BUY` or legitimate completed state
- no OCR/colour decision
- full buy before timeout => no abort
- already-aborted obligation cannot abort again after restart/resync

### Step 2: Implement

Persist one-abort status with obligation identity, not transient UI phase.

### Step 3: PASS + commit

`feat: preserve one-abort v990 buy monitoring`

---

## Task 12: Implement authoritative collect flow and inventory-delta reconciliation

**Files:**
- Create: `ge_v990/collect_flow.py`
- Create: `tests/test_collect_flow.py`

### Step 1: RED tests

Require:

- exact collect-ready offer state before collect
- pre-collect inventory target quantities and all observed offer slots captured
- click exact collect bound through verified mouse transaction
- accept only exact source-slot change and/or inventory increase consistent with collection
- collateral collected items discovered from exact inventory deltas
- collateral creates sell-first obligations
- no repeated blind collect clicking
- all-slot observation can explain collateral even though only slots 1-3 are actionable

### Step 2: Implement

### Step 3: PASS + commit

`feat: add authoritative v990 collect reconciliation`

---

## Task 13: Implement authoritative sell flow

**Files:**
- Create: `ge_v990/sell_flow.py`
- Create: `tests/test_sell_flow.py`

### Step 1: RED tests

Require:

- sell obligation has exact raw/canonical identity and quantity
- exact empty actionable slot
- exact sell-button bound
- GE-side inventory fresh after entering sell setup
- select exact target by item ID/canonical rules, never name alone
- require setup selected item ID match
- quantity and price typing only in their semantic modes
- confirm once
- require exact `SELLING`/`SOLD` matching item/qty/price
- **no sell timeout**
- **no automatic sell reprice**
- no abort path for sells

### Step 2: Run

```cmd
python -m pytest tools\ge_v990\tests\test_sell_flow.py -q
```

### Step 3: Implement

### Step 4: PASS + commit

`feat: add authoritative v990 sell flow`

---

## Task 14: Add persistent exact GE buy-limit ledger

**Files:**
- Create: `ge_v990/ge_limit_ledger.py`
- Create: `tests/test_ge_limit_ledger.py`
- Create runtime data directory contract under `tools/ge_v990/data/` but keep generated ledger DB/file gitignored

### Step 1: RED tests

Cover:

- known total limit from metadata
- actual fill delta increments ledger, not requested quantity
- partial fills
- full-quantity BUYING then BOUGHT de-duplicates
- four-hour window reset
- unknown limit stays unknown
- remaining limit caps candidate quantity
- persisted ledger survives process restart
- material contradiction with RuneLite state blocks that item until reconciliation

### Step 2: Implement with a small durable format

Prefer stdlib SQLite initially for atomic durability unless later evidence requires DuckDB. Market analytics can use a separate store.

### Step 3: PASS + commit

`feat: add exact persistent ge limit ledger`

---

## Task 15: Add manual-input hold/resync using RuneLite input and mouse events

**Files:**
- Modify: `session_policy.py`
- Modify: `mouse_policy.py`
- Modify: `execution_state_machine.py`
- Create: `tests/test_manual_hold.py`

### Step 1: RED tests

Require unexpected mouse move/press/wheel or whitelisted control-key input outside the current expected automation window to:

1. enter `MANUAL_HOLD`
2. stop new physical input
3. wait for all buttons released + configured idle period
4. fetch fresh V5 state
5. rebuild semantic phase from current RuneLite state
6. resume only at a valid boundary

Never classify human vs bot biometrically.

### Step 2: PASS + commit

`feat: add v990 manual input resynchronization`

---

## Task 16: Add sanitized replay traces and deterministic offline regression suite

**Files:**
- Create: `ge_v990/replay.py`
- Create: `ge_v990/diagnostics.py`
- Create: `tests/test_replay.py`
- Add sanitized JSONL fixtures under `fixtures/replay/`

### Step 1: RED tests

Replay full buy/collect/sell/cancel/mouse/session scenarios without RuneLite or PyAutoGUI.

Trace schema may contain:

- tick/sequence/session ID
- GE slots/item IDs/qty/price/spent
- semantic input mode
- bounds validity
- GP/inventory aggregate
- mouse RuneLite-local event data
- reason codes/phases

Trace schema must reject/strip:

- chat/search text
- username/password
- clipboard
- arbitrary keyboard chars
- unrelated desktop coordinates

### Step 2: Implement

### Step 3: Run all replay tests

```cmd
python -m pytest tools\ge_v990\tests\test_replay.py -q
```

### Step 4: Commit

`test: add sanitized v990 replay suite`

---

## Task 17: Property/state-machine testing for fail-closed invariants

**Files:**
- Modify: `tools/ge_v990/pyproject.toml` dev extras
- Create: `tests/test_properties.py`

Use Hypothesis only in the test dependency set if adopted.

Properties:

- no malformed snapshot permits physical action
- no non-empty/unknown slot is treated as empty
- no action targets slot > 3
- no mouse press occurs before move proof/focus proof
- no duplicate click after observed press/release
- no sell path emits timeout/reprice/abort
- at most one abort per buy obligation
- protocol != 5 never reaches action layer
- F8 state dominates every phase

Commit:

`test: add v990 fail-closed property tests`

---

## Task 18: Create live-lock wiring that cannot fall back to legacy OCR authority

**Files:**
- Create: `ge_v990/main.py`
- Create: `tests/test_live_wiring.py`

### Step 1: RED static/integration tests

Assert production wiring references:

- protocol-5 bridge parser
- RuneLite exact GE state
- RuneLite action bounds
- RuneLite GE inventory
- verified mouse transaction layer

Assert production wiring does **not** call any legacy OCR/pixel/template state function for:

- GP
- slots
- item identity
- GE semantic phase
- action permission

OCR may be wired only as diagnostics if retained externally.

### Step 2: Implement main coordinator

Market candidate provider is injected; use deterministic existing/baseline provider initially.

### Step 3: PASS + commit

`feat: wire v990 authoritative execution engine`

---

## Task 19: Windows smoke launchers and standalone packaging

**Files:**
- Create: `scripts/CHECK_V990_PROTOCOL5.cmd`
- Create: `scripts/START_V990.cmd`
- Create: `scripts/BUILD_V990_STANDALONE.cmd`
- Create: packaging config/script as needed
- Create static tests for launcher safety

Rules:

- launchers stay open on error
- checker refuses protocol != 5
- checker refuses unsettled session/malformed mouse geometry
- start script runs protocol checker before Python main
- standalone artifact bundles modular source, not a hand-edited generated monolith
- no embedded API secrets
- no V4 compatibility fallback

Verification:

```cmd
python -m pytest tools\ge_v990\tests -q
```

Then build standalone package with the chosen packager and verify its archive/executable hash.

Commit:

`build: add v990 windows packaging`

---

## Task 20: Controlled live verification gate

This stage occurs only after RuneLite V5 local Java tests/build and all Python tests pass.

### Phase A: read-only smoke

- Start custom V5 RuneLite.
- Run V5 checker.
- Run V990 in `--dry-run` / no-physical-input mode.
- Verify session, all slot states, GP, GE semantic state, action bounds, GE inventory, and mouse calibration.

### Phase B: mouse proof without GE transaction

- Move to one known safe in-client target with no gameplay effect if such a test surface is explicitly provided; otherwise skip rather than invent a click.
- Verify RuneLite observes target/move/press/release only if the test is safe and intentionally initiated.

### Phase C: controlled GE test

Use the smallest practical test trade and verify one complete buy -> collect -> sell lifecycle.

Do not claim live success until the user provides the actual local logs/state trace proving the transitions.

If any runtime behavior differs from tests, use systematic-debugging before changing code.

---

# V990 Completion Gate

V990 is not complete until:

1. `python -m pytest tools\ge_v990\tests -q` passes.
2. Protocol 2/3/4 rejection is verified.
3. Replay/property tests prove hard invariants.
4. Local V5 Java tests/build pass on Windows.
5. V5 checker reports exact valid state.
6. Dry-run makes zero physical input.
7. F8 emergency stop works in the production physical-input adapter.
8. First-three-slot action policy is enforced centrally.
9. 20m buy ceiling, one-abort rule, and sell no-timeout/no-reprice are covered by tests.
10. No OCR/static-coordinate fallback can become authoritative.
11. Controlled live logs prove the intended transitions before broader use.

# Rollback Strategy

- V4/V989 remains separate and untouched during V5/V990 development.
- V990 rejects V4 rather than silently operating with reduced state.
- Every execution subsystem is committed independently so the smallest failing component can be reverted.
- If V5 semantics are unavailable, V990 stops; it does not substitute OCR or guesses.
