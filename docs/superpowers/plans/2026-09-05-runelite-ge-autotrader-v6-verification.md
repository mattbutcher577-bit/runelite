# RuneLite GE Auto-Trader V6 Single-Command Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide one local command that verifies V6 Auto-Trader logic, GE Bridge logic and the shaded RuneLite jar build.

**Architecture:** Keep the existing focused regression tests, add a JUnit suite as a stable Auto-Trader verification entry point, fix the live offer-status Collect/Abort root mismatch, and add a PowerShell wrapper that runs Auto-Trader verification, bridge regressions and `shadowJar` sequentially with fail-fast behavior.

**Tech Stack:** Java 11+, JUnit 4, Mockito, Gradle, PowerShell.

**Spec:** `docs/superpowers/specs/2026-09-05-runelite-ge-autotrader-v6-verification-design.md`

## Global Constraints

- Preserve the existing focused tests; do not replace them with one monolithic test body.
- V6 may automate only GE slots 1-3.
- Widget actions must fail closed when an exact target cannot be resolved.
- Collect and Abort on the offer-status screen must resolve against the visible Grand Exchange window root.
- Verification must stop immediately on a failing test/build step and return a non-zero process exit code.
- The normal user workflow must be one verification command rather than repeated stage-specific commands.

---

### Task 1: Align offer-status Collect and Abort targeting

**Files:**
- Modify: `runelite-client/src/main/java/net/runelite/client/plugins/geautotrader/GeActionDispatcher.java`
- Test: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeActionDispatcherTest.java`

**Interfaces:**
- Consumes: `GeWidgetActionResolver.findUnique(Widget, String...)`
- Produces: `offerStatusRoot()` returning the visible GE window used by `ABORT_BUY` and `COLLECT`.

- [ ] **Step 1: Keep the existing failing regressions**

Use the already-added tests that place `Abort offer` and `Collect items` only under `WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER` while a visible offer container exists.

- [ ] **Step 2: Implement the minimal production change**

Change `ABORT_BUY` and `COLLECT` dispatch to search `offerStatusRoot()` instead of `setupRoot()`, with:

```java
private Widget offerStatusRoot()
{
    return visible(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
}
```

Keep quantity, price and confirm on `setupRoot()`.

- [ ] **Step 3: Verify through the consolidated suite**

Run the new consolidated verification entry point from Task 2 rather than asking for an isolated dispatcher command.

---

### Task 2: Add the V6 Auto-Trader verification suite

**Files:**
- Create: `runelite-client/src/test/java/net/runelite/client/plugins/geautotrader/GeAutoTraderV6EndToEndTest.java`

**Interfaces:**
- Consumes: existing public JUnit test classes in the `geautotrader` package.
- Produces: one JUnit suite class selectable by Gradle as `net.runelite.client.plugins.geautotrader.GeAutoTraderV6EndToEndTest`.

- [ ] **Step 1: Create a JUnit 4 suite**

Use `@RunWith(Suite.class)` and `@Suite.SuiteClasses` to include the lifecycle, scheduler, collection, restart recovery, reason reset, setup serialization, proof timeout, state reader, dispatcher, widget resolver, slot-root dispatch, prompt input, ledger persistence, opportunity selector and safety policy tests.

- [ ] **Step 2: Keep the normal lifecycle authoritative**

Ensure `GeTradeStateMachineLifecycleTest` remains in the suite so one run always exercises buy setup -> buy monitor -> collect -> sell setup -> sell monitor -> GP collect -> idle.

- [ ] **Step 3: Verify suite discovery**

The intended Gradle command is:

```powershell
.\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeAutoTraderV6EndToEndTest"
```

Expected: the suite and all listed child tests execute; any child failure fails the task.

---

### Task 3: Add one-command verification and build

**Files:**
- Create: `scripts/verify-ge-v6.ps1`

**Interfaces:**
- Consumes: repository-root `gradlew.bat`.
- Produces: one user command: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-ge-v6.ps1`.

- [ ] **Step 1: Run Auto-Trader suite**

```powershell
& .\gradlew.bat :client:test --tests "net.runelite.client.plugins.geautotrader.GeAutoTraderV6EndToEndTest"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

- [ ] **Step 2: Run all bridge regressions**

```powershell
& .\gradlew.bat :client:test --tests "net.runelite.client.plugins.gebridge.*"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

- [ ] **Step 3: Build the shaded jar**

```powershell
& .\gradlew.bat :client:shadowJar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

- [ ] **Step 4: Print one final result**

Print `GE Auto-Trader V6 verification passed.` only after all three commands return zero.

---

### Task 4: Final verification

**Files:**
- Verify: all files above.

- [ ] **Step 1: Pull the latest branch locally**

```powershell
git fetch gev6
git pull --ff-only
```

- [ ] **Step 2: Run exactly one verification command**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-ge-v6.ps1
```

Expected: all Auto-Trader suite tests pass, all bridge tests pass, `shadowJar` passes, and the script prints `GE Auto-Trader V6 verification passed.`

- [ ] **Step 3: Run one live smoke test**

Launch the built shaded jar and leave the GE open. Verify that V6 can continue a complete offer without `TARGET_UNAVAILABLE`, `UI_STATE_TIMEOUT`, stale `LOGIN_RESYNC`, or slot-identity drift. No repeated per-state manual testing is required.