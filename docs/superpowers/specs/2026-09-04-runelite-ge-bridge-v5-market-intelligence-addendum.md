# RuneLite GE Bridge V5 / V990 Market Intelligence and Reliability Addendum

## Status

This addendum extends the approved V5 authoritative-execution design. The original rule remains unchanged:

**RuneLite = authoritative eyes/state/geometry. Python = decisions + physical input only. AI may rank opportunities, estimate uncertainty, and learn from outcomes, but AI never decides whether an input action is safe.**

## Additional RuneLite reliability improvements

### Session identity and freshness

Protocol 5 should add:

- `bridgeInstanceId`: random identifier generated when the GE bridge plugin starts
- `snapshotSeq`: monotonically increasing snapshot sequence independent of game ticks
- `clientTick`: RuneLite `Client.getTickCount()`
- `lastLoginTick`
- `loginSettled`: true only after the configured post-login/world-hop settling window

Python treats a changed `bridgeInstanceId`, decreased/restarted sequence, login transition, or world hop as `LOGIN_RESYNC`. During `LOGIN_RESYNC` it issues no GE input, observes fresh snapshots for the settling window, rebuilds slot obligations from RuneLite state, then resumes only from a valid phase boundary.

This explicitly protects against RuneLite's known login/hopping behaviour where transient empty GE events can occur while the client is not fully logged in.

### Event-driven state refresh

The V5 bridge should refresh relevant sections immediately on the strongest RuneLite events available, while retaining normal game-tick snapshots:

- `GrandExchangeOfferChanged`
- `GrandExchangeSearched`
- `ScriptPostFired` for `ScriptID.GE_ITEM_SEARCH`
- `ScriptPostFired` for `ScriptID.GE_OFFERS_SETUP_BUILD`
- `VarbitChanged` / current GE item changes
- relevant `VarClientIntChanged` / message-layer input-mode changes
- GE `WidgetLoaded`
- GE `WidgetClosed`
- normal `GameTick`

Event handlers update section-specific ticks/sequences. Python requires the specific section involved in an action to advance or reach the expected semantic state; a newer HTTP timestamp alone does not prove the UI transition occurred.

### Search capture after plugin modification

`GrandExchangeSearched` is consumable by RuneLite plugins. V5 must avoid publishing a stale pre-modification search list as authoritative. The final search result snapshot should be captured on the client thread after the search-processing subscribers have completed, or from the post-build script state, and must carry `search.updatedTick` / `search.updatedSeq`.

### Semantic input mode without publishing prompt text

Use maintained RuneLite message-layer/input-state APIs as the first signal for GE input mode. If local widget text is required to distinguish quantity from price, Java may inspect that text internally, but the bridge serializes only the enum:

- `NONE`
- `ITEM_SEARCH`
- `QUANTITY`
- `PRICE`
- `UNKNOWN`

No prompt text, search query, chat content, arbitrary typed text, or account-identifying text is serialized or persisted.

### Raw and canonical item identity

Where item identity is exposed, publish both:

- `rawItemId`: exact RuneLite/Jagex item ID visible in that container/widget
- `canonicalItemId`: RuneLite `ItemManager.canonicalize(rawItemId)`

Matching rules:

- planned GE identity uses the exact expected tradeable item ID when known
- canonical identity may be used to match noted/unnoted representations where appropriate
- raw identity remains available to prevent accidental merging of genuinely distinct variants
- any ambiguous mapping fails closed rather than selecting by name alone

### Dedicated GE item-container probe

RuneLite currently defines dedicated inventory-container IDs including `TRADINGPOST_SELL_0..5` and `GE_OFFER_0..5`.

V5 should include a non-authoritative diagnostic probe for these containers during development. They become authoritative only if Java tests plus live sanitized traces prove a stable one-to-one semantic mapping for the relevant GE state. Until then, the tested widget/container path in the main V5 design remains authoritative.

### Offer event normalization

Normalize `GrandExchangeOfferChanged` into a privacy-safe event record with:

- slot
- item ID
- state
- total quantity
- quantity filled
- offer price
- spent
- client tick
- snapshot sequence

The bridge/state adapter must de-duplicate identical events and account for the known transition where a full-quantity `BUYING`/`SELLING` update can be immediately followed by `BOUGHT`/`SOLD`. Python should treat this as one completion progression, not as two independent trades or as a new timer start.

### Observe all GE slots, act only on first three

RuneLite V5 should observe every GE offer slot available to the client. V990's policy continues to permit physical placement/cancel/collect actions only in slots 1-3.

Global observation is useful for:

