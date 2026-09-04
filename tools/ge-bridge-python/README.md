# RuneLite GE State Bridge

This companion client reads the custom RuneLite `GE State Bridge` plugin at:

`http://127.0.0.1:17654/state`

## What it replaces

When the bridge is healthy, use RuneLite data instead of OCR/pixel reads for:

- GE slot state
- GREEN / ORANGE / RED / EMPTY compatibility state
- exact offer item ID, price and quantities
- inventory item IDs and quantities
- inventory GP
- completed/cancelled collect readiness

## Safety rule

A missing, stale, malformed, logged-out, or incompatible bridge response returns no trusted snapshot. The bot must treat that as `UNKNOWN/WAIT`, never as `EMPTY`.

## V981 integration

Import `V981RuneLiteStateAdapter` from `v981_bridge_adapter.py`, refresh it once per state loop, and use `physical_status(slot_index)` and `inventory_gp()` as the authoritative readers.

Keep existing OCR only as optional diagnostic output. Do not let OCR override a valid bridge snapshot.

The RuneLite plugin is read-only. It does not click, type, place, cancel, or collect offers.

## Local Python tests

From this directory:

```text
python -m unittest -v test_runelite_bridge.py
```

`requests` is already included in the existing V981 installer dependencies.

## RuneLite build

From the RuneLite repository root on Windows:

```text
gradlew.bat :client:test --tests net.runelite.client.plugins.gebridge.*
gradlew.bat :client:shadowJar
```

Start the custom RuneLite build and ensure the `GE State Bridge` plugin is enabled. A valid logged-in state should then be available on `/state`.
