# RuneLite GE State Bridge Design

## Goal

Replace OCR/pixel-based Grand Exchange and inventory state detection in the existing Python GE flipper with authoritative read-only state exported from RuneLite, while keeping market selection, timers, F8 emergency stop, and existing Python-side trade execution unchanged.

## Scope

This change adds a new RuneLite Java plugin plus a small Python client/adapter. The plugin reads RuneLite client state and publishes it on localhost. The Python flipper consumes the snapshot and treats missing, stale, logged-out, or malformed bridge data as `UNKNOWN/WAIT` rather than as an empty GE slot.

The RuneLite plugin is read-only. It must not click widgets, invoke menu actions, place/cancel offers, move the mouse, press keys, or execute Python commands.

## Existing Context

The RuneLite fork already exposes authoritative GE offer data through `Client.getGrandExchangeOffers()` and `GrandExchangeOfferChanged`. `GrandExchangeOffer` provides item ID, total quantity, traded quantity, offer price, spent amount, and `GrandExchangeOfferState`.

The existing Python flipper contains OCR/pixel state detection and earlier V440 dashboard logic that deliberately keeps visual state reading separate from trade execution. The new bridge keeps that separation while changing the source of truth from pixels to RuneLite state.

## Architecture

### Components

1. **RuneLite bridge plugin (Java)**
   - Lives in `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/`.
   - Reads GE offers directly from RuneLite.
   - Reads inventory item IDs and quantities directly from RuneLite.
   - Computes inventory GP from item ID `995` only.
   - Publishes a JSON snapshot over a loopback-only HTTP endpoint.
   - Refreshes the snapshot on relevant RuneLite events and when the HTTP endpoint is queried.

2. **Localhost JSON bridge**
   - Binds only to `127.0.0.1`.
   - Default port: `17654`.
   - Endpoint: `GET /state`.
   - No write endpoints.
   - No remote bind option.
   - No authentication is required because the server is loopback-only and read-only.

3. **Python bridge client**
   - Added alongside the current GE flipper, not as a replacement for market-selection/execution code.
   - Polls `http://127.0.0.1:17654/state` with a short timeout.
   - Validates protocol version, timestamp, game state, and required fields.
   - Exposes normalized slot and inventory state to the existing flipper.
   - Uses `UNKNOWN/WAIT` when the bridge is unavailable, stale, malformed, or logged out.

## Protocol

Protocol version is integer `1`.

Example response:

```json
{
  "protocol": 1,
  "generatedAtEpochMs": 1788512400000,
  "gameState": "LOGGED_IN",
  "slots": [
    {
      "slot": 0,
      "itemId": 314,
      "state": "BUYING",
      "visual": "ORANGE",
      "price": 12,
      "totalQuantity": 1000,
      "quantityTraded": 420,
      "spent": 5040,
      "collectReady": false
    }
  ],
  "inventory": [
    {"itemId": 995, "quantity": 53000}
  ],
  "inventoryGp": 53000
}
```

### Slot requirements

The `slots` array contains one entry per RuneLite GE slot returned by `Client.getGrandExchangeOffers()`. Do not hard-code the RuneLite side to three slots; the existing Python flipper may choose to consume only its configured first three slots.

Every slot entry contains:

- `slot`: zero-based RuneLite GE slot index.
- `itemId`: RuneLite item ID, or `-1` for empty.
- `state`: exact RuneLite `GrandExchangeOfferState` name.
- `visual`: compatibility state for the existing Python dashboard/state machine.
- `price`: offer price per item.
- `totalQuantity`: requested total quantity.
- `quantityTraded`: RuneLite `getQuantitySold()` value (traded quantity for either buy or sell offers).
- `spent`: RuneLite `getSpent()` value.
- `collectReady`: true for terminal non-empty offer states that require collection before the slot becomes empty.

### Exact state mapping

Compatibility mapping:

- `EMPTY` -> `EMPTY`
- `BUYING` -> `ORANGE`
- `SELLING` -> `ORANGE`
- `BOUGHT` -> `GREEN`
- `SOLD` -> `GREEN`
- `CANCELLED_BUY` -> `RED`
- `CANCELLED_SELL` -> `RED`

`collectReady` is:

- `false` for `EMPTY`, `BUYING`, and `SELLING`.
- `true` for `BOUGHT`, `SOLD`, `CANCELLED_BUY`, and `CANCELLED_SELL`.

Python must preserve the exact `state` value as the authoritative state. `visual` exists only for compatibility with existing GREEN/ORANGE/RED/EMPTY handling and logging.

## Inventory

The plugin exports all non-empty inventory stacks as item ID plus quantity.

Rules:

- Preserve RuneLite item IDs exactly.
- Aggregate duplicate item IDs before serialization.
- Ignore empty inventory slots.
- `inventoryGp` is the total quantity of item ID `995` in the inventory snapshot.
- Do not use OCR to derive GP when bridge state is valid.

## Freshness and Failure Behaviour

The Python client is responsible for freshness enforcement.

Defaults:

- HTTP connect/read timeout: `0.35` seconds.
- Maximum accepted snapshot age: `2.0` seconds.
- Poll interval: configurable by the existing Python loop; recommended `0.25-0.75` seconds.

A snapshot is invalid if any of the following is true:

- HTTP request fails.
- JSON is malformed.
- `protocol != 1`.
- `generatedAtEpochMs` is absent or older than the maximum accepted age.
- `gameState != "LOGGED_IN"`.
- `slots` is absent or not an array.

Invalid bridge state maps to `UNKNOWN/WAIT` in Python. It must never be interpreted as `EMPTY` and must never trigger a new offer merely because the bridge failed.

