# RuneLite GE Auto-Trader V6 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize the existing Java V6 Grand Exchange execution lifecycle so one deterministic F2P buy -> collect -> sell -> collect-GP cycle can complete safely, while preserving three-slot monitoring and truthful stop/error reporting.

**Architecture:** Keep the existing `GeAutoTraderPlugin` + `GeTradeStateMachine` + `GeStateReader` + `GeActionDispatcher` architecture. Strengthen prompt classification with multiple authoritative signals, explicitly serialize every shared GE UI workflow, preserve execution-failure reasons separately from F8, add bounded UI proof timeouts, and prove the full lifecycle with deterministic Java tests before one final Windows build/live run.

**Tech Stack:** Java, RuneLite API/widgets/menu actions, JUnit 4, Mockito, Gradle (`:client:test`, `:client:shadowJar`).

**Spec:** `docs/superpowers/specs/2026-09-04-runelite-ge-autotrader-v6-stabilization-design.md`

## Global Constraints

- F2P worlds only.
- Automation may use only GE slots 1, 2, and 3.
- F8 is the hard emergency stop; `STOPPED_F8` must mean a real F8 press only.
- Execution failures still fail closed but preserve their exact structured reason.
- Only one slot may own the shared GE setup/edit/open-offer/collect UI at a time.
- Pure `MONITOR_BUY` / `MONITOR_SELL` phases do not hold the shared UI lock.
- Buy timeout remains 20 minutes from proved placement; a buy may auto-abort at most once.
- Sells never auto-timeout, auto-abort, or auto-reprice.
- No Python, OCR, Robot, screen-coordinate, or HTTP round-trip execution fallback.
- Every emitted game action requires later RuneLite state proof before the state machine treats it as successful.
- All production changes are test-first; do not claim completion without fresh Windows Gradle output and live evidence.

---

### Task 1: Make BUY item-search classification match the observed live RuneLite state

**Files:**
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeStateReaderTest.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeStateReader.java`

**Interfaces:**
- Consumes: `WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER`, `WidgetInfo.CHATBOX_FULL_INPUT`, `VarPlayerID.TRADINGPOST_SEARCH`, `VarbitID.GE_NEWOFFER_TYPE`, `VarClientID.MESLAYERMODE`.
- Produces: `GeObservedState.getPromptMode()` returning `ITEM_SEARCH` for the proved live state `BUY + itemId < 0 + visible CHATBOX_FULL_INPUT`, regardless of an unrecognised raw message-layer mode.

- [ ] **Step 1: Keep and run the already-added failing live regression test**

The branch already contains:

```java
@Test
public void testVisibleFullInputMakesUnknownBuySetupItemSearch()
{
    Client client = baseClient();
    visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
    visible(client, WidgetInfo.CHATBOX_FULL_INPUT);
    when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
    when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
    when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

    GeObservedState state = new GeStateReader(client).read(true);
    assertEquals(GePromptMode.ITEM_SEARCH, state.getPromptMode());
}
```

Run:

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeStateReaderTest.testVisibleFullInputMakesUnknownBuySetupItemSearch"
```

Expected before production change: `expected:<ITEM_SEARCH> but was:<UNKNOWN>`.

- [ ] **Step 2: Add guard tests so the fallback cannot swallow quantity/price or arbitrary unknown states**

Add tests equivalent to:

```java
@Test
public void testUnknownBuySetupWithoutVisibleFullInputStaysUnknown()
{
    Client client = baseClient();
    visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
    when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(-1);
    when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
    when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

    assertEquals(GePromptMode.UNKNOWN, new GeStateReader(client).read(true).getPromptMode());
}

@Test
public void testSelectedBuyItemWithVisibleFullInputIsNotForcedToSearch()
{
    Client client = baseClient();
    visible(client, WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER);
    visible(client, WidgetInfo.CHATBOX_FULL_INPUT);
    when(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH)).thenReturn(1127);
    when(client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE)).thenReturn(0);
    when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(99);

    assertEquals(GePromptMode.UNKNOWN, new GeStateReader(client).read(true).getPromptMode());
}
```

- [ ] **Step 3: Implement the narrow fallback in `GeStateReader`**

Read `CHATBOX_TITLE` first so exact quantity/price prompts retain priority. Then classify search using the visible full input field:

```java
private GePromptMode classifyPrompt(boolean setupOpen, int setupItemId, GeTradeSide setupSide)
{
    if (!setupOpen)
    {
        return GePromptMode.NONE;
    }

    int messageLayerMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
    if (messageLayerMode == InputType.SEARCH.getType())
    {
        return GePromptMode.ITEM_SEARCH;
    }

    Widget promptWidget = client.getWidget(WidgetInfo.CHATBOX_TITLE);
    String prompt = promptWidget == null ? null : Text.removeTags(promptWidget.getText());
    prompt = prompt == null ? "" : prompt.trim();
    if (BUY_QUANTITY_PROMPT.equals(prompt) || SELL_QUANTITY_PROMPT.equals(prompt))
    {
        return GePromptMode.QUANTITY;
    }
    if (PRICE_PROMPT.equals(prompt))
    {
        return GePromptMode.PRICE;
    }

    if (setupSide == GeTradeSide.BUY
        && setupItemId < 0
        && isVisible(WidgetInfo.CHATBOX_FULL_INPUT))
    {
        return GePromptMode.ITEM_SEARCH;
    }

    if (messageLayerMode == InputType.NONE.getType())
    {
        return GePromptMode.NONE;
    }
    return GePromptMode.UNKNOWN;
}
```

- [ ] **Step 4: Run the full reader test class**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeStateReaderTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeStateReader.java runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeStateReaderTest.java
git commit -m "fix: classify live GE buy search input"
```

---

### Task 2: Separate real F8 stops from execution-failure stops

**Files:**
- Create: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeStopCause.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeAutoTraderPlugin.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeAutoTraderPluginTest.java`

**Interfaces:**
- Produces: `GeStopCause { NONE, F8, EXECUTION_FAILURE }`.
- `stopped` remains the atomic execution gate; `stopCause` records why it is closed.
- `STOPPED_F8` is written only by the F8 path.

- [ ] **Step 1: Add failing plugin tests for truthful stop origin**

Add tests that directly invoke existing package-visible hooks/reflection as appropriate:

```java
@Test
public void testExecutionFailureDoesNotBecomeStoppedF8()
{
    GeAutoTraderPlugin plugin = new GeAutoTraderPlugin();
    setLastReason(plugin, GeReasonCode.EXECUTION_TARGET_UNAVAILABLE);
    plugin.stopForExecutionFailure();

    assertEquals(GeReasonCode.EXECUTION_TARGET_UNAVAILABLE, plugin.getLastReason());
    assertFalse(plugin.isManualRestartAllowed());
}
```

Also add a test proving F8 still sets `STOPPED_F8` and `manualRestartAllowed=true`.

- [ ] **Step 2: Create the stop-cause enum**

```java
package net.runelite.client.plugins.geautotrader;

enum GeStopCause
{
    NONE,
    F8,
    EXECUTION_FAILURE
}
```

- [ ] **Step 3: Track stop cause in the plugin without changing the safety gate**

Add:

```java
private GeStopCause stopCause = GeStopCause.NONE;
```

Initialize/reset it to `NONE` in startup/shutdown/manual restart. In `stopForExecutionFailure()` set only:

```java
stopped.set(true);
stopCause = GeStopCause.EXECUTION_FAILURE;
manualRestartAllowed = false;
restartArmed = false;
restartRequested = false;
```

In F8 handling set:

```java
stopped.set(true);
stopCause = GeStopCause.F8;
manualRestartAllowed = true;
lastReason = GeReasonCode.STOPPED_F8;
```

Do not overwrite an existing execution reason inside `stopForExecutionFailure()`.

- [ ] **Step 4: Prevent later ticks from translating an execution stop into `STOPPED_F8`**

Before calling `stateMachine.onTick`, if the plugin is stopped because `EXECUTION_FAILURE`, retain `lastReason` and return read-only after refreshing `lastState`/market:

```java
if (stopped.get() && stopCause == GeStopCause.EXECUTION_FAILURE)
{
    lastAction = GePlannedAction.none();
    return;
}
```

The state machine remains fail-closed through its existing stopped supplier, but the orchestration layer no longer asks it to recalculate a synthetic `STOPPED_F8` reason for an execution failure.

- [ ] **Step 5: Run plugin tests**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeAutoTraderPluginTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeStopCause.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeAutoTraderPlugin.java runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeAutoTraderPluginTest.java
git commit -m "fix: preserve V6 execution failure reasons"
```

---

### Task 3: Serialize every phase that manipulates the shared GE UI

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineSetupSerializationTest.java`

**Interfaces:**
- `anotherSetupWorkflowInProgress(int slot)` remains the single lock check used before an idle slot starts a new buy.
- Pure monitor phases remain unlocked.

- [ ] **Step 1: Add failing tests for collect and abort ownership**

Extend the serialization test so another slot remains idle while slot 1 is in each shared phase:

```java
WAIT_ABORT_READY
WAIT_ABORT_RESULT
WAIT_BUY_COLLECT_READY
WAIT_BUY_COLLECT_RESULT
WAIT_SELL_COLLECT_READY
WAIT_SELL_COLLECT_RESULT
```

The test should assert the second slot emits `NONE`, not `OPEN_BUY`.

- [ ] **Step 2: Add a positive test that monitoring does not hold the shared lock**

Place slot 1 into `MONITOR_BUY` with a matching live BUYING observation and make slot 2 empty. Assert slot 2 may emit `OPEN_BUY` when a candidate exists. Repeat or parameterize for `MONITOR_SELL`.

- [ ] **Step 3: Expand `isSetupWorkflowPhase`**

Add the missing shared-interface phases:

```java
case WAIT_ABORT_READY:
case WAIT_ABORT_RESULT:
case WAIT_BUY_COLLECT_READY:
case WAIT_BUY_COLLECT_RESULT:
case WAIT_SELL_COLLECT_READY:
case WAIT_SELL_COLLECT_RESULT:
    return true;
```

Do not add `MONITOR_BUY` or `MONITOR_SELL`.

- [ ] **Step 4: Run serialization + state-machine tests**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeTradeStateMachineSetupSerializationTest" --tests "net.runelite.client.plugins.geautotrader.GeTradeStateMachineBuyTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineSetupSerializationTest.java
git commit -m "fix: serialize shared GE workflow phases"
```

---

### Task 4: Audit every dispatcher/input action required by the lifecycle

**Files:**
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeActionDispatcherTest.java`
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GePromptInputServiceTest.java`
- Modify only if a new RED test requires it: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeActionDispatcher.java`
- Modify only if a new RED test requires it: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeWidgetActionResolver.java`
- Modify only if a new RED test requires it: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GePromptInputService.java`

**Interfaces:**
- `GeActionDispatcher.dispatch(GePlannedAction, GeObservedState)` returns `OK`, `EXECUTION_TARGET_UNAVAILABLE`, or `EXECUTION_REJECTED`.
- `GePromptInputService` types only when state proves the required prompt mode.

- [ ] **Step 1: Add/confirm focused tests for all widget actions**

Cover these action types with exact target assertions:

```text
OPEN_BUY
OPEN_SELL
OPEN_OFFER
SELECT_ITEM
SELECT_SELL_ITEM
OPEN_QUANTITY
OPEN_PRICE
CONFIRM
ABORT_BUY
COLLECT
```

Use tagged and untagged action aliases where RuneLite may format text. Require unique targets; duplicate matches must return `EXECUTION_TARGET_UNAVAILABLE`.

- [ ] **Step 2: Add/confirm prompt-input tests**

For `TYPE_ITEM_SEARCH`, `TYPE_QUANTITY`, and `TYPE_PRICE`, assert:

```java
assertEquals(GeReasonCode.EXECUTION_REJECTED,
    service.typeItemSearch("Tomato", stateWithPrompt(GePromptMode.UNKNOWN)));
assertEquals(GeReasonCode.OK,
    service.typeItemSearch("Tomato", stateWithPrompt(GePromptMode.ITEM_SEARCH)));
```

Also retain the stopped-gate test proving no key events are emitted after stop.

