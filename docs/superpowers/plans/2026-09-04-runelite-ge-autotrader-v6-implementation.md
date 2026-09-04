# RuneLite GE Auto-Trader V6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a private RuneLite V6 plugin that automatically scans F2P GE opportunities, buys, monitors fills, collects, sells, recycles GP, tracks four-hour limits, uses only GE slots 1-3, and stops immediately on F8 without Python in the live execution path.

**Architecture:** Add a new `net.runelite.client.plugins.geautotrader` package. V6 reads state and executes GE UI actions inside RuneLite. Game/menu actions use `Client.menuAction(...)`; text/number prompt entry is isolated behind `GePromptInputService` and dispatches Java key events only after the state reader proves the expected GE prompt mode. Every action is one-shot and accepted only after a later RuneLite state observation proves the expected transition.

**Tech Stack:** Java, RuneLite `runelite-api`/`runelite-client`, Gson/HTTP client already available in RuneLite, JUnit 4, Mockito, RuneLite event bus/client thread, Java executor for market HTTP.

**Spec:** `docs/superpowers/specs/2026-09-04-runelite-ge-autotrader-v6-design.md`

## Global Constraints

- F2P worlds only.
- Automation owns GE slots 1, 2, and 3 only.
- F8 is the hard emergency stop and prevents all later automated game actions.
- Buy timeout is 20 minutes from proved offer placement.
- Each buy obligation may auto-abort at most once.
- Sell offers never auto-timeout, auto-abort, or auto-reprice.
- Four-hour limits are consumed by actual fills only.
- AI/ML is shadow-only and cannot authorize or execute actions.
- No Python, OCR, PyAutoGUI, screen coordinates, Java `Robot`, or localhost HTTP round trip may be required for live execution.
- No blocking network I/O, sleeps, or unbounded widget traversal on the RuneLite client thread.
- Every game action is followed by exact state verification before the next action is permitted.
- V5 `gebridge` remains available as diagnostics/rollback until V6 live validation is complete.

---

## File Structure

Create under `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/`:

- `GeAutoTraderPlugin.java` — lifecycle/event wiring only.
- `GeAutoTraderConfig.java` — enable flag, market thresholds, hard ceilings.
- `GeReasonCode.java` — structured runtime outcomes.
- `GeTradeSide.java` — BUY/SELL.
- `GeObservedSlot.java` — immutable slot observation.
- `GeObservedState.java` — immutable whole-state snapshot.
- `GeTradeObligation.java` — lifecycle record for one buy/sell obligation.
- `GeTradeLedger.java` — obligations and slot ownership.
- `GeLimitLedger.java` — four-hour actual-fill ledger.
- `GeSafetyPolicy.java` — global/transition safety gates.
- `GeMarketItem.java` / `GeMarketSnapshot.java` — immutable market data.
- `GeMarketService.java` — background Wiki price fetch/cache.
- `GeCandidate.java` / `GeOpportunitySelector.java` — deterministic filtering/ranking.
- `GeStateReader.java` — exact RuneLite state reader.
- `GeWidgetActionSpec.java` — immutable menu-action invocation fields.
- `GeWidgetActionResolver.java` — resolve exact current GE widget ops without screen geometry.
- `GePromptMode.java` / `GePromptInputService.java` — guarded item/quantity/price prompt input.
- `GeExecutionService.java` — one-shot `Client.menuAction(...)` execution.
- `GeTradePhase.java` — deterministic phase enum.
- `GeTradeStateMachine.java` — buy/collect/sell/timeout scheduler.
- `GeAutoTraderOverlay.java` — compact status surface.

Mirror focused tests under `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/`.

---

### Task 1: Core state model and fail-closed safety

**Files:**
- Create: `.../geautotrader/GeReasonCode.java`
- Create: `.../geautotrader/GeTradeSide.java`
- Create: `.../geautotrader/GeObservedSlot.java`
- Create: `.../geautotrader/GeObservedState.java`
- Create: `.../geautotrader/GeSafetyPolicy.java`
- Test: `.../geautotrader/GeSafetyPolicyTest.java`

**Interfaces:**
- `GeSafetyPolicy.evaluateGlobal(GeObservedState state, boolean enabled, boolean stopped)` → `GeReasonCode`
- `GeSafetyPolicy.canUseSlot(int slot)` → `boolean`
- Global success code is `OK`.

- [ ] **Step 1: Write failing safety tests**

