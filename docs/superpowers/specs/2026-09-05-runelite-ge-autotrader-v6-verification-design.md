# RuneLite GE Auto-Trader V6 Single-Command Verification Design

## Goal

Replace repeated stage-by-stage manual verification with one repeatable verification entry point that exercises the V6 trading lifecycle and the surrounding widget/bridge safety logic in one run.

## User-facing workflow

The user should normally run one command from the repository root. That command must run the complete GE Auto-Trader and GE Bridge regression set and then build the shaded RuneLite jar. Individual tests remain available for development, but they are no longer the normal user verification workflow.

## Coverage

The consolidated verification must cover:

- login/resync safety and stale-reason recovery
- market candidate selection and reservation accounting
- buy setup, search selection, quantity, price and confirm
- active-buy monitoring and 20-minute abort path
- completed/cancelled buy offer opening and collection
- transition from collected buy inventory into sell setup
- sell setup, monitoring, completed sell opening and GP collection
- restart persistence and adoption/recovery of slot 1-3 offers
- setup-workflow serialization across the three F2P slots
- exact GE slot-root targeting
- search-result widget traversal
- offer-status Collect and Abort targets on the live GE window root
- bridge state/action readers and widget action resolution
- proof timeouts and fail-closed execution behavior

## Architecture

Keep the existing focused tests because they are useful for diagnosis. Add a single JUnit suite class named `GeAutoTraderV6EndToEndTest` that groups the high-value Auto-Trader regression classes under one Gradle filter. Add a PowerShell verification script that runs the consolidated Auto-Trader suite, all GE Bridge tests, and `:client:shadowJar`, stopping on the first failure.

The lifecycle simulation remains deterministic and does not contact Jagex. The existing full state-machine lifecycle test supplies the normal buy -> collect -> sell -> collect path. Focused tests remain the authoritative checks for restart recovery, timeouts, slot identity and widget resolution.

## Live-only boundary

A unit/integration test cannot prove that Jagex has not changed the live widget tree. The bridge and dispatcher mocks must model the observed live widget/action layout, including Collect and Abort being resolved from the Grand Exchange window rather than assuming they live under the offer setup container. One live RuneLite run remains the final smoke check after the consolidated suite and jar build pass.

## Success criteria

A single command runs all Auto-Trader verification, all bridge regression tests and the shaded-jar build. Any failure returns a non-zero exit code. A successful run prints a clear final success message and is the only local verification normally requested from the user.