- detecting collateral collection
- avoiding false inventory accounting
- understanding GP changes
- recovering after manual intervention
- reconciling login/restart state

No additional slot becomes actionable merely because it is observed.

### Remove hard-coded Varrock-location authority

Player coordinates may remain diagnostic context, but `PLAYER_NOT_AT_GE` must not be based on a hard-coded Varrock rectangle when the actual GE interface is open and RuneLite provides valid GE widgets/state.

The authoritative GE-action precondition becomes:

- logged in
- player present
- protocol/session fresh
- GE interface open
- expected GE semantic state valid
- required widget bounds valid
- no blocker/manual hold

Coordinates cannot override an otherwise valid exact GE interface state.

## Persistent GE buy-limit ledger

### Inputs

Use:

- RuneLite item stats known GE limit where available
- exact `GrandExchangeOfferChanged` buy fill deltas
- normalized canonical/raw item identity
- timestamps and client ticks

### State

Persist per item:

- known total GE limit
- quantity bought in current four-hour window
- first qualifying purchase timestamp for the active window
- next expected refresh timestamp
- provenance/version of ledger state

### Rules

- Count actual filled buy quantity, not requested quantity.
- Partial fills increment only by the new exact fill delta.
- Completion events must not double count the preceding full-quantity `BUYING` event.
- When the four-hour window expires, reset from the first new qualifying buy.
- Unknown GE limit never becomes a fabricated number.
- Candidate quantity is capped by exact remaining known limit where known.
- If persisted history and current RuneLite state conflict materially, block new buys for that item until reconciliation rather than guessing.

## Market intelligence architecture

AI is isolated from execution safety. It produces scored recommendations consumed by the deterministic planner.

### Layer 1: deterministic market feature store

Maintain a local time-series store from public OSRS Wiki price data plus the bot's own sanitized trade outcomes.

Recommended features include:

- high/low/mid returns across multiple windows
- current and rolling spread percentage
- spread z-score
- buy/sell volume over 5m/15m/1h/6h windows
- volume imbalance
- rolling volatility and realized variance
- range position / momentum
- liquidity/missing-side flags
- VWAP deviation where meaningful
- time-of-day / weekday cyclical features
- GE tax-adjusted margin
- known remaining GE limit
- capital required
- historical own-account fill time
- own-account cancel rate
- update-shock/regime flag

This store contains no RuneLite chat/login/private text.

### Layer 2: probabilistic price/spread forecast

Preferred model family: probabilistic multi-horizon forecasting rather than a single point prediction.

Initial production target:

- predict future high-price and low-price return distributions
- horizons suited to the bot's holding periods, e.g. 5m, 10m, 15m, 30m, 1h, 2h, 3h
- output quantiles such as p10/p25/p50/p75/p90

A recent public OSRS project demonstrates a practical PyTorch LSTM quantile-regression approach using 5-minute Wiki price/volume data, 48 engineered features and multiple horizons. V990 may borrow the modelling pattern, data hygiene, leakage checks and uncertainty calibration approach, but should benchmark against simpler models before adopting a large network.

Required baselines before accepting the neural model:

- no-change / persistence
- rolling empirical quantiles
- linear/regularized model
- tree/boosted model if appropriate

The neural model is accepted only if out-of-sample skill and calibration are meaningfully better after fees/tax and realistic fill assumptions.

### Layer 3: fill probability and time-to-fill model

Price forecasts alone are insufficient. Build a separate model from the bot's own completed/cancelled orders to estimate:

- probability of a buy filling within the configured buy timeout
- expected time to partial fill
- expected time to full fill
- probability a sell will fill within useful horizons
- probability of cancellation/no-fill at the selected price distance

Features may include:

- spread
- price distance from latest high/low
- recent volume
- volume imbalance
- volatility
- item ID/canonical category embedding or grouped statistics
- time of day
- weekday
- quantity relative to recent volume
- remaining GE limit
- recent own fill performance for the item

Cancelled or still-open observations should be treated carefully as censored outcomes rather than automatically as zero-time failures where a survival-style model is used.

### Layer 4: online calibration and drift detection

Use a lightweight streaming learner for corrections that should adapt quickly to current market conditions without retraining the large forecasting model.

Candidate uses:

- online calibration of predicted fill probability
- online regression of actual slippage vs planned spread
- per-item or cluster-level fill-time correction
- anomaly detection
- concept-drift detection
- rolling model-quality metrics

The River Python library is a suitable candidate because it supports streaming `predict_one`/`learn_one`, drift/anomaly detection, forecasting, bandits and progressive validation.

Online learning must update only after an outcome is known and must never mutate the RuneLite execution safety policy.

