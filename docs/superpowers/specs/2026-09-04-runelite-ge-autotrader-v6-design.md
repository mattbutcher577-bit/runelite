# RuneLite GE Auto-Trader V6 Design

## Goal

Replace the V5 Java-read-only + Python-execution architecture with a Java-first private RuneLite fork in which the plugin owns the complete Grand Exchange trading state machine.

V6 must preserve the user's intended behaviour: automatically scan the F2P market, select opportunities, place buy offers, monitor fills, collect items/GP, place sell offers, recycle capital, track four-hour limits, and continuously reuse GE slots 1-3 until stopped.

## Core architecture

**RuneLite V6 owns state, decisions, execution, and verification.**

Python is removed from the live execution path. The localhost bridge may remain temporarily for diagnostics during migration, but no live trade may depend on Python, screen coordinates, PyAutoGUI, OCR, or HTTP round trips.

The V6 plugin consists of narrow Java components:

- `GeAutoTraderPlugin` - lifecycle, subscriptions, F8 stop, orchestration only.
- `GeTradeStateMachine` - deterministic slot/obligation state transitions.
- `GeExecutionService` - performs one exact RuneLite menu/widget action at a time and returns an execution token for later verification.
- `GeStateReader` - reads exact RuneLite GE, inventory, GP, login/world, blocker, and setup state.
- `GeMarketService` - fetches and caches public OSRS Wiki mapping/latest/5-minute price data.
- `GeOpportunitySelector` - filters/ranks safe F2P candidates using deterministic rules.
- `GeLimitLedger` - tracks actual filled quantities against four-hour limits.
- `GeTradeLedger` - records active buy/sell obligations and actual fills.
- `GeSafetyPolicy` - central fail-closed validation before every state transition and execution.
- `GeAutoTraderConfig` - user-facing configuration and hard safety ceilings.

No single class should become a replacement monolith for the removed Python runtime.

## Private-fork scope

V6 is for the user's private/custom RuneLite fork. It is not designed for Plugin Hub submission.

## Execution model

V6 executes through RuneLite's own client/menu/widget interaction APIs rather than physical mouse coordinates.

Execution rules:

1. Resolve the exact RuneLite widget/menu target from current client state.
2. Re-read safety and slot identity immediately before action.
3. Invoke exactly one action.
4. Do not assume success from the call itself.
5. Wait for a later RuneLite state transition proving the expected result.
6. If the expected transition does not occur, fail closed and resynchronise from current state.

V6 must never fabricate widget coordinates or fall back to Python/Robot/OCR execution.

## Hard safety rules

These are project-wide invariants:

- F2P worlds only. Members worlds immediately pause trading.
- Only GE slots 1, 2, and 3 are used by automation.
- F8 is the hard emergency stop and disables further automated game actions immediately.
- A stopped session does not auto-resume without explicit restart/toggle.
- Maximum buy price per item/order remains capped by configuration; default hard ceiling is 20,000,000 GP per unit/order policy as implemented by the existing V990 ceiling semantics.
- A buy obligation may be automatically aborted at most once.
- Buy timeout is 20 minutes from proved offer placement, not from candidate selection.
- Sell offers have no automatic timeout, no automatic abort, and no automatic repricing.
- No action is permitted while logged out, during login resync, on members worlds, with contradictory GE state, or while an unexpected blocking interface is active.
- Every action is verified by a subsequent exact RuneLite state transition.
- AI/ML cannot authorize or execute a game action. Any model remains shadow-only and advisory.

## Slot model

Automation owns slots 1-3 independently.

Each slot is represented by an immutable observation plus a mutable obligation record.

Observed slot states include at minimum:

- `EMPTY`
- `BUYING`
- `BOUGHT`
- `CANCELLED_BUY`
- `SELLING`
- `SOLD`
- `CANCELLED_SELL` if RuneLite exposes it distinctly
- `UNKNOWN`

Each active obligation stores:

- slot number
- side (`BUY` or `SELL`)
- canonical item ID
- item name for display only
- intended quantity
- intended price
- proved placed timestamp/tick
- actual filled quantity
- actual spent/received GP where available
- buy abort count
- parent obligation ID for buy->sell lifecycle

Unknown or contradictory slot identity pauses that slot rather than guessing.

## Automatic buy flow

For an empty owned slot:

