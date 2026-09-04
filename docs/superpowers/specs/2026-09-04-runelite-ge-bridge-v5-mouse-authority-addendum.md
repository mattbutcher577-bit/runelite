# RuneLite GE Bridge V5 / V990 Mouse Authority Addendum

## Status

This addendum extends the approved V5 authoritative-execution design and the market-intelligence/reliability addendum.

The core architecture remains unchanged:

**RuneLite = authoritative read-only eyes/state/geometry. Python = decisions + physical mouse/keyboard input only.**

Java must never move the cursor, synthesize a click, consume gameplay input for automation, type text, invoke a menu action, or mutate game state.

## Goal

Make every V990 mouse action provable end-to-end:

1. Python chooses a target from fresh RuneLite bounds.
2. Python moves the physical OS cursor.
3. RuneLite observes the cursor inside the intended target.
4. RuneLite observes the expected press.
5. RuneLite observes the expected release.
6. RuneLite then reports the expected GE semantic state transition.

An action is not accepted merely because PyAutoGUI returned successfully.

## RuneLite sources

Use RuneLite-native read-only sources wherever possible:

- `Client.getMouseCanvasPosition()` for current game-canvas mouse position.
- `Client.getMouseCurrentButton()` for the currently pressed mouse button.
- `Client.getMouseIdleTicks()` for client-side mouse idle time.
- `Client.getMouseLastPressedMillis()` for last press timing.
- RuneLite `MouseManager` observation of move, drag, press, release, click, enter, exit, and wheel events.
- `Client.getRealDimensions()` and `Client.getStretchedDimensions()` for stretched-mode translation.
- RuneLite UI focus state for foreground/focus gating.

No desktop-wide global mouse hook is required for the authoritative path.

## Protocol 5 mouse state

Add a top-level `mouse` object.

Required current-state fields:

- `updatedTick`
- `updatedSeq`
- `eventSeq`
- `canvasX`
- `canvasY`
- `insideCanvas`
- `currentButton`
- `dragging`
- `mouseIdleTicks`
- `lastPressMillis`
- `lastMoveMillis`
- `lastReleaseMillis`
- `lastWheelMillis`
- `lastWheelRotation`
- `lastEventType`
- `lastEventButton`
- `lastEventCanvasX`
- `lastEventCanvasY`
- `canvasFocused`
- `clientWindowFocused`

`lastEventType` is restricted to a fixed enum such as:

- `NONE`
- `MOVE`
- `DRAG`
- `PRESS`
- `RELEASE`
- `CLICK`
- `ENTER`
- `EXIT`
- `WHEEL`

No arbitrary input text or unrelated desktop input is serialized.

## Rolling mouse-event diagnostics

Optionally publish a bounded rolling buffer of recent RuneLite-canvas mouse events, for example the last 32 events.

Each event contains only:

- `eventSeq`
- `type`
- `canvasX`
- `canvasY`
- `button`
- `wheelRotation`
- `whenMillis`
- `consumed`

The buffer must never include:

- keyboard characters
- chat/search/login text
- clipboard data
- coordinates from other applications
- window titles from unrelated applications

The buffer is diagnostic and transition-verification data only.

## Coordinate systems

V990 maintains four explicitly named coordinate spaces:

1. `RUNELITE_CANVAS`
2. `RUNELITE_REAL`
3. `RUNELITE_STRETCHED`
4. `OS_SCREEN`

No helper may accept an untyped/ambiguous point.

### Stretched-mode transform

When stretched mode is enabled, use RuneLite's real/stretched dimensions to mirror RuneLite's own translation model conceptually:

- `canvasX = stretchedX / (stretchedWidth / realWidth)`
- `canvasY = stretchedY / (stretchedHeight / realHeight)`

The inverse transform is used when converting authoritative RuneLite bounds to OS-screen targets.

Any invalid, zero, missing, or contradictory dimension fails closed.

## Screen origin and DPI

Protocol 5 must continue publishing exact canvas screen origin and add enough geometry metadata to detect scale changes.

Recommended fields:

- `canvasScreenX`
- `canvasScreenY`
- `canvasScreenPositionValid`
- `realWidth`
- `realHeight`
- `stretchedWidth`
- `stretchedHeight`
- `stretchedEnabled`
- optional monitor/display identifier when reliably available
- optional graphics scale X/Y when reliably available

V990 never assumes `100%` Windows scaling and never assumes RuneLite remains on the same display.

If a monitor/DPI/window move changes the transform, V990 invalidates cached click targets and rebuilds geometry from a new snapshot.

## Passive startup geometry calibration

Before the first live GE action, V990 may perform a passive calibration with no click:

1. Read Python's OS cursor position.
2. Read RuneLite's current canvas cursor position.
3. Convert the RuneLite canvas point to expected OS-screen position.
4. Compare observed and expected screen coordinates.

