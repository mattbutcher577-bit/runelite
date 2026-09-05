# RuneLite GE Bridge V5 / V990 Implementation Plan

**Goal:** Implement the approved V5 authoritative execution, market-intelligence and mouse-authority designs without weakening the read-only RuneLite boundary.

## Phase 1 — V5 protocol foundation

1. Add failing Java tests for protocol 5, bridge/session sequencing, real/stretched geometry, removal of raw search query, semantic GE input state and privacy.
2. Confirm CI fails for the intended missing V5 surface.
3. Add narrow model classes and snapshot/builder changes.
4. Update plugin readers/events without any Java-generated input.
5. Confirm focused GE bridge tests and repository CI pass.

## Phase 2 — Mouse authority

1. Add failing tests for mouse state/event capture, event sequencing, bounded event ring, focus publication and privacy.
2. Add read-only `GeBridgeMouseTracker`, `GeBridgeMouseState` and event DTOs.
3. Wire tracker lifecycle into the plugin and snapshot.
4. Confirm tests/CI.

## Phase 3 — GE action/input/inventory authority

1. Add failing tests for semantic input modes, exact actionable bounds, GE-side inventory entries, canonical/raw item IDs and per-section freshness.
2. Implement focused state readers and DTOs.
3. Wire event-driven refresh (`GE_ITEM_SEARCH`, GE setup build, offer/widget/input changes) with invalid-state fail-closed behavior.
4. Confirm tests/CI.

## Phase 4 — V990 deterministic Python core

1. Create protocol-5 parser/models with tests rejecting protocol 2/3/4 and malformed/stale V5 state.
2. Create typed coordinate spaces and stretched/canvas/screen conversion tests.
3. Create mouse transaction layer tests for move/press/release proof, focus loss, desync, duplicate-click protection and stuck-button/manual hold.
4. Create GE transition-engine tests for buy/select/quantity/price/confirm/collect/sell/abort and structured failure codes.
5. Create sanitized replay fixtures and tests.
6. Bind V990 live execution to these modules; OCR remains diagnostics only.

## Phase 5 — Market intelligence in shadow mode

1. Add exact fill/limit ledger and post-tax trade-history dataset.
2. Add simple deterministic baseline scorer first.
3. Add optional probabilistic forecaster/fill model behind shadow mode.
4. Require evaluation against the baseline before any model can affect candidate ranking.
5. Keep all execution safety gates deterministic and RuneLite-authoritative.

## Verification gates

- Java remains read-only; no menu invocation, click, cursor movement or gameplay mutation.
- `GET /state` remains localhost-only; non-GET remains rejected.
- V5 JSON contains no search/chat/login/clipboard/arbitrary-key text.
- F8 emergency stop, F2P-only, first-three-slot policy, 20-minute buy ceiling, one-abort-per-buy-obligation and SELL no-timeout/no-reprice remain unchanged.
- No completion claim until fresh CI/test evidence exists.
