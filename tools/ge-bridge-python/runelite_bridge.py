from __future__ import annotations

from dataclasses import dataclass
import time
from typing import Dict, Optional, Tuple

import requests


@dataclass(frozen=True)
class BridgeBounds:
    x: int
    y: int
    width: int
    height: int
    valid: bool


@dataclass(frozen=True)
class BridgeClientState:
    logged_in: bool
    world: int
    world_types: Tuple[str, ...]
    members_world: bool
    canvas_width: int
    canvas_height: int
    viewport_width: int
    viewport_height: int
    viewport_x_offset: int
    viewport_y_offset: int
    top_level_interface_id: int
    fps: int


@dataclass(frozen=True)
class BridgePlayerState:
    present: bool
    world_x: int
    world_y: int
    plane: int


@dataclass(frozen=True)
class BridgeInterfaceState:
    grand_exchange_open: bool
    grand_exchange_offer_setup_open: bool
    bank_open: bool
    world_map_open: bool
    dialog_open: bool
    chatbox_input_open: bool
    dragging_widget: bool


@dataclass(frozen=True)
class BridgeGeState:
    open: bool
    offer_setup_open: bool
    offer_setup_item_id: int
    window_bounds: BridgeBounds
    offer_setup_bounds: BridgeBounds
    inventory_bounds: BridgeBounds


@dataclass(frozen=True)
class BridgeInventoryState:
    capacity: int
    occupied_slots: int
    free_slots: int


@dataclass(frozen=True)
class BridgeSafetyState:
    bridge_ready: bool
    modal_blocker: bool
    safe_for_mouse_actions: bool
    safe_for_ge_mouse_actions: bool


@dataclass(frozen=True)
class BridgeInputState:
    last_input_epoch_ms: int
    last_mouse_move_epoch_ms: int
    last_mouse_click_epoch_ms: int
    last_mouse_press_epoch_ms: int
    last_mouse_release_epoch_ms: int
    last_mouse_wheel_epoch_ms: int
    last_keyboard_epoch_ms: int
    mouse_x: int
    mouse_y: int
    mouse_inside_canvas: bool
    mouse_buttons_down_mask: int
    last_mouse_button: int
    last_wheel_rotation: int
    last_control_key: str
    input_idle_ms: int


@dataclass(frozen=True)
class BridgeSlot:
    slot: int
    item_id: int
    state: str
    visual: str
    price: int
    total_quantity: int
    quantity_traded: int
    spent: int
    collect_ready: bool


@dataclass(frozen=True)
class BridgeSnapshot:
    generated_at_epoch_ms: int
    tick: int
    game_state: str
    slots: Tuple[BridgeSlot, ...]
    inventory: Dict[int, int]
    inventory_gp: int
    client: BridgeClientState
    player: BridgePlayerState
    interfaces: BridgeInterfaceState
    ge: BridgeGeState
    inventory_state: BridgeInventoryState
    safety: BridgeSafetyState
    input: BridgeInputState


