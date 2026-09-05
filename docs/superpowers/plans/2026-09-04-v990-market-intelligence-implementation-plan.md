# V990 Market Intelligence and Shadow Learning Implementation Plan

> **For implementation:** this plan begins only after the V5 bridge and deterministic V990 execution engine have stable tests/replay. AI/model output may rank candidates but must never weaken protocol/session/GE/mouse/action safety policy.

**Goal:** Add a measurable, explainable market-intelligence layer that improves candidate ranking, quantity/capital allocation, and expected fill quality using public OSRS market data plus sanitized own-trade outcomes.

**Architecture:** Keep market intelligence behind an interface consumed by the deterministic planner. Begin with a local feature store and simple chronological baselines. Add probabilistic forecasting, fill/time-to-fill modeling, drift/update-shock detection, and optional online calibration only after shadow evaluation. Every model is versioned, explainable, and replaceable by the deterministic baseline without changing RuneLite or physical-input code.

**Source tree additions:**

```text
tools/ge_v990/
  ge_v990/
    market/
      __init__.py
      schema.py
      store.py
      wiki_client.py
      features.py
      baseline_score.py
      shadow.py
      evaluation.py
      forecasting.py
      calibration.py
      fill_model.py
      regime.py
      portfolio.py
      model_registry.py
  tests/
    market/
      ...
  data/
    .gitkeep
```

Generated databases, downloaded time series, model weights, and trade logs stay gitignored.

---

## Task 1: Define the market decision contract before adding any model

**Files:**
- Create: `ge_v990/market/schema.py`
- Create: `tests/market/test_schema.py`

### Step 1: RED tests

Define immutable structures for:

- `MarketCandidate`
- `MarketFeatures`
- `ForecastDistribution`
- `FillPrediction`
- `RiskBreakdown`
- `OpportunityScore`
- `ModelMetadata`

Require every scored candidate to expose numeric explanation fields:

- current high/low/mid/spread
- expected tax-adjusted margin
- capital required
- remaining known GE limit
- liquidity/volume metrics
- uncertainty/downside penalty
- buy/sell fill probability when available
- expected fill time when available
- regime penalty
- concentration penalty
- final score
- accept/reject reason
- model/schema versions

### Step 2: Run RED

```cmd
python -m pytest tools\ge_v990\tests\market\test_schema.py -q
```

### Step 3: Implement only schemas/enums

No model logic yet.

### Step 4: PASS + commit

`feat: define v990 market intelligence contract`

---

## Task 2: Add a local market/trade feature store

**Files:**
- Create: `ge_v990/market/store.py`
- Create: `tests/market/test_store.py`
- Update: `.gitignore` or `tools/ge_v990/.gitignore`

### Step 1: RED tests

Require persistence for:

- public Wiki price observations
- normalized item mapping/metadata
- feature snapshots
- sanitized own order outcomes
- model predictions and shadow decisions
- model/schema versions

Explicitly reject storage of:

- RuneLite chat/search/login/password text
- clipboard
- arbitrary keyboard chars
- unrelated desktop mouse activity

### Step 2: Choose minimal storage

Start with SQLite for the transactional/sanitized local store because it is stdlib and sufficient for initial scale. Add DuckDB later only if analytical scan performance becomes a measured bottleneck.

### Step 3: PASS + commit

`feat: add local v990 market feature store`

---

## Task 3: Build a resilient public OSRS Wiki price-data client

**Files:**
- Create: `ge_v990/market/wiki_client.py`
- Create: `tests/market/test_wiki_client.py`

### Step 1: RED tests using mocked HTTP

Cover:

- mapping endpoint parsing
- latest prices
- 5-minute data
- hourly data
- missing side/volume values
- HTTP timeout/rate-limit/error
- stale cache behavior
- user-agent/config support
- no market API failure can alter execution safety state

### Step 2: Implement with cache/retry bounds

Market API unavailable => model/candidate layer falls back or pauses new opportunity ranking according to policy; it never fabricates current prices.

### Step 3: PASS + commit

`feat: add cached osrs wiki market client`

---

## Task 4: Implement deterministic feature engineering

**Files:**
- Create: `ge_v990/market/features.py`
- Create: `tests/market/test_features.py`

### Step 1: RED tests for leakage-safe features

Compute from historical/current public data only:

- high/low/mid returns over several windows
- spread and spread percentage
- rolling spread z-score
- rolling volatility/realized variance
- high/low volumes at 5m/15m/1h/6h windows
- volume imbalance
- liquidity/missing-side flags
- range position/momentum
- VWAP deviation where data supports it
- cyclical hour/minute/weekday features
- GE tax-adjusted margin
- known remaining GE limit from V990 ledger
- capital required
- own historical fill/cancel metrics
- update/regime flags

