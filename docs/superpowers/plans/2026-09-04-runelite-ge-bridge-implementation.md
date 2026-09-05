# RuneLite GE State Bridge Implementation Plan

1. Add Java tests for GE state mapping, collect readiness, inventory aggregation, slot DTOs, protocol serialization, and loopback HTTP behavior.
2. Implement immutable bridge DTOs and a snapshot builder from RuneLite `GrandExchangeOffer[]` and inventory items.
3. Implement a loopback-only `GET /state` HTTP server on `127.0.0.1:17654`.
4. Implement `GeBridgePlugin` using RuneLite events/client-thread reads and a cached immutable snapshot; no game actions.
5. Add Python tests for valid parsing, stale/logged-out/protocol failures, disconnect behavior, slot lookup, and GP/inventory parsing.
6. Implement `RuneLiteBridgeClient` and a small V981 integration adapter that fails closed to `UNKNOWN/WAIT` when bridge data is invalid.
7. Add setup documentation and a bridge smoke-check script.
8. Open/update a draft PR, run GitHub Actions/available CI checks, inspect failures, fix, and only then mark ready for review.