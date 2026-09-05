# RuneLite GE Auto-Trader V6 Proof-Driven Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every critical GE UI transition retryable and proof-driven so transient missing widgets do not stop V6 and no phase advances without live state proof.

**Architecture:** Add an explicit offer-details visibility signal, keep a per-slot pending UI operation with bounded retry metadata, and feed dispatcher results back into the state machine. The state machine retries unresolved actions until state proof arrives or a deterministic deadline expires; Collect is proven by slot/inventory/GP changes, not by a widget action string.

**Tech Stack:** Java 11, RuneLite API/gameval interfaces, JUnit 4, Mockito, Gradle, PowerShell verification script.

**Spec:** `docs/superpowers/specs/2026-09-05-runelite-ge-v6-proof-driven-execution-design.md`

## Global Constraints

- F2P GE slots 1–3 only.
- F8 remains an immediate emergency stop.
- Members worlds, bank/world-map/dialog blockers, and identity contradictions remain fail-closed.
- `EXECUTION_TARGET_UNAVAILABLE` is retryable only for a bounded 5-second window.
- Existing UI proof timeout remains 10 seconds.
- No sell timeout/reprice/abort behaviour is added.
- `scripts/verify-ge-v6.ps1` remains the only user-facing verification command.

---

### Task 1: Expose offer-details visibility

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeObservedState.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeStateReader.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeState.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeStateReader.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeStateReaderTest.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeGeStateReaderTest.java`

**Interfaces:**
- Produces: `GeObservedState.isOfferDetailsVisible()` and bridge JSON field `ge.offerDetailsVisible`.

- [ ] **Step 1: Write failing tests** proving `InterfaceID.GeOffers.DETAILS` visibility maps to the new boolean while existing constructors default it to `false`.
- [ ] **Step 2: Run focused tests** and confirm RED.
- [ ] **Step 3: Implement the signal**. `GeStateReader.read()` should compute:

```java
boolean offerDetailsVisible = isVisible(InterfaceID.GeOffers.DETAILS);
```

and pass it to the full `GeObservedState` constructor. Bridge state reader should use the same component.
- [ ] **Step 4: Run focused tests** and confirm GREEN.
- [ ] **Step 5: Commit** `feat: expose GE offer details visibility`.

### Task 2: Add pending UI operation state and dispatcher-result feedback

**Files:**
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GePendingUiOperation.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeAutoTraderPlugin.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeProofDrivenExecutionTest.java`

**Interfaces:**
- Produces: `GeTradeStateMachine.recordExecutionResult(GePlannedAction, GeReasonCode, Instant): boolean`, where return `true` means stop fail-closed.
- Produces: `GeTradeStateMachine.getPendingAction(int): GePlannedActionType` for overlay/tests.

- [ ] **Step 1: Write failing tests** for `TARGET_UNAVAILABLE` being non-terminal before 5 seconds, terminal after 5 seconds, and `EXECUTION_REJECTED` being terminal immediately.
- [ ] **Step 2: Run focused tests** and confirm RED.
- [ ] **Step 3: Implement `GePendingUiOperation`** with action type, first attempt, last attempt, attempt count, last result, `shouldRetry(now)`, and `expiredTargetUnavailable(now)`.
- [ ] **Step 4: Add per-slot pending operation** to `SlotContext` and helper methods:

```java
private GePlannedAction pendingAction(SlotContext context, GePlannedActionType type, Instant now)
private void clearPending(SlotContext context)
public boolean recordExecutionResult(GePlannedAction action, GeReasonCode result, Instant now)
```

- [ ] **Step 5: Update plugin dispatch handling** so `OK` and retryable `EXECUTION_TARGET_UNAVAILABLE` do not stop; terminal results still call `stopForExecutionFailure()`.
- [ ] **Step 6: Run focused tests** and confirm GREEN.
- [ ] **Step 7: Commit** `feat: add bounded proof-driven UI retries`.

