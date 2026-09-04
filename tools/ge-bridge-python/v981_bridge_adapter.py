from __future__ import annotations

from typing import Optional, Tuple

from runelite_bridge import BridgeBounds, BridgeSnapshot, RuneLiteBridgeClient


class V981RuneLiteStateAdapter:
    """Read-only compatibility layer for V981-style RuneLite state reads.

    This module deliberately does not click, type, place offers, abort offers,
    collect offers, or change the existing F8 emergency stop. It only exposes
    validated protocol-v3 RuneLite bridge state to Python.
    """

    def __init__(self, client: Optional[RuneLiteBridgeClient] = None) -> None:
        self.client = client or RuneLiteBridgeClient()
        self._snapshot: Optional[BridgeSnapshot] = None

    def refresh(self) -> bool:
        self._snapshot = self.client.read_state()
        return self._snapshot is not None

    @property
    def snapshot(self) -> Optional[BridgeSnapshot]:
        return self._snapshot

    def physical_status(self, slot_index: int) -> str:
        return self.client.slot_visual(self._snapshot, slot_index)

    def exact_state(self, slot_index: int) -> str:
        return self.client.slot_state(self._snapshot, slot_index)

    def inventory_gp(self) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.inventory_gp

    def inventory_quantity(self, item_id: int) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.inventory.get(int(item_id), 0)

    def free_inventory_slots(self) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.inventory_state.free_slots

    def collect_ready(self, slot_index: int) -> Optional[bool]:
        if self._snapshot is None:
            return None
        for slot in self._snapshot.slots:
            if slot.slot == int(slot_index):
                return slot.collect_ready
        return None

    def safe_for_mouse_actions(self) -> bool:
        return self.client.safe_for_mouse_actions(self._snapshot)

    def safe_for_ge_mouse_actions(self) -> bool:
        return self.client.safe_for_ge_mouse_actions(self._snapshot)

    def modal_blocked(self) -> bool:
        return self.client.modal_blocked(self._snapshot)

    def canvas_size(self) -> Optional[Tuple[int, int]]:
        return self.client.canvas_size(self._snapshot)

    def ge_window_bounds(self) -> Optional[BridgeBounds]:
        return self.client.ge_window_bounds(self._snapshot)

    def ge_offer_setup_bounds(self) -> Optional[BridgeBounds]:
        if self._snapshot is None or not self._snapshot.ge.offer_setup_bounds.valid:
            return None
        return self._snapshot.ge.offer_setup_bounds

    def ge_inventory_bounds(self) -> Optional[BridgeBounds]:
        if self._snapshot is None or not self._snapshot.ge.inventory_bounds.valid:
            return None
        return self._snapshot.ge.inventory_bounds

    def ge_open(self) -> bool:
        return bool(self._snapshot is not None and self._snapshot.ge.open)

    def offer_setup_open(self) -> bool:
        return bool(self._snapshot is not None and self._snapshot.ge.offer_setup_open)

    def offer_setup_item_id(self) -> Optional[int]:
        if self._snapshot is None or not self._snapshot.ge.offer_setup_open:
            return None
        item_id = self._snapshot.ge.offer_setup_item_id
        return item_id if item_id >= 0 else None

    def world(self) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.client.world

    def player_location(self) -> Optional[Tuple[int, int, int]]:
        if self._snapshot is None or not self._snapshot.player.present:
            return None
        return (
            self._snapshot.player.world_x,
            self._snapshot.player.world_y,
            self._snapshot.player.plane,
        )

    def input_idle_ms(self) -> Optional[int]:
        return self.client.input_idle_ms(self._snapshot)

    def recent_input(self, window_ms: int) -> bool:
        return self.client.recent_input(self._snapshot, window_ms)

    def mouse_position(self) -> Optional[Tuple[int, int]]:
        return self.client.mouse_position(self._snapshot)

    def mouse_buttons_down_mask(self) -> Optional[int]:
        if self._snapshot is None:
            return None
        return self._snapshot.input.mouse_buttons_down_mask

    def last_control_key(self) -> Optional[str]:
        if self._snapshot is None:
            return None
        return self._snapshot.input.last_control_key or None