```java
@Test
public void testMembersWorldFailsClosed()
{
    GeObservedState s = Fixtures.loggedInState(true);
    assertEquals(GeReasonCode.WORLD_NOT_F2P,
        GeSafetyPolicy.evaluateGlobal(s, true, false));
}

@Test
public void testOnlySlotsOneToThreeAreOwned()
{
    assertTrue(GeSafetyPolicy.canUseSlot(1));
    assertTrue(GeSafetyPolicy.canUseSlot(3));
    assertFalse(GeSafetyPolicy.canUseSlot(4));
}

@Test
public void testStoppedAlwaysWins()
{
    assertEquals(GeReasonCode.STOPPED_F8,
        GeSafetyPolicy.evaluateGlobal(Fixtures.loggedInState(false), true, true));
}
```

- [ ] **Step 2: Run RED**

Run:
```bash
./gradlew :runelite-client:test --tests "net.runelite.client.plugins.geautotrader.GeSafetyPolicyTest"
```
Expected: FAIL because V6 classes do not exist.

- [ ] **Step 3: Implement minimal immutable models and policy**

`GeReasonCode` must include at least:
```java
OK,
STOPPED_F8,
DISABLED,
GAME_NOT_LOGGED_IN,
WORLD_NOT_F2P,
LOGIN_RESYNC,
GE_NOT_OPEN,
BLOCKER_ACTIVE,
SLOT_IDENTITY_CHANGED,
SETUP_NOT_OPEN,
SETUP_ITEM_MISMATCH,
SETUP_QUANTITY_MISMATCH,
SETUP_PRICE_MISMATCH,
SETUP_SIDE_MISMATCH,
CONFIRM_STATE_TIMEOUT,
COLLECT_STATE_MISMATCH,
INVENTORY_MISMATCH,
INSUFFICIENT_GP,
BUY_LIMIT_EXHAUSTED,
MARKET_DATA_UNAVAILABLE,
MARKET_DATA_STALE,
EXECUTION_TARGET_UNAVAILABLE,
EXECUTION_REJECTED
```

`canUseSlot` must be exactly:
```java
return slot >= 1 && slot <= 3;
```

- [ ] **Step 4: Run GREEN**

Run the same focused test; expected PASS.

- [ ] **Step 5: Commit**

```bash
git add runelite-client/src/main/java/net/runelite/client/plugins/geautotrader runelite-client/src/test/java/net/runelite/client/plugins/geautotrader
git commit -m "feat(v6): add GE auto-trader state and safety model"
```

---

### Task 2: Trade ledger, capital reservations, and four-hour limits

**Files:**
- Create: `GeTradeObligation.java`
- Create: `GeTradeLedger.java`
- Create: `GeLimitLedger.java`
- Test: `GeTradeLedgerTest.java`
- Test: `GeLimitLedgerTest.java`

**Interfaces:**
- `GeTradeLedger.reserveBuy(...)` creates an unproved reservation.
- `markPlaced(...)` records proved placement time only after slot proof.
- `reservedGp()` sums outstanding buy reservations.
- `GeLimitLedger.recordFill(int itemId, int quantity, Instant at)` records actual fills.
- `remaining(int itemId, int configuredLimit, Instant now)` expires entries older than four hours.

- [ ] **Step 1: Write failing ledger tests**

```java
@Test
public void testPartialFillConsumesOnlyActualQuantity()
{
    GeLimitLedger ledger = new GeLimitLedger();
    Instant now = Instant.parse("2026-09-04T18:00:00Z");
    ledger.recordFill(1127, 40, now);
    assertEquals(85, ledger.remaining(1127, 125, now.plusSeconds(60)));
}

@Test
public void testFourHourFillExpires()
{
    GeLimitLedger ledger = new GeLimitLedger();
    Instant t0 = Instant.parse("2026-09-04T10:00:00Z");
    ledger.recordFill(1127, 125, t0);
    assertEquals(125, ledger.remaining(1127, 125, t0.plus(Duration.ofHours(4)).plusSeconds(1)));
}
```

Add a `GeTradeLedgerTest` proving two active buys reserve GP independently and cannot oversubscribe available capital.

- [ ] **Step 2: Run RED** with both test classes.
- [ ] **Step 3: Implement ledgers using plain collections and immutable obligation snapshots.**
- [ ] **Step 4: Run GREEN.**
- [ ] **Step 5: Commit** `feat(v6): add trade and four-hour limit ledgers`.

---