class RuneLiteBridgeClient:
    PROTOCOL = 3
    SAFE_CONTROL_KEYS = {
        "",
        "SHIFT",
        "CTRL",
        "ALT",
        "ESCAPE",
        "ENTER",
        "F8",
        "TAB",
        "BACKSPACE",
        "DELETE",
        "LEFT",
        "RIGHT",
        "UP",
        "DOWN",
    }

    def __init__(
        self,
        base_url: str = "http://127.0.0.1:17654",
        timeout_seconds: float = 0.35,
        max_age_seconds: float = 2.0,
        session: Optional[requests.Session] = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = float(timeout_seconds)
        self.max_age_seconds = float(max_age_seconds)
        self.session = session or requests.Session()

    def read_state(self) -> Optional[BridgeSnapshot]:
        try:
            response = self.session.get(
                f"{self.base_url}/state",
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            payload = response.json()
        except (requests.RequestException, ValueError, TypeError):
            return None

        if not isinstance(payload, dict):
            return None
        if payload.get("protocol") != self.PROTOCOL:
            return None
        if payload.get("gameState") != "LOGGED_IN":
            return None

        generated_at = payload.get("generatedAtEpochMs")
        if not isinstance(generated_at, int) or generated_at <= 0:
            return None

        age_seconds = max(0.0, (time.time() * 1000 - generated_at) / 1000.0)
        if age_seconds > self.max_age_seconds:
            return None

        raw_slots = payload.get("slots")
        raw_inventory = payload.get("inventory")
        raw_client = payload.get("client")
        raw_player = payload.get("player")
        raw_interfaces = payload.get("interfaces")
        raw_ge = payload.get("ge")
        raw_inventory_state = payload.get("inventoryState")
        raw_safety = payload.get("safety")
        raw_input = payload.get("input")
        if not isinstance(raw_slots, list) or not isinstance(raw_inventory, list):
            return None
        if not all(
            isinstance(section, dict)
            for section in (
                raw_client,
                raw_player,
                raw_interfaces,
                raw_ge,
                raw_inventory_state,
                raw_safety,
                raw_input,
            )
        ):
            return None

        try:
            slots = tuple(
                BridgeSlot(
                    slot=int(row["slot"]),
                    item_id=int(row["itemId"]),
                    state=str(row["state"]),
                    visual=str(row["visual"]),
                    price=int(row["price"]),
                    total_quantity=int(row["totalQuantity"]),
                    quantity_traded=int(row["quantityTraded"]),
                    spent=int(row["spent"]),
                    collect_ready=bool(row["collectReady"]),
                )
                for row in raw_slots
                if isinstance(row, dict)
            )

            inventory: Dict[int, int] = {}
            for row in raw_inventory:
                if not isinstance(row, dict):
                    continue
                item_id = int(row["itemId"])
                quantity = int(row["quantity"])
                if item_id < 0 or quantity <= 0:
                    continue
                inventory[item_id] = inventory.get(item_id, 0) + quantity

            client = BridgeClientState(
                logged_in=bool(raw_client["loggedIn"]),
                world=int(raw_client["world"]),
                world_types=tuple(str(value) for value in raw_client["worldTypes"]),
                members_world=bool(raw_client["membersWorld"]),
                canvas_width=int(raw_client["canvasWidth"]),
                canvas_height=int(raw_client["canvasHeight"]),
                viewport_width=int(raw_client["viewportWidth"]),
                viewport_height=int(raw_client["viewportHeight"]),
                viewport_x_offset=int(raw_client["viewportXOffset"]),
                viewport_y_offset=int(raw_client["viewportYOffset"]),
                top_level_interface_id=int(raw_client["topLevelInterfaceId"]),
                fps=int(raw_client["fps"]),
            )
            player = BridgePlayerState(
                present=bool(raw_player["present"]),
                world_x=int(raw_player["worldX"]),
                world_y=int(raw_player["worldY"]),
                plane=int(raw_player["plane"]),
            )
            interfaces = BridgeInterfaceState(
                grand_exchange_open=bool(raw_interfaces["grandExchangeOpen"]),
                grand_exchange_offer_setup_open=bool(raw_interfaces["grandExchangeOfferSetupOpen"]),
                bank_open=bool(raw_interfaces["bankOpen"]),
                world_map_open=bool(raw_interfaces["worldMapOpen"]),
                dialog_open=bool(raw_interfaces["dialogOpen"]),
                chatbox_input_open=bool(raw_interfaces["chatboxInputOpen"]),
                dragging_widget=bool(raw_interfaces["draggingWidget"]),
            )
            ge = BridgeGeState(
                open=bool(raw_ge["open"]),
                offer_setup_open=bool(raw_ge["offerSetupOpen"]),
                offer_setup_item_id=int(raw_ge["offerSetupItemId"]),
                window_bounds=self._parse_bounds(raw_ge["windowBounds"]),
                offer_setup_bounds=self._parse_bounds(raw_ge["offerSetupBounds"]),
                inventory_bounds=self._parse_bounds(raw_ge["inventoryBounds"]),
            )
            inventory_state = BridgeInventoryState(
                capacity=int(raw_inventory_state["capacity"]),
                occupied_slots=int(raw_inventory_state["occupiedSlots"]),
                free_slots=int(raw_inventory_state["freeSlots"]),
            )
            safety = BridgeSafetyState(
                bridge_ready=bool(raw_safety["bridgeReady"]),
                modal_blocker=bool(raw_safety["modalBlocker"]),
                safe_for_mouse_actions=bool(raw_safety["safeForMouseActions"]),
                safe_for_ge_mouse_actions=bool(raw_safety["safeForGeMouseActions"]),
            )
            last_control_key = str(raw_input.get("lastControlKey", "")).upper()
            if last_control_key not in self.SAFE_CONTROL_KEYS:
                last_control_key = ""
            input_state = BridgeInputState(
                last_input_epoch_ms=int(raw_input["lastInputEpochMs"]),
                last_mouse_move_epoch_ms=int(raw_input["lastMouseMoveEpochMs"]),
                last_mouse_click_epoch_ms=int(raw_input["lastMouseClickEpochMs"]),
                last_mouse_press_epoch_ms=int(raw_input["lastMousePressEpochMs"]),
                last_mouse_release_epoch_ms=int(raw_input["lastMouseReleaseEpochMs"]),
                last_mouse_wheel_epoch_ms=int(raw_input["lastMouseWheelEpochMs"]),
                last_keyboard_epoch_ms=int(raw_input["lastKeyboardEpochMs"]),
                mouse_x=int(raw_input["mouseX"]),
                mouse_y=int(raw_input["mouseY"]),
                mouse_inside_canvas=bool(raw_input["mouseInsideCanvas"]),
                mouse_buttons_down_mask=int(raw_input["mouseButtonsDownMask"]),
                last_mouse_button=int(raw_input["lastMouseButton"]),
                last_wheel_rotation=int(raw_input["lastWheelRotation"]),
                last_control_key=last_control_key,
                input_idle_ms=int(raw_input["inputIdleMs"]),
            )
            tick = int(payload["tick"])
            inventory_gp = int(payload.get("inventoryGp", inventory.get(995, 0)))
        except (KeyError, TypeError, ValueError):
            return None

        if not client.logged_in or not player.present or not safety.bridge_ready:
            return None
        if client.canvas_width <= 0 or client.canvas_height <= 0:
            return None
        if inventory_state.capacity <= 0 or inventory_state.free_slots < 0:
            return None
        if input_state.mouse_buttons_down_mask < 0 or input_state.input_idle_ms < -1:
            return None

        return BridgeSnapshot(
            generated_at_epoch_ms=generated_at,
            tick=tick,
            game_state="LOGGED_IN",
            slots=slots,
            inventory=inventory,
            inventory_gp=inventory_gp,
            client=client,
            player=player,
            interfaces=interfaces,
            ge=ge,
            inventory_state=inventory_state,
            safety=safety,
            input=input_state,
        )

    @staticmethod
    def _parse_bounds(raw: object) -> BridgeBounds:
        if not isinstance(raw, dict):
            raise TypeError("bounds must be an object")
        return BridgeBounds(
            x=int(raw["x"]),
            y=int(raw["y"]),
            width=int(raw["width"]),
            height=int(raw["height"]),
            valid=bool(raw["valid"]),
        )

    @staticmethod
    def slot_visual(snapshot: Optional[BridgeSnapshot], slot_index: int) -> str:
        if snapshot is None:
            return "UNKNOWN"
        for slot in snapshot.slots:
            if slot.slot == int(slot_index):
                return slot.visual if slot.visual in {"GREEN", "ORANGE", "RED", "EMPTY"} else "UNKNOWN"
        return "UNKNOWN"

    @staticmethod
    def slot_state(snapshot: Optional[BridgeSnapshot], slot_index: int) -> str:
        if snapshot is None:
            return "UNKNOWN"
        for slot in snapshot.slots:
            if slot.slot == int(slot_index):
                return slot.state
        return "UNKNOWN"

    @staticmethod
    def safe_for_mouse_actions(snapshot: Optional[BridgeSnapshot]) -> bool:
        return bool(snapshot is not None and snapshot.safety.safe_for_mouse_actions)

    @staticmethod
    def safe_for_ge_mouse_actions(snapshot: Optional[BridgeSnapshot]) -> bool:
        return bool(snapshot is not None and snapshot.safety.safe_for_ge_mouse_actions)

    @staticmethod
    def modal_blocked(snapshot: Optional[BridgeSnapshot]) -> bool:
        return bool(snapshot is None or snapshot.safety.modal_blocker)

    @staticmethod
    def canvas_size(snapshot: Optional[BridgeSnapshot]) -> Optional[Tuple[int, int]]:
        if snapshot is None:
            return None
        if snapshot.client.canvas_width <= 0 or snapshot.client.canvas_height <= 0:
            return None
        return snapshot.client.canvas_width, snapshot.client.canvas_height

    @staticmethod
    def ge_window_bounds(snapshot: Optional[BridgeSnapshot]) -> Optional[BridgeBounds]:
        if snapshot is None or not snapshot.ge.window_bounds.valid:
            return None
        return snapshot.ge.window_bounds

    @staticmethod
    def mouse_position(snapshot: Optional[BridgeSnapshot]) -> Optional[Tuple[int, int]]:
        if snapshot is None:
            return None
        return snapshot.input.mouse_x, snapshot.input.mouse_y

    @staticmethod
    def input_idle_ms(snapshot: Optional[BridgeSnapshot]) -> Optional[int]:
        if snapshot is None:
            return None
        return snapshot.input.input_idle_ms

    @staticmethod
    def recent_input(
        snapshot: Optional[BridgeSnapshot],
        window_ms: int,
        now_epoch_ms: Optional[int] = None,
    ) -> bool:
        if snapshot is None or snapshot.input.last_input_epoch_ms <= 0:
            return False
        now = int(time.time() * 1000) if now_epoch_ms is None else int(now_epoch_ms)
        age = max(0, now - snapshot.input.last_input_epoch_ms)
        return age <= max(0, int(window_ms))