If the distance exceeds the configured tolerance, set:

`MOUSE_COORDINATE_DESYNC`

and block all physical GE actions.

Calibration may be repeated after window movement, focus loss/regain, stretched-mode changes, display changes, or bridge restart.

## Focus and visibility safety

A new GE physical action requires all of the following:

- RuneLite client window focused.
- RuneLite canvas focused where applicable.
- canvas showing/valid.
- expected RuneLite bridge instance/session fresh.
- cursor target maps inside the active canvas.
- no blocker/manual hold.

If focus changes between target selection and mouse press, abandon the action before pressing.

Focus state never substitutes for GE semantic state; it is an additional gate.

## Safe interior target rectangles

Python does not click arbitrary widget borders.

For every RuneLite widget bound, V990 derives an interior target rectangle by applying a bounded inset that is small relative to widget dimensions.

Rules:

- Never shrink below a configured minimum clickable area.
- Never fabricate an interior region when the source bound is invalid.
- Prefer the geometric centre of the valid interior only when no stronger semantic sub-bound exists.
- Search-result `nameBounds`/`iconBounds` may define the target directly when validated.

This reduces edge misses caused by borders, clipping, one-pixel layout changes, and scaling rounding.

## Mouse action transaction

Every physical mouse action is represented by an immutable `MouseActionIntent` containing:

- `actionId`
- `phase`
- `targetKind`
- `targetBoundsCanvas`
- `targetPointCanvas`
- `targetPointScreen`
- `button`
- `preActionMouseEventSeq`
- `preActionSnapshotSeq`
- `preActionClientTick`
- expected semantic RuneLite transition

### Move proof

After Python moves the cursor, V990 requires a fresh RuneLite mouse observation whose canvas point lies inside the intended target region.

If not observed before timeout:

`MOUSE_TARGET_MISS`

or

`MOUSE_EVENT_STALE`

No button press is attempted after a failed move proof.

### Press proof

After Python physically presses the intended button, V990 requires a newer RuneLite mouse event showing the expected button press inside the intended target region.

Failure:

`MOUSE_PRESS_NOT_OBSERVED`

### Release proof

After Python physically releases the button, V990 requires a newer RuneLite release event for the same button.

Failure:

`MOUSE_RELEASE_NOT_OBSERVED`

### Semantic proof

Only after move + press + release proof does V990 wait for the GE state transition required by the parent action phase.

Examples:

- slot buy button -> setup open / item-search semantic mode
- search result -> selected setup item ID matches
- quantity button -> `QUANTITY`
- price button -> `PRICE`
- confirm -> slot becomes exact `BUYING/BOUGHT` or `SELLING/SOLD`
- abort -> exact cancel/completion transition
- collect -> exact slot/inventory delta

Mouse proof cannot override a failed semantic transition.

## Duplicate-click protection

Each live physical action gets one `actionId`.

Once RuneLite has observed the expected press/release for that `actionId`, V990 must not issue a second click merely because the semantic transition is slow.

The system waits for the transition or fails with the relevant state-transition reason code.

A retry requires a newly validated phase boundary and a new action ID.

## Stuck-button protection

If RuneLite reports a mouse button remaining held outside an expected short press window:

1. stop all new physical input;
2. enter `MOUSE_BUTTON_HOLD`/manual-hold state;
3. wait until RuneLite reports no mouse button pressed;
4. fetch a fresh protocol-5 snapshot;
5. resynchronise the V990 state machine from RuneLite state.

Do not blindly issue extra clicks to 'unstick' the UI.

Failure code:

`MOUSE_BUTTON_STUCK`

## Manual mouse interference

V990 tracks expected automation windows around its own move/press/release actions.

Unexpected RuneLite mouse movement/press/wheel activity outside those windows causes `MANUAL_HOLD`.

Rules:

- Do not attempt to identify the human by biometric mouse behaviour.
- Do not classify arbitrary cursor paths as 'bot' vs 'human'.
- Do not capture mouse activity outside RuneLite.
- Resume only after buttons are released, configured idle time has elapsed, and a fresh RuneLite state-machine boundary is identified.

## OS cursor vs RuneLite cursor cross-check

Python compares its OS-screen cursor coordinates against RuneLite's observed canvas coordinates transformed to screen space.

Use a small configurable tolerance that accounts for integer rounding and scaling.

Large or persistent disagreement blocks input with:

`MOUSE_COORDINATE_DESYNC`

The tolerance must not silently grow to mask bad geometry.

## Wheel verification

If V990 ever needs scrolling inside a GE/search/inventory region:

1. require cursor inside the intended scroll region;
2. record pre-wheel `mouse.eventSeq` and relevant UI scroll state where available;
3. issue physical wheel input;
4. require RuneLite wheel observation with matching sign/rotation;
5. require the relevant UI state/result region to update before another scroll.

