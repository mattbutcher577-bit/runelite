# RuneLite GE Bridge Protocol v2 Design

## Goal

Expand the existing read-only RuneLite GE bridge so the Python flipper can use authoritative RuneLite state for client readiness, world/player state, interface blockers, GE interface state, inventory, viewport geometry, and widget bounds instead of relying on OCR and a fixed 773x535 client size.

## Constraints

- The Java plugin remains read-only. It must never click, type, place/cancel/collect offers, invoke menu actions, or automate gameplay.
- The HTTP server remains loopback-only on `127.0.0.1:17654` with `GET /state` only.
- Python remains responsible for all mouse automation and F8 emergency stop behavior.
- Invalid, stale, logged-out, or malformed bridge state fails closed.
- Existing GE offer fields and inventory data remain available.
- Protocol v2 is intentionally explicit and JSON-only.

## Protocol v2 snapshot

Top-level fields:

- `protocol`: `2`
- `generatedAtEpochMs`
- `tick`: monotonically increasing bridge game-tick counter while logged in
- `gameState`: compatibility field
- `slots`: existing exact GE offer states
- `inventory`: existing aggregated inventory
- `inventoryGp`: existing GP compatibility field
- `client`: client/world/viewport state
- `player`: local-player location state
- `interfaces`: important modal and GE interface state
- `ge`: GE widget state and bounds
- `inventoryState`: free-slot and occupied-slot summary
- `safety`: read-only readiness summary for Python

### client

Fields:

- `loggedIn`
- `world`
- `worldTypes`: list of `WorldType` names
- `membersWorld`
- `canvasWidth`, `canvasHeight`
- `viewportWidth`, `viewportHeight`
- `viewportXOffset`, `viewportYOffset`
- `topLevelInterfaceId`
- `fps`

### player

Fields:

- `present`
- `worldX`, `worldY`, `plane`

When no local player is available, `present=false` and coordinates are `-1`.

### interfaces

Fields:

- `grandExchangeOpen`
- `grandExchangeOfferSetupOpen`
- `bankOpen`
- `worldMapOpen`
- `dialogOpen`
- `chatboxInputOpen`
- `draggingWidget`

A widget is considered open only when it exists and is not hidden.

### ge

Fields:

- `open`
- `offerSetupOpen`
- `windowBounds`
- `offerSetupBounds`
- `inventoryBounds`

Each bounds object contains `x`, `y`, `width`, `height`, and `valid`. Bounds are canvas-relative and are read from RuneLite widgets on the client thread.

### inventoryState

Fields:

- `capacity`: 28
- `occupiedSlots`
- `freeSlots`

Occupied slots count physical inventory slots with a positive-quantity item. Aggregated item quantities remain in the existing `inventory` list.

### safety

Fields:

- `bridgeReady`: logged in, post-login game tick seen, and local player available
- `modalBlocker`: bank, world map, dialog, chat input, or widget drag is active
- `safeForMouseActions`: `bridgeReady && !modalBlocker`
- `safeForGeMouseActions`: `safeForMouseActions && grandExchangeOpen`

These are advisory read-only summaries. Python still decides whether and where to click.

## Python behavior

`runelite_bridge.py` will parse protocol v2 into immutable dataclasses and reject protocol v1 for the v2 integration. It will expose helpers for exact slot state, inventory, viewport size, GE/widget bounds, interface blockers, and safety readiness.

The V981 integration will use bridge state as the authoritative source for:

- GE slot state
- inventory GP
- client logged-in readiness
- GE open/setup state
- modal blockers
- actual canvas dimensions and GE widget bounds

The old fixed-size/OCR geometry check will no longer be allowed to put the bot into degraded mode when a fresh protocol-v2 bridge snapshot provides valid canvas/widget state. OCR remains available only as a fallback diagnostic path, never as a silent replacement for invalid bridge state.

## Failure behavior

Any of the following causes Python to return no snapshot and fail closed:

- HTTP failure
- malformed JSON
- protocol mismatch
- non-`LOGGED_IN` state
- non-positive timestamp
- snapshot older than 2 seconds
- malformed required sections

If required GE widget bounds are invalid while Python is about to perform a GE click, Python must wait instead of guessing coordinates.

## Testing

Java tests cover protocol version, offer/inventory preservation, inventory slot counts, safety boolean rules, and bounds serialization. Python tests cover valid v2 parsing, stale/logged-out/protocol mismatch rejection, blocker handling, viewport/bounds parsing, and fail-closed helpers.