### Task 3: Background market cache and deterministic candidate selection

**Files:**
- Create: `GeMarketItem.java`
- Create: `GeMarketSnapshot.java`
- Create: `GeMarketService.java`
- Create: `GeCandidate.java`
- Create: `GeOpportunitySelector.java`
- Test: `GeOpportunitySelectorTest.java`
- Test: `GeMarketServiceTest.java`

**Interfaces:**
- `GeMarketService.refreshAsync()` never blocks client thread.
- `GeMarketService.snapshot()` returns last immutable cache or empty.
- `GeOpportunitySelector.select(GeMarketSnapshot, long availableGp, GeLimitLedger, GeTradeLedger, GeAutoTraderConfig)` → ordered `List<GeCandidate>`.

- [ ] **Step 1: Write failing selector tests** for F2P filtering, after-tax positive spread, ROI floor, volume floor, GP ceiling, duplicate obligation rejection, and remaining-limit truncation.

Example:
```java
@Test
public void testCandidateQuantityIsCappedByRemainingLimitAndCapital()
{
    // 125 remaining limit, only enough GP for 100 -> expect 100.
}
```

- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement Wiki mapping/latest/5m DTO parsing and immutable cache.** Network work runs on a single background executor; completion publishes a new snapshot atomically.
- [ ] **Step 4: Implement deterministic ranking** using after-tax unit profit, ROI, recent volume and capital efficiency; no ML authorization.
- [ ] **Step 5: Run GREEN.**
- [ ] **Step 6: Commit** `feat(v6): add market cache and opportunity selector`.

---

### Task 4: Exact RuneLite state reader

**Files:**
- Create: `GeStateReader.java`
- Create: `GePromptMode.java`
- Reuse read-only logic from V5 `gebridge` readers where correct.
- Test: `GeStateReaderTest.java`

**Interfaces:**
- `GeStateReader.read()` → `GeObservedState`
- Reads `Client.getGrandExchangeOffers()`, exact inventory/GP, GE setup vars, current interface state, login/world type, and blocker state.
- `GeObservedState` must carry exact setup item ID, quantity, price, side, and `GePromptMode`.

- [ ] **Step 1: Write failing tests** for F2P detection, exact slot state conversion, GP extraction, setup values, and prompt mode.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement using current V5 readers as references, but return V6 models directly.** No JSON and no geometry are required.
- [ ] **Step 4: Run GREEN.**
- [ ] **Step 5: Commit** `feat(v6): add exact RuneLite GE state reader`.

---

### Task 5: Geometry-free action resolver and execution service

**Files:**
- Create: `GeWidgetActionSpec.java`
- Create: `GeWidgetActionResolver.java`
- Create: `GeExecutionService.java`
- Test: `GeWidgetActionResolverTest.java`
- Test: `GeExecutionServiceTest.java`

**Interfaces:**

`GeWidgetActionSpec` stores exactly:
```java
int param0;
int param1;
MenuAction action;
int identifier;
int itemId;
String option;
String target;
```

`GeExecutionService.execute(GeWidgetActionSpec spec)` invokes exactly:
```java
client.menuAction(
    spec.getParam0(),
    spec.getParam1(),
    spec.getAction(),
    spec.getIdentifier(),
    spec.getItemId(),
    spec.getOption(),
    spec.getTarget());
```

The resolver turns a currently visible RuneLite GE widget action into a `CC_OP`/appropriate widget action using its actual widget id/index/action index; it never invents screen coordinates.

- [ ] **Step 1: Write failing resolver tests** proving exact action index, widget id/index, item id, ambiguity rejection, hidden-widget rejection, and slots 4-8 rejection.
- [ ] **Step 2: Write failing execution test** verifying `client.menuAction(...)` is called once with the exact stored fields.
- [ ] **Step 3: Run RED.**
- [ ] **Step 4: Implement resolver with bounded traversal only.** Reuse the 512-node cap concept from the V5 resolver; no recursion that can monopolize the client thread.
- [ ] **Step 5: Implement one-shot execution service.** It must refuse execution when the plugin stop flag is set.
- [ ] **Step 6: Run GREEN.**
- [ ] **Step 7: Commit** `feat(v6): add geometry-free RuneLite GE execution`.

---

### Task 6: Guarded GE prompt input without Python

**Files:**
- Create: `GePromptInputService.java`
- Test: `GePromptInputServiceTest.java`