- [ ] **Step 3: Run the dispatcher/input tests before production edits**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeActionDispatcherTest" --tests "net.runelite.client.plugins.geautotrader.GePromptInputServiceTest"
```

If all tests are already green, do not change production code in this task. If a focused test is red, make only the smallest alias/resolver/input change required by that test.

- [ ] **Step 4: Re-run until green and commit only real changes**

```bash
git add runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeActionDispatcherTest.java runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GePromptInputServiceTest.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeActionDispatcher.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeWidgetActionResolver.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GePromptInputService.java
git commit -m "test: cover V6 GE execution actions"
```

---

### Task 5: Add bounded UI proof timeouts without changing the 20-minute trading timeout

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeReasonCode.java`
- Create: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineProofTimeoutTest.java`

**Interfaces:**
- Add `UI_PROOF_TIMEOUT = Duration.ofSeconds(10)` for short UI transition waits.
- Preserve `BUY_TIMEOUT = Duration.ofMinutes(20)` exactly for placed buys.
- Each `SlotContext` records when its current phase was entered.

- [ ] **Step 1: Add one failing proof-timeout test**

Example: after `OPEN_BUY`, keep feeding a state where the setup never opens. At `t0 + 11 seconds`, assert no new executable action is emitted and `getLastReason()` becomes `CONFIRM_STATE_TIMEOUT` (or introduce `UI_STATE_TIMEOUT` if a distinct code makes the test clearer).

Prefer a new reason code because the timeout applies to more than confirm:

```java
UI_STATE_TIMEOUT,
```

- [ ] **Step 2: Add phase-entry timestamp to `SlotContext`**

Use an `Instant phaseEnteredAt` initialized/reset with the context. Add a helper:

```java
private void transition(SlotContext context, GeTradePhase phase, Instant now)
{
    context.phase = phase;
    context.phaseEnteredAt = now;
}
```

Replace direct phase assignments for executable/wait transitions with `transition(...)` so timing is deterministic in tests.

- [ ] **Step 3: Define which phases are bounded UI-proof waits**

Use a helper returning true for setup/search/selection/quantity/price/confirm/open-offer/collect proof phases, excluding `IDLE`, `MONITOR_BUY`, `MONITOR_SELL`, and the 20-minute buy business timeout.

Before stepping the current phase:

```java
if (isUiProofPhase(context.phase)
    && context.phaseEnteredAt != null
    && now.isAfter(context.phaseEnteredAt.plus(UI_PROOF_TIMEOUT)))
{
    lastReason = GeReasonCode.UI_STATE_TIMEOUT;
    return GePlannedAction.none();
}
```

This records failure; plugin-level fail-close should occur only when an emitted execution fails. A pure proof timeout pauses action emission and makes the reason visible without guessing success.

- [ ] **Step 4: Add tests proving monitor phases do not use the short timeout and buy timeout remains 20 minutes from placed time**

Reuse existing timeout tests where possible. Explicitly assert a `MONITOR_BUY` at 11 seconds does not become `UI_STATE_TIMEOUT`.

- [ ] **Step 5: Run timeout/state-machine tests**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeTradeStateMachineProofTimeoutTest" --tests "net.runelite.client.plugins.geautotrader.GeTradeStateMachineBuyTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeReasonCode.java runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineProofTimeoutTest.java
git commit -m "fix: bound V6 GE proof waits"
```

---

### Task 6: Prove the complete deterministic buy -> collect -> sell -> collect lifecycle

**Files:**
- Create: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineLifecycleTest.java`
- Modify if the RED lifecycle exposes a real transition defect: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java`

**Interfaces:**
- Uses real `GeTradeStateMachine`, `GeTradeLedger`, `GeLimitLedger`, market snapshot, and `GeObservedState` snapshots.
- Produces a deterministic sequence of `GePlannedActionType` values ending with slot context `IDLE` and no active sell obligation.

- [ ] **Step 1: Build a state helper inside the test**

The helper must let the test specify:

```java
state(
    long gp,
    GePromptMode prompt,
    GeTradeSide setupSide,
    int setupItemId,
    int setupQuantity,
    int setupPrice,
    GeObservedSlot slot1,
    int inventoryQty)
```

Use one fixed candidate, e.g. item ID `1982`, name `Tomato`, quantity `100`, buy price `160`, sell price `180`.

- [ ] **Step 2: Assert the exact buy setup sequence**

Feed snapshots and assert actions in order:

```text
OPEN_BUY
TYPE_ITEM_SEARCH
SELECT_ITEM
OPEN_QUANTITY
TYPE_QUANTITY
OPEN_PRICE
TYPE_PRICE
CONFIRM
```

After each emitted action, feed a later snapshot proving the expected transition. Repeated identical snapshots while waiting must emit `NONE` rather than duplicate the previous action.

- [ ] **Step 3: Assert placed buy and collection**

Feed a matching `BUYING` then `BOUGHT` slot. Assert:

```text
OPEN_OFFER
COLLECT
```

Then feed an empty slot with inventory increased by the actual filled quantity. Assert the buy obligation is replaced by a sell obligation for exactly that inventory delta and the next action is `OPEN_SELL`.

- [ ] **Step 4: Assert exact sell setup/placement sequence**

Feed SELL setup/inventory/item/quantity/price snapshots and assert:

```text
SELECT_SELL_ITEM
OPEN_QUANTITY
TYPE_QUANTITY
OPEN_PRICE
TYPE_PRICE
CONFIRM
```

Then prove `SELLING` / `SOLD` with matching item, quantity, and price.

- [ ] **Step 5: Assert final GP collection and return to IDLE**

Assert:

```text
OPEN_OFFER
COLLECT
```

Then feed slot empty + GP greater than the captured pre-collect GP. Assert:

```java
assertEquals(GeTradePhase.IDLE, machine.getPhase(1));
assertNull(machine.getCurrentCandidate(1));
```

Also assert the sell obligation has been removed from `GeTradeLedger`.

