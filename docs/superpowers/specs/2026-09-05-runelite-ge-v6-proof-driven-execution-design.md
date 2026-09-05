# RuneLite GE Auto-Trader V6 Proof-Driven Execution Design

## Goal

Replace the current optimistic UI-action progression with a proof-driven execution model so V6 does not advance merely because an action was emitted. Every critical Grand Exchange transition must be confirmed from live RuneLite state before the state machine proceeds.

The redesign must eliminate repeated `TARGET_UNAVAILABLE` / timing failures around opening offers, collecting, aborting, and setup transitions, while keeping the existing fail-closed safety model and slot ownership/persistence rules.

## Current Failure Pattern

The current state machine changes phase before or at the same time it emits an action. For example, `WAIT_BUY_COLLECT_READY` stores pre-collect state, advances to `WAIT_BUY_COLLECT_RESULT`, and emits `COLLECT`. If the live widget is not ready or cannot be resolved on that tick, dispatch fails and the plugin stops. The state machine has already advanced, so retry and recovery are awkward.

The live GE also uses dynamic widget content. A visible GE details container does not guarantee that a child with an action string such as `Collect` is present at the exact moment the dispatcher searches it. The OSRS interface definition shows that `details_collect` is a layer populated by clientscript rather than a permanent action widget.

## Design Principles

1. **Action intent and UI proof are separate states.** Emitting an action never by itself proves the transition occurred.
2. **Critical actions are retryable while their preconditions still hold.** A transient unresolved target does not immediately stop V6.
3. **State proof, not widget identity, decides success.** Widgets are used to execute; GE API state, setup state, inventory, GP, and visible interface state prove results.
4. **Retries are bounded.** V6 never loops indefinitely on a missing target.
5. **Fail closed on contradiction.** A changed slot identity, wrong setup item, wrong quantity/price, or impossible state still stops/pauses with a specific reason.
6. **One shared setup/details workflow at a time.** Monitoring other slots may continue, but only one slot owns a mutating GE UI workflow.
7. **Persist ownership and recover from restart.** Existing V6 ledger persistence remains authoritative; V6 may also adopt occupied slots 1–3 when enabled as already approved.
8. **One verification entry point.** Focused tests remain for diagnosis, but the user runs one V6 verification script rather than stage-by-stage commands.

## Architecture

### 1. Pending UI Operation

Introduce a small execution-state object for each slot context that records a pending UI operation:

- action type (`OPEN_OFFER`, `COLLECT`, `ABORT_BUY`, `OPEN_BUY`, `OPEN_SELL`, `SELECT_ITEM`, `OPEN_QUANTITY`, `OPEN_PRICE`, `CONFIRM`, etc.)
- first-attempt timestamp
- last-attempt timestamp
- attempt count
- proof baseline where needed (pre-collect inventory, pre-collect GP, prior slot state)

The state machine owns the pending operation. The dispatcher remains stateless and only attempts the requested action.

### 2. Emit, Retry, Prove

For every mutating UI transition:

1. State machine observes a valid source state.
2. State machine creates/keeps a pending operation and emits the action when retry timing allows.
3. Plugin dispatches the action.
4. If dispatch returns `OK`, the state machine still waits for live proof on later ticks.
5. If dispatch returns `EXECUTION_TARGET_UNAVAILABLE`, the plugin records the transient result but does **not** stop immediately while the pending operation remains inside its retry window.
6. If live state proves success, the state machine clears the pending operation and advances.
7. If live state contradicts the expected transition, fail closed.
8. If the retry/proof deadline expires, return a terminal reason such as `UI_STATE_TIMEOUT` / `EXECUTION_TARGET_UNAVAILABLE` and stop for execution failure.

### 3. Retry Policy

Use deterministic bounded retries rather than arbitrary rapid clicks:

- retry interval: at most once per game tick; do not emit duplicate actions multiple times within the same state-machine tick
- target-unavailable retry window: 5 seconds
- proof timeout for setup/details transitions: existing 10-second `UI_PROOF_TIMEOUT`
- no unlimited retry loops
- retry only while the original action remains logically valid from the observed state

`EXECUTION_REJECTED`, F8 stop, login loss, members-world mismatch, blockers, and identity contradictions remain immediate fail-closed conditions.

### 4. Offer Details / Collect Flow

A completed or cancelled buy follows:

`MONITOR_BUY -> OPEN_OFFER pending -> DETAILS_PROVED -> COLLECT pending -> COLLECTION_PROVED -> OPEN_SELL pending`

Proof rules:

- `OPEN_OFFER` is proven when the GE is open and the target slot is still the same completed/cancelled offer and the details panel is visible. The proof must not depend on finding a `Collect` action string.
- `COLLECT` is proven when the target GE slot becomes `EMPTY` and either:
  - inventory quantity of the bought item increases by the filled amount or a positive amount for a partial fill, or
  - for a zero-fill cancelled buy, GP increases by the refund amount/positive refund.
- If the overview reappears before collection proof, the pending workflow returns to/retries `OPEN_OFFER`; it must not treat the overview as a successful collection.
- After collection proof, the actual inventory delta determines the sell quantity.

A completed sell follows the same model:

`MONITOR_SELL -> OPEN_OFFER pending -> DETAILS_PROVED -> COLLECT pending -> GP_COLLECTION_PROVED -> IDLE`

Sell collection is proven by the slot becoming `EMPTY` and GP increasing from the pre-collect baseline.

### 5. Details Visibility Proof

Extend observed state with an explicit offer-details-visible signal, derived from the RuneLite GE `DETAILS` component visibility. This is independent from whether `DETAILS_COLLECT` currently exposes an action.

The bridge should publish the same details visibility so live diagnostics reflect the state machine's proof signal.

### 6. Dispatcher Behaviour

The dispatcher should:

- resolve the safest available widget action when present
- return `EXECUTION_TARGET_UNAVAILABLE` when no executable target exists
- never advance state itself
- never guess success from coordinates alone

For Collect/Abort, resolver search may inspect `DETAILS_COLLECT` / `DETAILS_MODIFY`, `DETAILS`, and the GE window, but missing action strings are treated as a transient dispatch failure while proof-driven retry is active.

### 7. Plugin Execution Result Handling

Change `GeAutoTraderPlugin` so dispatch outcomes are fed back to the state machine.

- `OK`: record that an attempt was sent; continue waiting for proof.
- `EXECUTION_TARGET_UNAVAILABLE`: record transient failure; keep running if the pending operation is retryable and within its deadline.
- `EXECUTION_REJECTED`, `STOPPED_F8`, or a pending operation whose retry deadline has expired: stop fail-closed.

The overlay should show the pending operation and current reason without claiming success before proof.

### 8. Restart Reconciliation

On restart, owned/adopted offers are reconstructed from GE state and the persisted ledger.

If the persisted buy obligation still exists but the GE slot is already empty and the inventory contains the expected item quantity, V6 should reconcile this as an already-collected buy and proceed to create the sell obligation instead of discarding the ledger entry as a slot identity change.

If a persisted sell obligation is already empty and GP collection cannot be proven from persisted baseline, V6 should fail closed rather than inventing profit.

### 9. Safety Boundaries

The redesign must preserve:

- F2P slots 1–3 only
- F8 emergency stop
- no action in members worlds
- no action while bank, world map, dialogs, or drag blockers are active
- exact item/quantity/price proof for setup and placement
- no broad adoption outside slots 1–3
- no automatic sell timeout/reprice behaviour unless separately approved
- one buy abort maximum under the existing buy timeout rule

## Testing Strategy

### Focused tests

Add/adjust tests for:

- pending operation does not advance phase solely because dispatch returned `OK`
- `TARGET_UNAVAILABLE` for Collect is retryable for up to 5 seconds
- Collect target becomes available on a later tick and succeeds without stopping
- overview reappearing before collection causes offer details to be reopened
- completed buy collection proof uses actual inventory delta and creates the sell
- zero-fill cancelled buy uses GP refund proof and returns to idle
- completed sell uses GP proof and returns to idle
- contradiction/identity change still fails closed
- retry deadline expiry stops with a deterministic reason
- restart reconciliation for an already-collected owned buy
- details visibility is read from `InterfaceID.GeOffers.DETAILS`

### End-to-end suite

`GeAutoTraderV6EndToEndTest` remains the consolidated entry point and must include the proof-driven execution tests. `scripts/verify-ge-v6.ps1` remains the single user-facing verification command and must:

1. run the consolidated Auto-Trader suite and GE Bridge suite
2. stop immediately on test failure
3. build `:client:shadowJar` only after tests pass
4. print the final jar path and `GE Auto-Trader V6 verification passed.` only after both stages succeed

## Acceptance Criteria

The redesign is complete when all of the following are true:

- A completed buy can be opened, collected, and converted into a sell without stopping on a transient missing Collect widget.
- V6 never advances from an emitted UI action without later observing proof of the expected state change.
- A transient `EXECUTION_TARGET_UNAVAILABLE` does not stop V6 until its bounded retry deadline expires.
- Returning to the GE overview does not count as successful collection.
- Restarted V6 can reconcile an already-collected owned buy when inventory proves the collection.
- Existing safety, slot ownership, market, setup, timeout, bridge, and persistence tests remain green.
- The single `scripts/verify-ge-v6.ps1` command passes and builds the shaded jar.