**Interfaces:**
- `typeItemSearch(String text, GeObservedState state)` only when `state.promptMode == ITEM_SEARCH`.
- `typeQuantity(int quantity, GeObservedState state)` only when `QUANTITY`.
- `typePrice(int price, GeObservedState state)` only when `PRICE`.
- Input dispatches Java `KeyEvent`s to RuneLite's canvas; Enter is sent once after the value.
- No clipboard use and no global desktop hooks.

- [ ] **Step 1: Write failing tests** that wrong prompt mode returns `SETUP_*`/`EXECUTION_REJECTED` and emits zero key events.
- [ ] **Step 2: Write a test adapter** around key dispatch so unit tests capture characters instead of touching AWT.
- [ ] **Step 3: Run RED.**
- [ ] **Step 4: Implement ASCII item-name/digit entry and one Enter.** Validate item search strings against item name chosen by the market mapping; quantity/price must be positive integers within config ceiling.
- [ ] **Step 5: Run GREEN.**
- [ ] **Step 6: Commit** `feat(v6): add guarded GE prompt input`.

---

### Task 7: Deterministic automatic BUY state machine

**Files:**
- Create: `GeTradePhase.java`
- Create: `GeTradeStateMachine.java`
- Test: `GeTradeStateMachineBuyTest.java`

**Interfaces:**
- `onTick(GeObservedState state, Instant now)` returns at most one `GePlannedAction`.
- Repeated ticks in the same phase must not produce duplicate confirm/actions.
- Buy phases:
  `IDLE -> OPEN_BUY -> WAIT_BUY_SETUP -> ITEM_SEARCH -> WAIT_ITEM_SELECTED -> QUANTITY -> WAIT_QUANTITY -> PRICE -> WAIT_PRICE -> CONFIRM -> WAIT_BUY_SLOT -> MONITOR_BUY`.

- [ ] **Step 1: Write a successful buy lifecycle test** that advances fake observations phase-by-phase and asserts exactly one action per phase.
- [ ] **Step 2: Write rejection tests** for wrong selected item, quantity, price, side, slot identity change, insufficient GP, market stale, and duplicate confirm on repeated tick.
- [ ] **Step 3: Run RED.**
- [ ] **Step 4: Implement minimal buy state machine** using Task 1 safety, Task 2 ledgers, Task 3 candidate selector, Task 4 state reader contract, and Task 5/6 execution abstractions.
- [ ] **Step 5: Require proved placement before starting timer.** `markPlaced()` occurs only after slot state becomes matching `BUYING` or `BOUGHT`.
- [ ] **Step 6: Run GREEN.**
- [ ] **Step 7: Commit** `feat(v6): add automatic GE buy state machine`.

---

### Task 8: Collect, automatic SELL, and exact fill accounting

**Files:**
- Modify: `GeTradeStateMachine.java`
- Modify: `GeTradeLedger.java`
- Test: `GeTradeStateMachineSellTest.java`
- Test: `GeTradeStateMachineCollectTest.java`

**Interfaces:**
- Buy collect creates sell obligation from actual inventory delta.
- Sell placement price is fixed at first placement and never changed automatically.

- [ ] **Step 1: Write failing collect test**: BOUGHT state + inventory delta of 73 must create a sell obligation for exactly 73 and record a 73-unit buy fill in `GeLimitLedger`.
- [ ] **Step 2: Write failing partial-cancel collect test** proving only actual filled amount is sold/limited.
- [ ] **Step 3: Write failing sell lifecycle test** through `OPEN_SELL -> SELECT_SELL_ITEM -> QUANTITY -> PRICE -> CONFIRM -> SELLING/SOLD`.
- [ ] **Step 4: Write explicit invariant tests** asserting no sell timeout, no auto-abort, no auto-reprice method/path.
- [ ] **Step 5: Run RED.**
- [ ] **Step 6: Implement collect/sell phases and exact delta validation.**
- [ ] **Step 7: Run GREEN.**
- [ ] **Step 8: Commit** `feat(v6): add collect and automatic sell lifecycle`.

---

### Task 9: 20-minute buy timeout, one-abort rule, and three-slot scheduler

**Files:**
- Modify: `GeTradeStateMachine.java`
- Modify: `GeTradeLedger.java`
- Test: `GeTradeSchedulerTest.java`