Tests must ensure no future timestamp leaks into features.

### Step 2: PASS + commit

`feat: add leakage-safe ge market features`

---

## Task 5: Build the deterministic risk-adjusted baseline scorer

**Files:**
- Create: `ge_v990/market/baseline_score.py`
- Create: `tests/market/test_baseline_score.py`

### Step 1: RED tests

The baseline score should combine explicit weighted components such as:

- expected margin after tax
- ROI
- volume/liquidity
- volatility penalty
- capital-lock proxy
- remaining GE limit
- portfolio concentration
- data freshness

Hard constraints stay outside this score and remain enforced by `ge_policy.py`.

Require full explanation for accept/reject.

### Step 2: Implement transparent deterministic formula

This becomes the guaranteed fallback if every ML model is unavailable.

### Step 3: PASS + commit

`feat: add explainable baseline opportunity score`

---

## Task 6: Add shadow-decision infrastructure before training AI

**Files:**
- Create: `ge_v990/market/shadow.py`
- Create: `tests/market/test_shadow.py`

### Step 1: RED tests

Require:

- shadow model can score the same candidate set as baseline
- shadow result is persisted with timestamp/model/schema version
- shadow result cannot change the live chosen candidate
- live deterministic decision and shadow decision are independently recorded
- model exception/malformed output => log failure, continue deterministic baseline

### Step 2: Implement strict shadow-only adapter

### Step 3: PASS + commit

`feat: add v990 model shadow mode`

**Gate:** no AI model may become live until this task is complete.

---

## Task 7: Build chronological evaluation and realistic outcome metrics

**Files:**
- Create: `ge_v990/market/evaluation.py`
- Create: `tests/market/test_evaluation.py`

### Step 1: RED tests

Require chronological train/validation/test split utilities that account for the maximum future forecast horizon.

Metrics include:

- pinball loss/quantile coverage
- calibration error
- Brier/log loss for fill probability
- time-to-fill/survival metrics where applicable
- realized/shadow net profit after GE tax
- capital-hours locked
- fill rate
- cancel rate
- max adverse excursion/drawdown proxy
- baseline comparison

A model is never evaluated on a random split that leaks future market behavior into training.

### Step 2: PASS + commit

`test: add chronological market model evaluation`

---

## Task 8: Implement simple forecast baselines before neural networks

**Files:**
- Create: `ge_v990/market/forecasting.py`
- Create: `tests/market/test_forecasting.py`

### Step 1: RED tests

Implement/compare at least:

1. no-change/persistence
2. rolling empirical return quantiles
3. regularized linear model
4. tree/boosted model if dependency burden is acceptable

Outputs must conform to `ForecastDistribution` with horizons such as 5m, 10m, 15m, 30m, 1h, 2h, 3h and p10/p25/p50/p75/p90.

### Step 2: Implement baselines

Use only past/current features.

### Step 3: Evaluation gate

Record chronological validation/test metrics. These are the benchmarks every later model must beat.

### Step 4: Commit

`feat: add probabilistic forecast baselines`

---

## Task 9: Add conformal/empirical uncertainty calibration

**Files:**
- Create: `ge_v990/market/calibration.py`
- Create: `tests/market/test_calibration.py`
- Optional dependency: MAPIE only if it fits the selected baseline/model API cleanly

### Step 1: RED tests

Require calibration to:

- operate on a held-out conformal/calibration period
- improve or at least quantify empirical interval coverage
- refuse insufficient/non-exchangeable samples where assumptions are materially violated
- widen intervals rather than invent confidence when model error rises

### Step 2: Implement simplest reliable calibration first

Begin with empirical rolling calibration. Add MAPIE only when its adaptive/time-series methods demonstrably improve the workflow.

### Step 3: PASS + commit

`feat: calibrate v990 forecast uncertainty`

---

## Task 10: Prototype the multi-horizon LSTM quantile model in shadow only

**Files:**
- Create: `ge_v990/market/models/lstm_quantile.py`
- Create: `tests/market/test_lstm_quantile.py`
- Update model extras in `pyproject.toml`

### Step 1: RED unit tests

Cover:

- deterministic tensor shape
- separate high/low output heads
- multiple horizons/quantiles
- masked invalid targets
- no future-label leakage across split boundary
- CPU inference path
- versioned model metadata

### Step 2: Implement minimal model/training wrapper