### Layer 5: update-shock / regime guard

Game updates can materially move GE prices. Maintain an `UPDATE_SHOCK` market regime flag using:

- known OSRS update timestamps where available
- abnormal cross-sectional volatility
- abnormal item-level return/volume z-scores
- sudden spread expansion

A public OSRS Update Impact Tracker demonstrates the value of measuring pre/post-update price effects from Wiki price data.

During `UPDATE_SHOCK`:

- raise minimum confidence requirements
- reduce maximum capital per new position
- optionally exclude items directly affected by the update
- never loosen RuneLite execution checks

### Layer 6: risk-adjusted opportunity score

Do not rank candidates by raw spread alone.

Compute a deterministic score from model outputs, for example incorporating:

- expected tax-adjusted profit
- downside quantile / uncertainty penalty
- predicted buy-fill probability
- predicted sell-fill probability
- expected capital lock time
- recent volume/liquidity
- volatility penalty
- remaining GE limit
- portfolio concentration
- model calibration quality
- regime/update-shock penalty

The planner must be able to explain the score in plain numeric components for every chosen/rejected candidate.

### Optional contextual bandit, later only

A contextual bandit may eventually choose between already-safe candidate/price strategies, but only after deterministic scoring and shadow evaluation are stable.

Hard constraints remain outside the bandit:

- F2P only
- max capital/order ceilings
- exact remaining known GE limit
- first-three-slot action policy
- RuneLite safety/state preconditions
- buy timeout / one-abort rule
- sell no-timeout/no-reprice rule

The bandit may rank strategies; it never bypasses a constraint or directly issues an input.

## Shadow mode and model promotion

Every new intelligence model begins in `SHADOW` mode:

- calculate prediction/score
- log what it would have selected
- do not change live candidate selection
- compare predicted vs actual outcomes

Promotion requires predefined offline/shadow thresholds for:

- calibration
- net expected profit after tax
- fill-rate improvement
- drawdown/risk
- coverage across enough trades/items

Models are versioned. Each live trade records the market-model version, feature schema version and decision score, but no sensitive RuneLite/user text.

Rollback to the deterministic baseline is always available without changing the RuneLite V5 bridge.

## Explainability and diagnostics

For every planned candidate V990 should be able to emit a compact reason summary such as:

- expected margin after tax
- p10/p50/p90 return or spread forecast
- buy-fill probability
- expected fill time
- liquidity score
- remaining GE limit
- capital required
- risk penalty
- final opportunity score
- accept/reject reason code

This makes model mistakes diagnosable and prevents an opaque model from silently controlling capital.

## Testing additions

### Java

Add tests for:

- bridge instance/session identity
- login/hop settling
- current-input-mode mapping without serialized prompt text
- event-driven section freshness
- raw/canonical item IDs
- all-slot observation
- normalized offer event records
- duplicate/full-quantity-before-completion handling where Java owns normalization

### Python

Add tests for:

- bridge restart/session change -> `LOGIN_RESYNC`
- transient login empty events never create empty-slot permission
- canonical vs raw item identity rules
- GE-limit partial-fill accounting
- completion event de-duplication
- four-hour limit reset behaviour
- model unavailable -> deterministic baseline, never execution failure
- malformed model output -> reject model score, never loosen safety
- shadow/live model separation
- opportunity-score explanation fields

### Market-model validation

Require chronological/leakage-safe train/validation/test splits and realistic transaction costs.

Track at minimum:

- probabilistic calibration / quantile coverage
- pinball loss or equivalent forecast metric
- fill-probability calibration
- Brier/log loss for fill classification where used
- fill-time error / survival calibration where used
- realized net profit after tax
- capital-hours locked
- max adverse excursion / drawdown proxy
- baseline comparison

## Recommended implementation order

1. V5 session identity + event-driven authoritative state
2. semantic input modes + exact action/inventory geometry
3. raw/canonical item identity + normalized offer events
4. persistent exact GE-limit ledger
5. V990 deterministic transition engine and structured diagnostics
6. sanitized replay/shadow infrastructure
7. deterministic risk-adjusted opportunity score
8. collect Wiki time-series feature store
9. baseline probabilistic forecasting
10. optional LSTM quantile model if it beats baselines
11. own-account fill probability/time-to-fill model
12. online calibration/drift detection
13. optional contextual bandit only after extensive shadow validation

## Non-goals

- No LLM in the click/state-validation loop.
- No model may fabricate RuneLite state.
- No model may override a fail-closed state.
- No raw private text is used as a model feature.
- No automatic self-modifying production code.
- No reinforcement-learning agent is permitted to explore unsafe UI actions.
