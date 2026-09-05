# RuneLite GE Auto-Trader V6 Stabilization Design

## Status and scope

This document is an implementation addendum to `docs/superpowers/specs/2026-09-04-runelite-ge-autotrader-v6-design.md`.

It covers the stabilization pass approved after live testing exposed a series of adjacent runtime assumptions in the existing V6 buy/sell flow. It does not replace the original architecture, market-selection rules, F2P-only scope, four-hour limit policy, 20-minute buy timeout, or no-timeout/no-reprice sell policy.

The stabilization pass is one subsystem: the Java V6 Grand Exchange execution lifecycle from candidate selection through buy, collect, sell, and final GP collection.

## Current verified baseline

At the start of this pass the branch is `feat/runelite-ge-autotrader-v6` and has already demonstrated the following with unit tests and live evidence:

- the GE bridge starts and publishes live state on `127.0.0.1:17654`;
- Wiki mapping/latest/5m endpoints are reachable and market snapshots become `READY`;
- empty F2P slots 1-3 expose valid live Buy/Sell action bounds;
- `Create Buy offer` / `Create Sell offer` widget aliases are recognised;
- RuneLite formatting tags are stripped by the Auto-Trader widget-action resolver;
- a single Buy setup screen can be opened by V6;
- setup workflows are serialized so slot 2/3 do not start while another slot is still in pre-placement setup;
- `NO_OPPORTUNITY` is treated as healthy scanning rather than a paused market-data failure.

Known remaining live failure at this point:

- the Buy setup is visibly open with `offerSetupType=BUY`, `offerSetupItemId=-1`, and a valid full chatbox input field, but RuneLite reports an unexpected message-layer mode; `GeStateReader` therefore remains at `WAIT_BUY_SETUP` instead of emitting `TYPE_ITEM_SEARCH`.

The bridge independently reports this same screen as `geInput.mode=UNKNOWN` with `inputFieldBounds.valid=true` and `promptBounds.valid=false`.

## Stabilization goals

The pass is complete only when the existing V6 Java pipeline can reliably perform and verify one full lifecycle:

`IDLE -> BUY SETUP -> SEARCH -> ITEM SELECT -> QUANTITY -> PRICE -> CONFIRM BUY -> MONITOR BUY -> COLLECT ITEMS -> SELL SETUP -> SELECT INVENTORY ITEM -> QUANTITY -> PRICE -> CONFIRM SELL -> MONITOR SELL -> COLLECT GP -> IDLE`

The implementation must also preserve continuous use of the three F2P GE slots, while allowing only one setup/edit/collect workflow to own the shared GE setup interface at a time.

## Design principles

### 1. Observe authoritative game state, not one fragile signal

Prompt/input classification must combine exact RuneLite state instead of trusting only `VarClientID.MESLAYERMODE`.

The classifier will use:

- GE offer setup visibility;
- setup side (`BUY` or `SELL`);
- selected setup item ID;
- `MESLAYERMODE` when it is a recognised `InputType`;
- visibility of `CHATBOX_FULL_INPUT`;
- exact quantity/price prompt title text after `Text.removeTags`;
- setup quantity/price values where useful for verification, not for guessing a prompt.

### 2. Fail closed, but preserve the real reason

An execution failure still stops automated actions immediately, but the displayed reason must remain the actual execution reason (`EXECUTION_TARGET_UNAVAILABLE`, `EXECUTION_REJECTED`, setup mismatch, etc.).

`STOPPED_F8` is reserved exclusively for a real F8 key press.

### 3. One shared setup owner at a time

Only one slot may be in a phase that controls the shared GE setup UI. Other slots may continue monitoring already-placed offers, but they may not begin another setup/collect/edit workflow until the shared owner releases it.

### 4. Every action needs later proof

Dispatch success only means RuneLite accepted the menu/key action. The state machine advances only when later client state proves the expected result.

### 5. No broad recovery guesses

Recovery may clear an abandoned pre-placement workflow only when the observed slot is still empty and the setup is not an already-confirmed offer. It must not destroy or duplicate a real live offer.

## Prompt and input classification

`GeStateReader` will be the authoritative classifier used by V6 execution.

Classification rules, evaluated in this order:

1. If GE offer setup is not open: `NONE`.
2. If `MESLAYERMODE == SEARCH`: `ITEM_SEARCH`.
3. If a recognised exact chatbox title is the buy/sell quantity prompt: `QUANTITY`.
4. If a recognised exact chatbox title is the price prompt: `PRICE`.
5. If the setup side is `BUY`, setup item ID is `< 0`, and `CHATBOX_FULL_INPUT` is visible: `ITEM_SEARCH`, even if the raw message-layer mode is `NONE`, `RUNELITE`, `RUNELITE_CHATBOX_PANEL`, a future/unknown value, or another non-search mode.
6. If raw mode is `NONE` and none of the above apply: `NONE`.
7. Otherwise: `UNKNOWN`.