Borrow the proven *pattern* from public OSRS quantile-forecast work: 5-minute series, price/spread/volume/liquidity/volatility/time features, multi-horizon quantile loss. Do not copy unreviewed code wholesale.

### Step 3: Benchmark gate

The LSTM remains SHADOW unless it materially beats the simple baselines on out-of-sample pinball loss/calibration **and** improves simulated/shadow net outcomes after tax/fill assumptions.

### Step 4: Commit

`feat: add shadow lstm quantile forecaster`

---

## Task 11: Add constrained hyperparameter tuning only after baseline model works

**Files:**
- Create: `ge_v990/market/tuning.py`
- Create: `tests/market/test_tuning.py`
- Optional dependency: Optuna

Use Optuna for a bounded search over already-validated model parameters; never optimize directly on the held-out final test period.

Objectives may be multi-metric/constraint aware:

- forecast loss
- calibration
- model size/inference latency
- shadow net profitability

Require early pruning and a strict trial budget to avoid endless tuning.

Commit:

`feat: add bounded market model tuning`

---

## Task 12: Build fill probability and time-to-fill dataset from own outcomes

**Files:**
- Create: `ge_v990/market/fill_model.py`
- Create: `tests/market/test_fill_model.py`

### Step 1: RED tests

Training rows derive only from sanitized order outcomes and market features at placement time.

Labels/features cover:

- buy filled within configured timeout
- time to partial/full fill
- sell fill horizons
- cancellation/censoring
- price distance from current high/low
- spread
- volume/imbalance
- volatility
- order quantity relative to volume
- time of day/week
- item/group statistics

Cancelled/still-open orders must be representable as censored rather than incorrectly assigning an arbitrary exact fill time.

### Step 2: Implement baseline models

Start with calibrated classification for fill-within-horizon and simple survival analysis where useful. `lifelines` or `scikit-survival` is optional after the data contract is proven.

### Step 3: PASS + commit

`feat: add shadow ge fill prediction`

---

## Task 13: Add online calibration and concept-drift detection

**Files:**
- Create: `ge_v990/market/online.py`
- Create: `tests/market/test_online.py`
- Optional dependency: River

### Step 1: RED tests

Require online learner to:

- predict before learning outcome
- learn only once outcome becomes known
- maintain rolling calibration/quality metrics
- detect drift/anomaly signals
- reset/de-weight online corrections without changing execution policy
- survive process restart via versioned state or reset cleanly

### Step 2: Implement lightweight corrections

Good initial uses:

- fill-probability calibration
- expected slippage correction
- per-item/cluster fill-time correction
- drift warning

Do not online-train the RuneLite state or mouse policy.

### Step 3: PASS + commit

`feat: add online market calibration and drift detection`

---

## Task 14: Add game-update / market-shock regime detection

**Files:**
- Create: `ge_v990/market/regime.py`
- Create: `tests/market/test_regime.py`

### Step 1: RED tests

Detect `NORMAL`, `VOLATILE`, `UPDATE_SHOCK`, `DATA_UNCERTAIN` from:

- optional known update timestamps
- cross-sectional return/volume spikes
- item return/volume z-scores
- spread expansion
- missing/stale data

During shock/uncertain regimes, the market planner may:

- raise confidence threshold
- lower new-position capital cap
- exclude directly affected/unreliable items

It may never loosen RuneLite execution checks.

### Step 2: PASS + commit

`feat: add ge market regime guard`

---

## Task 15: Add portfolio/concentration-aware slot allocation

**Files:**
- Create: `ge_v990/market/portfolio.py`
- Create: `tests/market/test_portfolio.py`

### Step 1: RED tests

Given up to 3 actionable slots, choose among already-safe candidates while respecting:

- exact available GP
- per-order 20m buy ceiling
- remaining GE limits
- max per-item capital
- item/category concentration
- correlated recent price movement where measurable
- liquidity/fill confidence
- existing sell-first obligations take priority

Output remains an explainable allocation plan; execution state machine still rechecks every action.

### Step 2: PASS + commit

`feat: add three-slot risk-aware portfolio allocator`

---

## Task 16: Add model registry/versioning and reproducibility

**Files:**
- Create: `ge_v990/market/model_registry.py`
- Create: `tests/market/test_model_registry.py`

Persist for every model:

- semantic model name/version
- git commit
- feature schema version
- training period
- validation/test period
- metrics
- hyperparameters
- artifact checksum
- promotion state: `EXPERIMENT`, `SHADOW`, `LIVE`, `RETIRED`

Start with a simple local registry. MLflow is optional later if local metadata becomes cumbersome; do not require an MLflow server for V990 operation.