Never repeatedly spin the wheel without observed progress.

## Mouse quality metrics

Sanitized diagnostics may record per action:

- target kind
- target width/height
- intended canvas point
- RuneLite-observed point at press
- pixel miss distance
- move-to-observed latency
- press-to-observed latency
- press-to-release duration
- release-to-semantic-transition latency
- success/failure reason code

These metrics may be aggregated by control type to identify systematic geometry or latency problems.

They must not become a behavioural fingerprint of the user.

## Mouse-related reason codes

Add at minimum:

- `MOUSE_STATE_UNAVAILABLE`
- `MOUSE_EVENT_STALE`
- `MOUSE_OUTSIDE_CANVAS`
- `MOUSE_COORDINATE_DESYNC`
- `WINDOW_NOT_ACTIVE`
- `CANVAS_NOT_FOCUSED`
- `MOUSE_TARGET_BOUNDS_INVALID`
- `MOUSE_TARGET_MISS`
- `MOUSE_PRESS_NOT_OBSERVED`
- `MOUSE_RELEASE_NOT_OBSERVED`
- `MOUSE_BUTTON_STUCK`
- `MOUSE_DUPLICATE_ACTION_BLOCKED`
- `MOUSE_WHEEL_NOT_OBSERVED`
- `MOUSE_SCROLL_NO_PROGRESS`
- `MANUAL_MOUSE_HOLD`

Every failure log includes the action phase, action ID, target kind, relevant RuneLite tick/sequence, and privacy-safe geometry summary.

## Java implementation units

Add focused read-only components rather than expanding `GeBridgePlugin` into a monolith:

- `GeBridgeMouseState`
- `GeBridgeMouseEvent`
- `GeBridgeMouseTracker`
- optional `GeBridgeFocusState` if focus data is not cleanly owned by client state

`GeBridgeMouseTracker` registers with RuneLite `MouseManager` and only observes/copies event metadata. It never consumes or modifies gameplay mouse events for automation purposes.

The tracker maintains the bounded event ring and sequence counter.

## Python implementation units

V990 gains focused mouse/geometry support:

- `mouse_models.py`
- `mouse_policy.py`
- `coordinate_spaces.py`
- `mouse_transaction.py`
- `mouse_diagnostics.py`

The live GE state machine calls the mouse transaction layer for physical actions rather than calling PyAutoGUI clicks directly.

## Java tests

Add tests for:

- protocol serialization of mouse current state
- no arbitrary keyboard/text fields
- event sequence monotonicity
- move/press/release/wheel event capture
- bounded rolling event buffer
- no Java mouse-event mutation/automation
- idle-tick and last-press fields
- focus-state publication
- stretched/real/canvas geometry consistency
- bridge restart resets instance/event sequences appropriately

## Python tests

Add tests for:

- canvas <-> screen conversion
- stretched-mode conversion
- rounding tolerance
- multi-monitor/origin changes invalidate cached geometry
- passive startup calibration success/failure
- focus lost before press -> no click
- target miss -> no press
- press not observed -> fail closed
- release not observed -> fail closed
- semantic transition slow -> no duplicate click
- stuck button -> hold/resync
- unexpected human mouse movement -> manual hold
- wheel observed but no UI progress -> stop scrolling
- bridge restart during mouse transaction -> action abandoned
- stale mouse event sequence rejected

## Replay fixtures

Sanitized replay fixtures should include:

- perfect buy-button click
- cursor reaches target but press is never observed
- press lands outside target
- press observed but release missing
- release observed but GE setup never opens
- stretched-mode coordinate mismatch
- RuneLite moved to another monitor between planning and press
- focus loss while moving
- user moves mouse during search flow
- user presses while automation is waiting
- duplicate semantic-delay scenario
- stuck-left-button scenario
- wheel event with no search-list progress

## Privacy

Mouse authority remains intentionally narrow.

Allowed:

- RuneLite canvas coordinates
- RuneLite-local mouse event type/button/timing
- bounded RuneLite-local diagnostic path
- focus/geometry metadata needed for safety

Forbidden:

- desktop-wide mouse surveillance
- unrelated application coordinates/activity
- behavioural biometric profiling
- keyboard/chat/search/login/clipboard content
- screenshots as authoritative mouse proof

## Non-goals

- No Java-generated clicks.
- No Java cursor movement.
- No global OS hook unless a future design proves a specific missing capability and receives separate approval.
- No mouse-behaviour anti-detection/humanisation model.
- No AI model may override mouse/focus/geometry fail-closed checks.

## Resulting action proof chain

The V990 production rule becomes:

**fresh RuneLite target -> valid coordinate transform -> focused RuneLite -> physical cursor move -> RuneLite target observation -> RuneLite press observation -> RuneLite release observation -> exact GE semantic transition -> action accepted.**

Anything less is not proof of a successful action.