This rule is intentionally narrow: the fallback only classifies item search when no item has yet been selected on a BUY setup and the full input field is visibly open. It does not reinterpret quantity or price prompts as item search.

The diagnostic bridge may be updated to mirror the same classifier semantics so live diagnostics agree with the Auto-Trader, but V6 execution must not depend on the bridge.

## Buy lifecycle

### Start

An empty owned slot may reserve a candidate and emit `OPEN_BUY` only if no other slot owns the shared setup workflow.

### Search

After Buy setup is proved open and prompt mode is `ITEM_SEARCH`, emit exactly one `TYPE_ITEM_SEARCH` with the candidate name.

`GePromptInputService` may type only when state proves the expected prompt mode. It must never type arbitrary text into an unknown chatbox mode.

### Select exact result

After search results are present, select the exact candidate item ID. Item name is display/search text; canonical item ID remains the identity check.

If the expected item ID cannot be resolved uniquely, do not pick the first result; stop the transition with a structured execution/identity reason.

### Quantity and price

Open quantity, wait for `QUANTITY`, type exact quantity, then require the exact setup quantity before opening price.

Open price, wait for `PRICE`, type exact price, then require exact item ID, quantity, price, and BUY side before confirm.

### Confirm and placement proof

Confirm exactly once. Stay in `WAIT_BUY_SLOT` until the target slot proves `BUYING` or `BOUGHT` with matching item ID, total quantity, and price. Only then mark the buy placed and begin the 20-minute timeout.

## Buy monitoring, abort, and collection

Monitoring continues independently of the shared setup owner.

A buy may auto-abort at most once, only after 20 minutes from proved placement. Abort itself uses the shared GE offer-view workflow and must not collide with another setup/collect workflow.

For `BOUGHT` or proved cancelled-partial buys:

- open the exact offer;
- capture pre-collect inventory quantity;
- collect once;
- require the target slot to become empty and inventory quantity to increase;
- record only the actual received quantity into the four-hour limit ledger;
- create the sell obligation using actual received quantity.

## Sell lifecycle

A sell obligation starts only when the required inventory quantity is present and the shared setup workflow is free.

The sequence is:

1. `OPEN_SELL` on the selected empty owned slot;
2. prove SELL setup is open;
3. select the exact inventory item ID;
4. prove the exact setup item ID;
5. open/type exact quantity;
6. open/type exact sell price;
7. require exact item ID, quantity, price, and SELL side;
8. confirm exactly once;
9. prove `SELLING` or `SOLD` in the target slot.

After placement there is no automatic sell timeout, abort, or repricing.

When sold, open the exact offer, capture pre-collect GP, collect once, and require both slot empty and GP increase before removing the sell obligation and returning the slot context to `IDLE`.

## Shared workflow ownership

The existing serialization rule will be made explicit and covered end-to-end.

Phases that own the shared GE workflow include:

- all buy setup/search/quantity/price phases;
- `WAIT_BUY_SLOT` until placement is proved;
- buy abort/open-offer/collect phases while the GE offer view is being manipulated;
- all sell setup/item/quantity/price phases;
- `WAIT_SELL_SLOT` until placement is proved;
- sell collect/open-offer phases while the GE offer view is being manipulated.

Pure monitoring phases (`MONITOR_BUY`, `MONITOR_SELL`) do not own the shared setup interface, so another slot may start a new workflow while existing offers are merely being monitored.

The state machine must never emit two executable shared-interface actions from one tick.

## Stop and error model

The single `stopped` gate may continue to block execution, but stop origin must be represented separately.

Required semantics:

- real F8 press: block execution, `manualRestartAllowed=true`, visible reason `STOPPED_F8`;
- execution failure: block execution, `manualRestartAllowed=false`, preserve the exact execution reason;
- disabled config: `DISABLED`, not an F8 reason;
- global safety gate: show its exact safety reason;
- OFF->ON manual re-arm remains allowed only for a genuine F8 stop, not for an execution-failure stop.

A practical implementation may introduce a small `GeStopCause` enum (`NONE`, `F8`, `EXECUTION_FAILURE`) or an equivalent explicit field; do not infer the cause solely from the shared atomic stop flag.

## Dispatcher and widget resolution audit

The stabilization pass will audit every existing `GeActionDispatcher` action against live RuneLite widget/menu semantics:

- `OPEN_BUY` / `OPEN_SELL` aliases;
- `TYPE_ITEM_SEARCH`;
- exact search-result selection;
- sell-inventory selection;
- `OPEN_QUANTITY`;
- `TYPE_QUANTITY`;
- `OPEN_PRICE`;
- `TYPE_PRICE`;
- `CONFIRM`;
- `OPEN_OFFER`;
- `ABORT_BUY`;
- `COLLECT`.

For widget actions:

- strip RuneLite tags before alias comparison;
- require a unique/slot-correct target;
- never use coordinates as the authority;
- return `EXECUTION_TARGET_UNAVAILABLE` when resolution is not unique or absent;
- return `EXECUTION_REJECTED` only when a resolved action/input invocation itself fails.

## State-machine verification and timeouts

The state machine remains tick-driven and deterministic.

Each emitted action moves to a wait/proof phase. Repeated ticks in that phase must not re-emit the same action unless the design explicitly calls for a new one.

The pass will add bounded transition timeouts for setup/proof phases that can otherwise wait forever. A timeout must not guess success; it records a structured reason and fail-closes the current execution workflow. The 20-minute business timeout for pending buys remains separate from these short UI proof timeouts.

Exact timeout values will be conservative and testable; they are implementation constants, not configurable trading policy.

## Test architecture

All production changes use TDD.

### Focused reader tests

Cover:

- recognised `SEARCH` mode;
- live fallback: BUY + item `-1` + visible full input + raw unknown mode => `ITEM_SEARCH`;
- the same setup without visible full input does not become `ITEM_SEARCH`;
- selected item plus quantity/price prompts are not misclassified as search;
- exact title text still identifies quantity/price.

### Dispatcher/input tests

Cover every action listed in the dispatcher audit, including tagged aliases, unique target enforcement, exact item-ID result selection, stopped gate, and prompt-mode gating.

### State-machine tests

Cover:

- one shared setup owner;
- monitoring does not block another slot from starting;
- no duplicate action while awaiting proof;
- buy setup through confirmed placement;
- buy collect using actual inventory delta;
- buy->sell obligation creation;
- sell setup through confirmed placement;
- sell GP collection and return to idle;
- one-abort rule and 20-minute placement-based timer;
- mismatched state fail-closed behavior;
- recovery does not clear a confirmed/live offer.

### End-to-end deterministic lifecycle test

Add one integration-style Java test that feeds a sequence of `GeObservedState` snapshots through the real `GeTradeStateMachine` and captures emitted `GePlannedAction` values for a full lifecycle:

`IDLE -> OPEN_BUY -> TYPE_ITEM_SEARCH -> SELECT_ITEM -> OPEN_QUANTITY -> TYPE_QUANTITY -> OPEN_PRICE -> TYPE_PRICE -> CONFIRM -> BUYING/BOUGHT -> OPEN_OFFER/COLLECT -> OPEN_SELL -> SELECT_SELL_ITEM -> OPEN_QUANTITY -> TYPE_QUANTITY -> OPEN_PRICE -> TYPE_PRICE -> CONFIRM -> SELLING/SOLD -> OPEN_OFFER/COLLECT -> IDLE`.

The test must assert that no second slot starts while the shared setup workflow is active and that another slot can start once the first offer reaches a pure monitoring phase.

### Plugin stop-reason tests

Cover:

- F8 produces `STOPPED_F8` and enables manual re-arm;
- execution failure preserves its true reason and does not enable manual re-arm;
- later game ticks do not overwrite an execution-failure reason with `STOPPED_F8` merely because the shared stop gate is true.

## Verification commands

Targeted tests will be run during each TDD task. Before declaring the pass complete, run at least:

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.*"
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.*"
.\gradlew.bat :client:shadowJar
```

Do not claim success from code review alone. Fresh command output is required.

## Live validation

After all Java tests and `shadowJar` pass, perform one clean live validation using the newly built shaded JAR:

1. start on the main GE screen with slots 1-3 empty or otherwise known;
2. enable V6 once;
3. do not manually click GE controls or press F8;
4. observe one complete low-value buy setup and placement;
5. if the buy completes, observe collection and sell placement;
6. if the sell completes, observe GP collection and return to a reusable slot;
7. inspect the overlay reason if any transition stops.

Live evidence is the final check for widget/runtime assumptions. A unit-test pass alone does not prove end-to-end live trading.

## Out of scope for this pass

The following are deliberately excluded unless a failing stabilization test proves they are required for the lifecycle:

- changing market opportunity ranking or ROI strategy;
- adding members-world support;
- changing buy-limit policy;
- sell repricing, sell timeout, or sell abort logic;
- Python/physical-mouse execution fallback;
- unrelated World Hopper ping/session-service warnings;
- UI redesign beyond reason/status correctness.

## Definition of done

The V6 stabilization pass is complete when all of the following are true:

- prompt classification handles the observed `UNKNOWN + visible full input` Buy-search state;
- all dispatcher actions required by buy/collect/sell have focused tests;
- only one slot owns the shared GE setup/collect workflow at a time;
- monitoring offers can coexist across slots and does not unnecessarily serialize all trading;
- a deterministic full lifecycle test passes;
- F8 stop and execution-failure stop have distinct, truthful status semantics;
- full GE Auto-Trader tests pass;
- relevant GE Bridge tests pass;
- `:client:shadowJar` succeeds;
- one clean live run demonstrates the pipeline progressing through at least a complete buy->collect->sell placement sequence, with final GP collection when the sell fills.