Commit:

`feat: version v990 market models`

---

## Task 17: Define hard promotion/rollback gates

**Files:**
- Create: `ge_v990/market/promotion.py`
- Create: `tests/market/test_promotion.py`

### RED tests

A model cannot move from SHADOW to LIVE unless configured thresholds are met for enough independent observations, including:

- better than deterministic baseline after tax
- acceptable calibration
- adequate fill prediction quality if used
- no unacceptable drawdown/capital-lock regression
- enough trades/items/time regimes
- no unresolved drift alert

Model unavailable/malformed => immediate deterministic baseline fallback.

Rollback is a config/state change in the market layer only; RuneLite/V990 execution stays unchanged.

Commit:

`feat: add guarded market model promotion`

---

## Task 18: Optional contextual bandit experiment — SHADOW ONLY initially

**Files:**
- Create only after all previous gates are stable: `ge_v990/market/bandit.py`
- Create: `tests/market/test_bandit.py`

The bandit may choose/rank among **already safe, already policy-compliant candidate/price strategies**. It may not:

- bypass F2P/slot/capital/GE-limit rules
- bypass RuneLite/mouse/session checks
- issue physical input
- explore arbitrary UI actions
- modify sell no-timeout/no-reprice or one-abort constraints

Use SHADOW for an extended period before considering live ranking.

Commit only if measured value exists:

`experiment: add shadow contextual strategy bandit`

---

## Task 19: Add monitoring/drift reports without production coupling

**Files:**
- Create: `ge_v990/market/reporting.py`
- Create: `tests/market/test_reporting.py`

Generate local reports for:

- prediction calibration
- feature drift
- candidate acceptance/rejection reasons
- profit after tax
- fill/cancel rates
- capital-hours locked
- per-item/model performance
- update-shock periods

Evidently may be evaluated as an optional reporting dependency, but V990 must not fail to trade safely merely because a reporting library is unavailable.

Commit:

`feat: add v990 market quality reporting`

---

## Task 20: Integrate market planner into V990 behind a strict adapter

**Files:**
- Create/modify: `ge_v990/market/__init__.py`
- Modify: `ge_v990/main.py`
- Create: `tests/market/test_execution_integration.py`

### Step 1: RED integration tests

Prove:

- baseline planner produces candidates, execution revalidates them independently
- shadow model disagreement cannot alter live action
- live promoted model may change candidate ranking only
- market model cannot create permission when `ge_policy` denies action
- market model exception => deterministic baseline
- no market model can call `physical_input` or `mouse_transaction`
- sell-first obligations still outrank new speculative buys

### Step 2: Implement narrow interface

Execution receives only a proposed plan/candidate with explanation/version. It performs fresh V5 policy/state/mouse checks from scratch.

### Step 3: PASS + commit

`feat: integrate guarded market ranking with v990`

---

# Market Intelligence Completion Gate

Before any model is allowed to affect live candidate ranking:

1. Deterministic baseline and shadow infrastructure are stable.
2. Chronological train/validation/test evaluation is implemented.
3. Model beats baseline on predefined out-of-sample metrics.
4. Prediction intervals/fill probabilities are calibrated sufficiently for their intended use.
5. Shadow sample size covers enough trades/items/regimes.
6. Net performance includes GE tax and realistic fill/cancel/capital-lock effects.
7. Every decision remains numerically explainable.
8. Model/version/schema are recorded with each decision.
9. Deterministic fallback is tested.
10. Execution safety tests prove market output cannot bypass V5/V990 policy.

# Recommended Dependency Discipline

Adopt dependencies only after the task that needs them proves value:

- **SQLite:** initial local storage, stdlib.
- **scikit-learn:** simple baseline models if needed.
- **PyTorch:** only for the LSTM shadow experiment after baselines.
- **MAPIE:** optional uncertainty calibration if it improves empirical coverage.
- **Optuna:** optional bounded tuning after a valid model exists.
- **lifelines/scikit-survival:** optional censored time-to-fill modeling.
- **River:** optional online calibration/drift after enough outcomes exist.
- **DuckDB:** optional analytics scale-up if SQLite scan performance is measured as limiting.
- **MLflow/Evidently:** optional model/reporting operations, never runtime safety dependencies.

# Rollback Strategy

- Every model starts SHADOW.
- Live market ranking can be disabled independently of V990 execution.
- Deterministic baseline remains permanently available.
- Model errors degrade ranking intelligence, never execution safety.
- No ML dependency is imported by the RuneLite bridge or mouse transaction layer.
