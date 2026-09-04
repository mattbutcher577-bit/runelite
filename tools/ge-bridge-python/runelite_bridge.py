from __future__ import annotations

from dataclasses import dataclass
import time
from typing import Dict, Optional, Tuple

import requests


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
    game_state: str
    slots: Tuple[BridgeSlot, ...]
    inventory: Dict[int, int]
    inventory_gp: int


class RuneLiteBridgeClient:
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
        if payload.get("protocol") != 1:
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
        if not isinstance(raw_slots, list) or not isinstance(raw_inventory, list):
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

            inventory_gp = int(payload.get("inventoryGp", inventory.get(995, 0)))
        except (KeyError, TypeError, ValueError):
            return None

        return BridgeSnapshot(
            generated_at_epoch_ms=generated_at,
            game_state="LOGGED_IN",
            slots=slots,
            inventory=inventory,
            inventory_gp=inventory_gp,
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