1. Refresh market data if stale.
2. Calculate available GP after reserving capital for active obligations.
3. Apply F2P, volume, spread, tax, ROI, price, four-hour-limit, and capital filters.
4. Rank candidates deterministically.
5. Select one candidate not already conflicting with another owned-slot obligation.
6. Open the exact empty slot's Buy action through RuneLite.
7. Verify GE buy setup is open.
8. Select the exact item ID through current GE search/setup state.
9. Set exact quantity.
10. Set exact price.
11. Re-read setup values and require exact item ID, quantity, price, and BUY side.
12. Invoke Confirm once.
13. Require the target slot to become `BUYING` or immediately `BOUGHT` with matching item ID, quantity, and price.
14. Only then create/start the buy timeout obligation.

Any mismatch stops that transition and leaves the state machine to resynchronise from actual RuneLite state.

## Buy monitoring and one-abort rule

While a buy is `BUYING`, V6 records actual fill changes from RuneLite offer state.

At 20 minutes after proved placement:

- if completed, continue to collect;
- if still pending/partial and abort count is 0, re-check exact slot identity and invoke one Abort action;
- increment abort count only after RuneLite proves the cancellation transition;
- never abort the same buy obligation twice.

Partial fills are preserved and become sell obligations after collection.

## Collect flow

When a buy reaches `BOUGHT` or `CANCELLED_BUY` with collectable items:

1. Capture exact inventory quantity of the target item and GP before collection.
2. Invoke the exact collect action once.
3. Require an inventory/GP/slot transition proving collection.
4. Calculate actual received item quantity from exact deltas.
5. Register the received quantity in `GeLimitLedger` as an actual buy fill.
6. Create or update a sell obligation for the actual collected quantity.

When a sell reaches `SOLD`, collect proceeds once and verify GP/slot change.

The state machine does not repeatedly spam a global collect button.

## Automatic sell flow

For a sell obligation and an empty owned slot:

1. Verify exact target item and quantity exist in inventory.
2. Open the exact slot's Sell action.
3. Select the exact item ID from the GE-side inventory/setup interface.
4. Set exact sell quantity.
5. Set exact sell price chosen at the moment the sell offer is first created.
6. Re-read setup values and require exact item ID, quantity, price, and SELL side.
7. Confirm exactly once.
8. Require slot state `SELLING` or immediately `SOLD` with matching item ID/quantity/price.

After placement, V6 never automatically changes that sell price, never times it out, and never aborts it automatically.

## Market data and candidate selection

`GeMarketService` uses public OSRS Wiki real-time price endpoints already used by the native V990 prototype:

- item mapping
- latest prices
- 5-minute prices/volumes

Market retrieval runs off the RuneLite client thread and publishes an immutable cache back to the state machine.

Candidate selection is deterministic and testable. Minimum filters:

- item is F2P/tradeable and has a valid canonical ID
- valid high/low prices
- positive after-tax spread
- configurable minimum ROI
- configurable minimum recent volume/liquidity
- price fits available/reserved capital
- remaining four-hour buy limit is positive
- candidate does not exceed hard buy-price ceiling
- no duplicate/conflicting obligation unless explicitly allowed by policy

Ranking can include after-tax unit profit, ROI, recent volume, expected capital efficiency, and simple momentum/volatility penalties. Model-based scoring is optional shadow telemetry only.

## Four-hour limit ledger

Limits are based on actual fills, not planned quantities.

For each item, store timestamped actual bought quantities. Expire entries older than four hours. Remaining allowance equals configured/wiki limit minus unexpired actual fills.

An aborted partial buy only consumes limit for the quantity actually filled.

## Capital accounting

V6 uses exact RuneLite GP plus obligation reservations.

Before a new buy, reserve enough GP for already placed or currently-being-confirmed buy obligations. Do not oversubscribe GP across slots.

Collected sell GP becomes reusable only after RuneLite proves the GP increase/collection transition.

## Emergency stop and manual control

F8 behavior:

- immediately set a volatile/atomic stopped flag;
- prevent any subsequent automatic client/menu action;
- cancel pending scheduler callbacks that would execute actions where practical;
- continue read-only state observation/logging if RuneLite remains open;
- display `STOPPED - F8` in plugin status.

The plugin also exposes an explicit enable/disable control in config or panel state. Startup defaults to disabled unless the existing user workflow explicitly chooses auto-start in the launcher/config.

## Threading

- RuneLite client state reads and game/menu actions execute on the client thread.
- Network price requests execute on a dedicated background executor.
- Market results are immutable snapshots passed back to the client-thread state machine.
- No network request, sleep, blocking wait, or large recursive widget traversal may run on the RuneLite client thread.
- State transitions are event/tick-driven, not busy loops.

## Error handling

V6 uses structured reason codes and visible status, including at minimum:

- `STOPPED_F8`
- `DISABLED`
- `GAME_NOT_LOGGED_IN`
- `WORLD_NOT_F2P`
- `LOGIN_RESYNC`
- `GE_NOT_OPEN`
- `BLOCKER_ACTIVE`
- `SLOT_IDENTITY_CHANGED`
- `SETUP_NOT_OPEN`
- `SETUP_ITEM_MISMATCH`
- `SETUP_QUANTITY_MISMATCH`
- `SETUP_PRICE_MISMATCH`
- `SETUP_SIDE_MISMATCH`
- `CONFIRM_STATE_TIMEOUT`
- `COLLECT_STATE_MISMATCH`
- `INVENTORY_MISMATCH`
- `INSUFFICIENT_GP`
- `BUY_LIMIT_EXHAUSTED`
- `MARKET_DATA_UNAVAILABLE`
- `MARKET_DATA_STALE`
- `EXECUTION_TARGET_UNAVAILABLE`
- `EXECUTION_REJECTED`

Errors pause only the affected transition where possible. Global safety violations pause the whole trader.

## Logging and privacy

Log only trading/state information needed for debugging:

- timestamp/tick
- state-machine phase
- slot number/state
- canonical item ID/name
- quantities/prices/fills
- GP aggregates
- reason code
- market snapshot age

Do not log account credentials, chat text, clipboard data, arbitrary key input, or private messages.

## User-visible status

The plugin should expose a compact status panel or log surface showing:

- `RUNNING`, `PAUSED`, or `STOPPED`
- current F2P world
- GP available/reserved
- slot 1-3 state and obligation
- current candidate/market age
- four-hour remaining limit for active items
- last structured reason code
- F8 reminder

This replaces the Python console as the primary runtime status surface.

## Migration from V5/V990

Migration is staged so failures remain diagnosable:

### Stage 1 - V6 state/market core

Create Java state-machine, market, limit, ledger, and safety components with no execution enabled. Feed them existing V5 state readers and unit-test deterministic decisions.

### Stage 2 - exact Java buy execution

Add RuneLite execution service for opening a buy slot, item selection, quantity, price, and confirm. Verify every transition from RuneLite state. Keep execution behind a config feature flag.

### Stage 3 - collect and sell execution

Add exact collection and sell state machine, preserving no-timeout/no-reprice sell policy.

### Stage 4 - timeout/abort and slot scheduler

Enable 20-minute buy timeout, one-abort rule, and independent slot 1-3 scheduling.

### Stage 5 - remove Python authority

Disable/remove Python execution aliases and launcher dependency. Keep V5 bridge only if useful for diagnostics; otherwise remove it after Java tests and live validation pass.

## Test strategy

All implementation uses TDD.

### Unit tests

- F2P/member-world safety gate
- stopped/disabled gate
- slot 1-3 ownership only
- exact setup item/quantity/price/side validation
- buy placed only after exact slot proof
- immediate buy completion
- partial fill accounting
- 20-minute timeout calculation
- exactly one automatic buy abort
- zero automatic sell aborts
- zero automatic sell reprices
- collect delta validation
- buy->sell obligation creation
- actual-fill four-hour ledger and expiry
- capital reservations across three slots
- candidate filters and ranking
- market stale/unavailable fail-closed behavior
- F8 prevents execution after stop flag is set

### Integration-style RuneLite tests

Use mocked/fake client/widget/menu state to prove:

- one execution call per state-machine transition
- no action when expected target is unavailable
- no second confirm from repeated ticks
- wrong/mismatched slot after action is rejected
- login/world changes pause immediately
- market background work never blocks the client thread

### Live validation sequence

Live validation must progress conservatively:

1. shadow-only candidate/status mode;
2. one-slot buy with tiny quantity/value;
3. one-slot buy->collect->sell lifecycle;
4. timeout/partial-fill path;
5. three-slot scheduling;
6. normal continuous operation.

Do not claim a later stage is working until the previous stage has fresh runtime evidence.

## Definition of done

V6 is complete only when:

- Python is not required for live execution;
- the custom RuneLite plugin automatically buys, monitors, collects, sells, and recycles GP on F2P using slots 1-3;
- four-hour limits use actual fills;
- the 20-minute buy timeout and one-abort rule are enforced;
- sells never auto-timeout, auto-abort, or auto-reprice;
- F8 reliably prevents any further automated action;
- every execution step is validated by subsequent RuneLite state;
- focused Java tests pass;
- the full relevant RuneLite test/build command passes on the user's Windows environment;
- live validation has demonstrated at least one complete buy->collect->sell cycle before enabling unrestricted continuous mode.