### Task 3: Convert offer-details, abort, and collection flows to proof-driven transitions

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeProofDrivenExecutionTest.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineCollectTest.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineLifecycleTest.java`

**Interfaces:**
- `WAIT_BUY_COLLECT_READY` means “open/details proof pending”.
- `WAIT_BUY_COLLECT_RESULT` means “collect/result proof pending”.
- Equivalent semantics apply to sell collection and abort.

- [ ] **Step 1: Write failing lifecycle tests** for:
  - completed buy emits `OPEN_OFFER` repeatedly until `isOfferDetailsVisible()` is true;
  - details proof then emits `COLLECT`;
  - overview returning before slot/inventory proof sends the flow back to `OPEN_OFFER` instead of failing;
  - slot empty + inventory delta proves buy collection and emits `OPEN_SELL`;
  - zero-fill cancelled buy requires GP refund proof;
  - sold offer requires GP increase proof before returning `IDLE`;
  - abort waits for details proof before emitting `ABORT_BUY`.
- [ ] **Step 2: Run focused tests** and confirm RED.
- [ ] **Step 3: Implement proof transitions**. Representative buy collection logic:

```java
case WAIT_BUY_COLLECT_READY:
    if (!state.isOfferDetailsVisible())
        return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
    clearPending(context);
    context.preCollectInventory = state.getInventoryQuantity(context.candidate.getItemId());
    context.preCollectGp = state.getGp();
    context.phase = GeTradePhase.WAIT_BUY_COLLECT_RESULT;
    return pendingAction(context, GePlannedActionType.COLLECT, now);

case WAIT_BUY_COLLECT_RESULT:
    if (collectionProved(...)) { clearPending(context); return finish...; }
    if (!state.isOfferDetailsVisible()) {
        clearPending(context);
        context.phase = GeTradePhase.WAIT_BUY_COLLECT_READY;
        return pendingAction(context, GePlannedActionType.OPEN_OFFER, now);
    }
    return pendingAction(context, GePlannedActionType.COLLECT, now);
```

- [ ] **Step 4: Preserve contradiction handling** for wrong item/slot/side and 10-second proof timeout.
- [ ] **Step 5: Run focused tests** and confirm GREEN.
- [ ] **Step 6: Commit** `fix: make GE details and collection proof driven`.

### Task 4: Make setup transitions retryable without duplicate logical progress

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeProofDrivenExecutionTest.java`
- Test: existing buy/sell/setup serialization tests.

**Interfaces:**
- Pending UI actions retry only while their original source state still holds.

- [ ] **Step 1: Write failing tests** for `OPEN_BUY`, `OPEN_SELL`, `OPEN_QUANTITY`, `OPEN_PRICE`, `CONFIRM`, and `SELECT_ITEM` retrying until their existing state proof appears.
- [ ] **Step 2: Run focused tests** and confirm RED.
- [ ] **Step 3: Replace one-shot emissions** with `pendingAction(...)`; clear pending only when the corresponding prompt/setup/slot proof is observed.
- [ ] **Step 4: Do not blindly retry keyboard typing** (`TYPE_ITEM_SEARCH`, `TYPE_QUANTITY`, `TYPE_PRICE`) after an `OK`; those remain proof-waiting operations to avoid duplicated text.
- [ ] **Step 5: Run buy/sell/setup focused tests** and confirm GREEN.
- [ ] **Step 6: Commit** `fix: make GE setup transitions proof driven`.

### Task 5: Restart reconciliation and consolidated verification

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineRestartRecoveryTest.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeAutoTraderV6EndToEndTest.java`
- Modify only if needed: `scripts/verify-ge-v6.ps1`

**Interfaces:**
- Empty owned buy slot + inventory proving received items may reconcile to a sell; empty owned sell without a persisted GP baseline must remain fail-closed.

- [ ] **Step 1: Write failing restart test** for already-collected owned buy inventory reconciliation.
- [ ] **Step 2: Run focused test** and confirm RED.
- [ ] **Step 3: Implement safe reconciliation** using owned obligation item/quantity plus current inventory; never invent sell profit for an unproved sell collection.
- [ ] **Step 4: Add `GeProofDrivenExecutionTest` to `GeAutoTraderV6EndToEndTest`**.
- [ ] **Step 5: Run the single consolidated verification command**:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-ge-v6.ps1
```

Expected: all Auto-Trader + Bridge tests pass, shaded jar builds, and the script prints `GE Auto-Trader V6 verification passed.`
- [ ] **Step 6: Commit** `test: verify proof-driven GE lifecycle`.