- [ ] **Step 1: Write failing timeout test** at 19:59 (no abort) and 20:00+ (one abort action).
- [ ] **Step 2: Write failing second-abort test** proving an obligation with `abortCount == 1` cannot emit another Abort.
- [ ] **Step 3: Write failing three-slot scheduling test** showing slots 1-3 can independently hold buy/sell obligations and slot 4 is untouched.
- [ ] **Step 4: Run RED.**
- [ ] **Step 5: Implement event/tick-driven scheduler** with one action maximum per client tick and deterministic slot ordering.
- [ ] **Step 6: Run GREEN.**
- [ ] **Step 7: Commit** `feat(v6): add timeout abort and three-slot scheduling`.

---

### Task 10: Plugin wiring, config, F8 hard stop, and visible status

**Files:**
- Create: `GeAutoTraderConfig.java`
- Create: `GeAutoTraderPlugin.java`
- Create: `GeAutoTraderOverlay.java`
- Test: `GeAutoTraderPluginTest.java`

**Interfaces:**
- Plugin starts disabled by default.
- `AtomicBoolean stopped` is checked by execution service immediately before every `client.menuAction` or key dispatch.
- F8 sets stopped and cannot auto-resume.

- [ ] **Step 1: Write failing F8 test** proving an action queued before F8 is rejected when execution is attempted after stop is set.
- [ ] **Step 2: Write failing members-world transition test** proving the next tick is `WORLD_NOT_F2P` and emits zero game actions.
- [ ] **Step 3: Run RED.**
- [ ] **Step 4: Implement plugin subscriptions** for `GameTick`, `GameStateChanged`, `GrandExchangeOfferChanged`, relevant widget/var changes, and F8 via RuneLite `KeyManager`.
- [ ] **Step 5: Implement compact overlay** with RUNNING/PAUSED/STOPPED, world, available/reserved GP, S1-S3, current candidate, last reason, and `F8 STOP` reminder.
- [ ] **Step 6: Run GREEN.**
- [ ] **Step 7: Commit** `feat(v6): wire auto-trader plugin and F8 stop`.

---

### Task 11: Migration gate, launcher update, and full verification

**Files:**
- Modify only after V6 tests pass: existing V990 launcher/setup scripts if still used to start this custom fork.
- Keep V5 `gebridge` classes until live V6 validation succeeds.
- Add/modify: `docs/superpowers/specs/2026-09-04-runelite-ge-autotrader-v6-design.md` only if implementation reveals a real contract correction.

- [ ] **Step 1: Run all V6 focused tests**

Windows:
```bat
gradlew.bat :runelite-client:test --tests "net.runelite.client.plugins.geautotrader.*"
```
Expected: PASS.

- [ ] **Step 2: Run the existing V5 bridge tests as regression**

```bat
gradlew.bat :runelite-client:test --tests "net.runelite.client.plugins.gebridge.*"
```
Expected: PASS.

- [ ] **Step 3: Run the full relevant module tests**

```bat
gradlew.bat :runelite-client:test
```
Expected: PASS.

- [ ] **Step 4: Update launcher to checkout `feat/runelite-ge-autotrader-v6` and start RuneLite without launching Python.** The launcher must stop if V6 Java tests fail.

- [ ] **Step 5: Live validation in strict sequence**
  1. V6 disabled/shadow status only.
  2. One-slot tiny-value buy.
  3. One complete buy -> collect -> sell -> collect GP cycle.
  4. Partial-fill/timeout path.
  5. Slots 1-3 scheduling.
  6. Continuous mode only after the above have fresh evidence.

- [ ] **Step 6: Only after live cycle proof, remove Python from the recommended runtime package.** Do not delete V5 rollback branch.

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "feat(v6): complete Java-first GE auto-trader migration"
```

---

## Plan Self-Review

- Spec coverage: auto-buy, monitor, collect, auto-sell, recycle GP, F2P, slots 1-3, actual-fill limits, capital reservations, 20-minute timeout, one buy abort, no sell timeout/abort/reprice, F8, privacy, threading, status and migration are each mapped to explicit tasks.
- Placeholder scan: no TBD/TODO/"implement later" steps remain.
- Type consistency: `GeObservedState`, `GeTradeLedger`, `GeLimitLedger`, `GeMarketSnapshot`, `GeWidgetActionSpec`, `GePromptInputService`, `GeExecutionService`, and `GeTradeStateMachine` have one named role and are reused consistently across tasks.
- Risk gate: live execution is not enabled until unit/integration tests pass; continuous mode is not claimed until a complete buy->collect->sell cycle is observed on the user's Windows RuneLite build.