## Python Integration

Create a focused bridge client rather than embedding HTTP parsing throughout the large flipper file.

Target interface:

```python
class RuneLiteBridgeClient:
    def __init__(
        self,
        base_url: str = "http://127.0.0.1:17654",
        timeout_seconds: float = 0.35,
        max_age_seconds: float = 2.0,
    ) -> None: ...

    def read_state(self) -> "BridgeSnapshot | None": ...
```

Normalized Python data objects must include:

```python
@dataclass(frozen=True)
class BridgeSlot:
    slot: int
    item_id: int
    state: str
    visual: str
    price: int
    total_quantity: int
    quantity_traded: int
    spent: int
    collect_ready: bool

@dataclass(frozen=True)
class BridgeSnapshot:
    generated_at_epoch_ms: int
    game_state: str
    slots: tuple[BridgeSlot, ...]
    inventory: dict[int, int]
    inventory_gp: int
```

Integration rule:

- Bridge state is authoritative when valid.
- OCR/pixel status may remain temporarily for diagnostics/logging only.
- OCR/pixel status must not override a valid bridge result.
- If bridge data is invalid, the live trading state becomes `UNKNOWN/WAIT`; do not silently fall back to pixel state for automatic actions in the first bridge-enabled release.

This fail-closed rule is intentional because the main goal is to remove false-empty and stale visual-state bugs.

## RuneLite Event Flow

The plugin maintains a current immutable snapshot.

Relevant triggers:

- plugin startup
- `GrandExchangeOfferChanged`
- inventory container changes
- game state changes
- an HTTP `GET /state` request may force a safe latest read on the RuneLite client thread if required by thread-safety constraints

When logged out or hopping, the endpoint may remain reachable but must report the actual RuneLite game state. Python will reject non-`LOGGED_IN` snapshots.

## Threading

RuneLite client state must only be read in a thread-safe way consistent with RuneLite conventions. HTTP request threads must not directly perform unsafe client reads.

Preferred design:

- event handlers update a cached immutable bridge snapshot on the RuneLite/client event thread;
- the HTTP handler only serializes the cached snapshot;
- startup seeds the cache when possible;
- shutdown stops the local server and clears state.

## Configuration

Add a small RuneLite config group for bridge status/configuration only if needed by existing plugin conventions.

Required defaults are fixed for the first release:

- host: `127.0.0.1`
- port: `17654`
- protocol: `1`

Do not expose a remote host binding setting.

Python may allow the base URL, timeout, and max-age values to be overridden by environment/config for local testing, while keeping the defaults above.

## Logging

RuneLite logs:

- bridge start/stop
- bind failure
- serialization failure
- protocol endpoint errors

Do not log every successful poll.

Python logs state transitions and bridge health transitions only:

- connected
- stale
- unavailable
- recovered
- protocol mismatch

Avoid per-poll console spam.

## Security and Safety Boundaries

The bridge is deliberately one-way and read-only.

It must not:

- accept POST/PUT/PATCH/DELETE commands;
- expose a command endpoint;
- bind to `0.0.0.0` or a LAN address;
- execute shell commands;
- launch or control Python;
- call RuneLite menu actions;
- click widgets;
- place, cancel, or collect GE offers.

## Testing

### Java tests

Add focused tests for:

- every `GrandExchangeOfferState` -> compatibility `visual` mapping;
- `collectReady` mapping;
- slot DTO field mapping;
- inventory aggregation and GP extraction;
- serialized snapshot protocol shape;
- loopback-only HTTP response behavior where practical without requiring a live RuneLite session.

### Python tests

Add focused tests for:

- valid snapshot parsing;
- slot ordering and exact state preservation;
- inventory aggregation parsing;
- stale timestamp rejection;
- logged-out snapshot rejection;
- malformed JSON rejection;
- protocol mismatch rejection;
- timeout/unavailable bridge returning `None`;
- integration adapter translating invalid bridge data to `UNKNOWN/WAIT` rather than `EMPTY`.

All production behavior must be implemented test-first.

## Non-Goals

This first release does not:

- rewrite market-selection/ranking logic;
- rewrite mouse-coordinate execution;
- implement GE actions inside RuneLite;
- remove F8 emergency stop;
- add remote networking;
- add WebSocket streaming;
- add authentication;
- redesign the console UI;
- remove legacy OCR code immediately.

## Rollout

1. Add and test RuneLite bridge state model/mapping.
2. Add and test loopback HTTP endpoint.
3. Add and test Python `RuneLiteBridgeClient`.
4. Add a thin adapter into the current authoritative Python flipper entrypoint.
5. Run bridge-enabled dry/state-observation checks before allowing it to drive existing automatic trade transitions.
6. Keep the existing Python execution logic and F8 emergency stop unchanged.

## Acceptance Criteria

The feature is accepted when:

- RuneLite exposes a loopback-only `GET /state` JSON snapshot using protocol `1`.
- GE slot state comes from `GrandExchangeOfferState`, not screen pixels.
- Inventory item IDs/quantities and GP come from RuneLite, not OCR, whenever bridge data is valid.
- Python preserves exact RuneLite offer state and exposes compatibility GREEN/ORANGE/RED/EMPTY state.
- completed/cancelled offers expose `collectReady=true`.
- stale/disconnected/logged-out bridge data becomes `UNKNOWN/WAIT`, never false `EMPTY`.
- the RuneLite plugin performs no automated game actions.
- existing market selection, timers, F8 emergency stop, and Python execution logic remain functionally separate from the bridge.