- [ ] **Step 6: Prove three-slot scheduling semantics in the same test or a companion test**

While slot 1 is in shared setup, slot 2 must not emit `OPEN_BUY`. Once slot 1 reaches `MONITOR_BUY`, slot 2 may start a new `OPEN_BUY` when capital/candidate rules allow it.

- [ ] **Step 7: Run lifecycle test and fix only defects it exposes**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeTradeStateMachineLifecycleTest"
```

Repeat RED -> minimal production fix -> GREEN until the full deterministic lifecycle passes.

- [ ] **Step 8: Commit**

```bash
git add runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachineLifecycleTest.java runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeTradeStateMachine.java
git commit -m "test: prove complete V6 GE lifecycle"
```

---

### Task 7: Keep bridge diagnostics aligned with the live item-search fallback

**Files:**
- Modify: `runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeGeInputClassifierTest.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeInputClassifier.java`
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeLiveGeSections.java`

**Interfaces:**
- Diagnostics only; Auto-Trader execution remains independent of the bridge.
- Bridge `geInput.mode` should report `ITEM_SEARCH` for the same observed screen that V6 classifies as item search.

- [ ] **Step 1: Add a failing bridge classifier test**

Refactor classifier signature to accept whether the full input widget is visible and the minimum setup identity needed for the narrow fallback. Test:

```java
assertEquals("ITEM_SEARCH", GeBridgeGeInputClassifier.classify(
    true,
    99,
    null,
    true,
    true,
    -1));
```

Where the final arguments represent visible input, BUY side, and no selected item. Keep tests showing selected item / hidden input do not become search.

- [ ] **Step 2: Pass the needed values from `GeBridgeLiveGeSections`**

Do not introduce any dependency from Auto-Trader to the bridge. This is only so PowerShell diagnostics stop saying `UNKNOWN` for a screen V6 correctly treats as search.

- [ ] **Step 3: Run bridge tests**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeGeInputClassifier.java runelite-client/src/main/java/net/runelite/client/plugins/gebridge/GeBridgeLiveGeSections.java runelite-client/src/test/java/net/runelite/client/plugins/gebridge/GeBridgeGeInputClassifierTest.java
git commit -m "fix: align GE bridge input diagnostics"
```

---

### Task 8: Full regression, build, and one live end-to-end validation

**Files:**
- No planned production changes. Any failure discovered here returns to the responsible task and gets a new RED regression test before a fix.

**Interfaces:**
- Final output is a shaded client JAR plus fresh test evidence.

- [ ] **Step 1: Run all GE Auto-Trader tests**

```powershell
cd C:\Users\matt\Desktop\runelite-git
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run all GE Bridge tests**

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build the shaded JAR**

```powershell
.\gradlew.bat :client:shadowJar
```

Expected: `BUILD SUCCESSFUL` and a fresh `runelite-client\build\libs\client-*-shaded.jar`.

- [ ] **Step 4: Launch the exact built JAR**

```powershell
java -ea -jar "C:\Users\matt\Desktop\runelite-git\runelite-client\build\libs\client-1.12.39-SNAPSHOT-shaded.jar" --developer-mode
```

If the versioned filename changes, resolve the newest shaded JAR from PowerShell only after changing to the repository directory.

- [ ] **Step 5: Live validation without manual interference**

Start on the main GE screen. Enable V6 once. Do not click GE controls and do not press F8. Observe:

```text
RUNNING
OPEN_BUY / item search progresses
quantity and price are set
buy confirms into BUYING/BOUGHT
buy items are collected
sell setup is created
sell confirms into SELLING/SOLD
sold GP is collected when filled
slot returns to reusable IDLE
```

A sell that has not filled yet is not a failure; no-timeout/no-reprice behavior is intentional. The required live proof before unrestricted continuous mode is at least buy -> collect -> sell placement, with final GP collection when the sell actually fills.

- [ ] **Step 6: Capture diagnostic bridge state only if a live transition stops**

Use the relevant section rather than dumping everything:

```powershell
$r = Invoke-RestMethod -Uri "http://127.0.0.1:17654/state" -TimeoutSec 3
$r.ge | ConvertTo-Json -Depth 6
$r.geInput | ConvertTo-Json -Depth 6
$r.geActions | ConvertTo-Json -Depth 8
```

The overlay's exact structured reason is the first diagnostic signal. Do not press F8 after a failure because that deliberately changes the stop state.

- [ ] **Step 7: Final verification commit only after fresh green output**

No production code should be changed merely to make this step look green. If all tests/build pass and live validation reaches the required stage, record the final branch HEAD and runtime evidence in the handoff message.
